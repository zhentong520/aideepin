package com.moyz.adi.chat.controller.externalapi.v1;

import com.moyz.adi.common.dto.finetune.ProgressReportReq;
import com.moyz.adi.common.dto.finetune.ProgressReportResp;
import com.moyz.adi.common.dto.finetune.WorkerClaimResp;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.FinetuneDataset;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.service.FileService;
import com.moyz.adi.common.service.finetune.FinetuneDatasetService;
import com.moyz.adi.common.service.finetune.FinetuneJobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 训练 Worker 开放协议（/ext/v1/finetune/*，ExtApiAuthFilter 以用户级 key 鉴权）：
 * 心跳 → 领取任务 → 下载集 → 进度/日志回写 → 终态 + 产物上报。
 * 所有写操作以 (jobUuid, workerEpoch) 守护，被回收重派后的迟到写入一律拒绝。
 */
@Slf4j
@Tag(name = "External API - Finetune Worker")
@RestController
@RequestMapping("/ext/v1/finetune")
public class ExtFinetuneController {

    @Resource
    private FinetuneJobService finetuneJobService;

    @Resource
    private FinetuneDatasetService finetuneDatasetService;

    @Resource
    private FileService fileService;

    @Operation(summary = "Worker heartbeat | Worker 心跳")
    @PostMapping("/worker/heartbeat")
    public Map<String, Object> workerHeartbeat(@RequestBody Map<String, String> body) {
        String workerId = body.getOrDefault("workerId", "");
        String gpuInfo = body.getOrDefault("gpuInfo", "");
        log.info("Finetune worker heartbeat, workerId:{}, gpu:{}", workerId, gpuInfo);
        return Map.of("ok", true, "serverTime", System.currentTimeMillis());
    }

    @Operation(summary = "Claim a pending job | 领取任务")
    @PostMapping("/job/claim")
    public WorkerClaimResp claim(@RequestBody Map<String, String> body) {
        String workerId = body.getOrDefault("workerId", "");
        return finetuneJobService.claim(workerId);
    }

    @Operation(summary = "Report progress (heartbeat folded) | 进度回写（含心跳）")
    @PostMapping("/job/{uuid}/progress")
    public ProgressReportResp progress(@PathVariable String uuid,
                                       @RequestParam("workerEpoch") String workerEpoch,
                                       @RequestBody ProgressReportReq req) {
        return finetuneJobService.reportProgress(uuid, workerEpoch, req);
    }

    @Operation(summary = "Append training log | 追加训练日志")
    @PostMapping("/job/{uuid}/log")
    public Map<String, Object> appendLog(@PathVariable String uuid,
                                         @RequestParam("workerEpoch") String workerEpoch,
                                         @RequestBody Map<String, String> body) {
        finetuneJobService.appendLog(uuid, workerEpoch, StringUtils.defaultString(body.get("content")));
        return Map.of("ok", true);
    }

    @Operation(summary = "Job complete + artifacts | 任务完成与产物上报")
    @PostMapping("/job/{uuid}/complete")
    public Map<String, Object> complete(@PathVariable String uuid,
                                        @RequestParam("workerEpoch") String workerEpoch,
                                        @RequestPart(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "artifactType", required = false) String artifactType,
                                        @RequestParam(value = "name", required = false) String name,
                                        @RequestParam(value = "externalPath", required = false) String externalPath,
                                        @RequestParam(value = "externalSizeBytes", required = false) Long externalSizeBytes,
                                        @RequestParam(value = "remark", required = false) String remark,
                                        @RequestParam(value = "metrics", required = false) String metrics) {
        boolean ok = finetuneJobService.complete(uuid, workerEpoch, file, artifactType, name,
                externalPath, externalSizeBytes, remark, metrics);
        return Map.of("accepted", ok);
    }

    @Operation(summary = "Job terminal state (failed/stopped) | 任务失败/中止上报")
    @PostMapping("/job/{uuid}/finish")
    public Map<String, Object> finish(@PathVariable String uuid,
                                      @RequestParam("workerEpoch") String workerEpoch,
                                      @RequestParam("status") String status,
                                      @RequestBody(required = false) Map<String, String> body) {
        if (!com.moyz.adi.common.entity.FinetuneJob.STATUS_FAILED.equals(status)
                && !com.moyz.adi.common.entity.FinetuneJob.STATUS_STOPPED.equals(status)) {
            return Map.of("accepted", false);
        }
        boolean ok = finetuneJobService.finish(uuid, workerEpoch, status,
                StringUtils.defaultString(body == null ? null : body.get("reason")));
        return Map.of("accepted", ok);
    }

    @Operation(summary = "Download dataset file | 下载数据集文件")
    @GetMapping("/dataset/{fileUuid}/download")
    public ResponseEntity<?> downloadDataset(@PathVariable String fileUuid) {
        AdiFile adiFile = fileService.getByUuid(fileUuid);
        // 定位所属数据集并校验归属（file 可能被多个数据集共享，按 sha256 去重）
        FinetuneDataset dataset = finetuneDatasetService.lambdaQuery()
                .eq(FinetuneDataset::getFileUuid, fileUuid)
                .eq(FinetuneDataset::getIsDeleted, false)
                .last("limit 1")
                .one();
        if (null == dataset) {
            return ResponseEntity.notFound().build();
        }
        // 存储位置 1=本地：直接流式回；2=OSS：302 跳转到对象 URL
        if (adiFile.getStorageLocation() != null && adiFile.getStorageLocation() == 2) {
            String url = FileOperatorContext.getFileUrl(adiFile);
            return ResponseEntity.status(302).location(URI.create(url)).build();
        }
        java.nio.file.Path path = Paths.get(adiFile.getPath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dataset.getUuid() + ".jsonl\"")
                .body(new FileSystemResource(path));
    }
}
