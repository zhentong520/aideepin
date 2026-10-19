package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.dto.ApiKeyResp;
import com.moyz.adi.common.dto.finetune.FinetuneJobCreateReq;
import com.moyz.adi.common.dto.finetune.FinetuneMetaResp;
import com.moyz.adi.common.dto.finetune.FinetunePreviewResp;
import com.moyz.adi.common.entity.FinetuneArtifact;
import com.moyz.adi.common.entity.FinetuneDataset;
import com.moyz.adi.common.entity.FinetuneJob;
import com.moyz.adi.common.entity.FinetuneSamplePool;
import com.moyz.adi.common.service.ExtApiService;
import com.moyz.adi.common.service.finetune.FinetuneArtifactService;
import com.moyz.adi.common.service.finetune.FinetuneDatasetService;
import com.moyz.adi.common.service.finetune.FinetuneJobService;
import com.moyz.adi.common.service.finetune.FinetuneSamplePoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 模型微调训练管理端：数据集 / 训练任务 / 模型产物 / Worker Key。
 * 遵循项目惯例：/admin/** 由 TokenFilter 校验 is_admin。
 */
@Tag(name = "Finetune Management | 模型微调管理", description = "Finetune dataset/job/artifact management | 模型微调数据集、训练任务与产物管理")
@RestController
@RequestMapping("/admin/finetune")
@Validated

@AdminOnly
public class AdminFinetuneController {

    public static final String WORKER_KEY_TYPE = "finetune";

    @Resource
    private FinetuneDatasetService finetuneDatasetService;

    @Resource
    private FinetuneJobService finetuneJobService;

    @Resource
    private FinetuneArtifactService finetuneArtifactService;

    @Resource
    private FinetuneSamplePoolService finetuneSamplePoolService;

    @Resource
    private ExtApiService extApiService;

    // ========== 数据集 ==========

    @Operation(summary = "上传数据集 | Upload dataset (jsonl)")
    @PostMapping("/dataset/upload")
    public FinetuneDataset upload(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "name", required = false) String name,
                                  @RequestParam(value = "description", required = false) String description,
                                  @RequestParam(value = "format", defaultValue = "alpaca-jsonl") String format) {
        return finetuneDatasetService.create(file, name, description, format);
    }

    @Operation(summary = "搜索数据集 | Search datasets")
    @PostMapping("/dataset/search")
    public Page<FinetuneDataset> searchDatasets(@RequestParam(value = "name", required = false) String name,
                                                @NotNull @Min(1) Integer currentPage,
                                                @NotNull @Min(10) Integer pageSize) {
        return finetuneDatasetService.search(name, currentPage, pageSize);
    }

    @Operation(summary = "数据集详情 | Dataset detail")
    @GetMapping("/dataset/{uuid}")
    public FinetuneDataset datasetDetail(@PathVariable String uuid) {
        return finetuneDatasetService.getByUuid(uuid);
    }

    @Operation(summary = "数据集样本预览 | Preview dataset samples")
    @GetMapping("/dataset/{uuid}/preview")
    public List<Object> preview(@PathVariable String uuid, @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return finetuneDatasetService.preview(uuid, Math.min(Math.max(limit, 1), 100))
                .stream().map(n -> (Object) n).toList();
    }

    @Operation(summary = "删除数据集 | Delete dataset")
    @PostMapping("/dataset/del/{id}")
    public void deleteDataset(@PathVariable Long id) {
        finetuneDatasetService.softDelete(id);
    }

    @Operation(summary = "从知识库导出QA为数据集 | Export KB QA pairs as dataset")
    @PostMapping("/dataset/exportFromKb")
    public FinetuneDataset exportFromKb(@RequestParam("kbUuid") String kbUuid,
                                        @RequestParam(value = "name", required = false) String name,
                                        @RequestParam(value = "description", required = false) String description) {
        return finetuneDatasetService.createFromKb(kbUuid, name, description);
    }

    // ========== 训练任务 ==========

    @Operation(summary = "训练元数据 | Finetune metadata (base models / presets / defaults / limits)")
    @GetMapping("/meta")
    public FinetuneMetaResp meta() {
        return finetuneJobService.meta();
    }

    @Operation(summary = "参数预演 | Preview steps/vram/warnings for the given params")
    @PostMapping("/job/preview")
    public FinetunePreviewResp preview(@RequestBody FinetuneJobCreateReq req) {
        return finetuneJobService.preview(req);
    }

    @Operation(summary = "创建训练任务 | Create training job")
    @PostMapping("/job/create")
    public FinetuneJob createJob(@RequestBody FinetuneJobCreateReq req) {
        return finetuneJobService.create(req);
    }

    @Operation(summary = "搜索训练任务 | Search training jobs")
    @PostMapping("/job/search")
    public Page<FinetuneJob> searchJobs(@RequestParam(value = "status", required = false) String status,
                                        @RequestParam(value = "name", required = false) String name,
                                        @NotNull @Min(1) Integer currentPage,
                                        @NotNull @Min(10) Integer pageSize) {
        return finetuneJobService.search(status, name, currentPage, pageSize);
    }

    @Operation(summary = "任务详情 | Job detail")
    @GetMapping("/job/{uuid}")
    public FinetuneJob jobDetail(@PathVariable String uuid) {
        return finetuneJobService.getByUuid(uuid);
    }

    @Operation(summary = "任务日志尾部 | Job log tail")
    @GetMapping("/job/{uuid}/log")
    public Map<String, Object> jobLog(@PathVariable String uuid, @RequestParam(value = "lines", defaultValue = "200") int lines) {
        List<String> logLines = finetuneJobService.readLogTail(uuid, Math.min(Math.max(lines, 1), 2000));
        return Map.of("lines", logLines);
    }

    @Operation(summary = "请求停止任务 | Request job stop")
    @PostMapping("/job/stop/{uuid}")
    public void stopJob(@PathVariable String uuid) {
        finetuneJobService.requestStop(uuid);
    }

    @Operation(summary = "删除任务 | Delete job")
    @PostMapping("/job/del/{id}")
    public void deleteJob(@PathVariable Long id) {
        finetuneJobService.softDelete(id);
    }

    // ========== 模型产物 ==========

    @Operation(summary = "搜索产物 | Search artifacts")
    @PostMapping("/artifact/search")
    public Page<FinetuneArtifact> searchArtifacts(@RequestParam(value = "jobUuid", required = false) String jobUuid,
                                                  @RequestParam(value = "type", required = false) String type,
                                                  @NotNull @Min(1) Integer currentPage,
                                                  @NotNull @Min(10) Integer pageSize) {
        return finetuneArtifactService.search(jobUuid, type, currentPage, pageSize);
    }

    @Operation(summary = "删除产物 | Delete artifact")
    @PostMapping("/artifact/del/{id}")
    public void deleteArtifact(@PathVariable Long id) {
        finetuneArtifactService.softDelete(id);
    }

    @Operation(summary = "一键注册为可用模型 | Register artifact as usable model")
    @PostMapping("/artifact/{uuid}/register")
    public Map<String, Object> register(@PathVariable String uuid,
                                        @RequestParam("ollamaBaseUrl") String ollamaBaseUrl,
                                        @RequestParam("ollamaTag") String ollamaTag) {
        FinetuneArtifact artifact = finetuneArtifactService.register(uuid, ollamaBaseUrl, ollamaTag);
        return Map.of("artifactUuid", artifact.getUuid());
    }

    // ========== Worker Key（训练节点接入凭证，复用用户级 ext key 体系） ==========

    @Operation(summary = "生成训练 Worker Key | Generate finetune worker API key")
    @PostMapping("/workerKey/generate")
    public ApiKeyResp generateWorkerKey() {
        return extApiService.generateUserApiKey(WORKER_KEY_TYPE);
    }

    @Operation(summary = "Worker Key 掩码信息 | Worker key info (masked)")
    @GetMapping("/workerKey")
    public ApiKeyResp workerKeyInfo() {
        return extApiService.getUserApiKeyInfo(WORKER_KEY_TYPE);
    }

    @Operation(summary = "查看 Worker Key 明文 | Reveal worker key")
    @PostMapping("/workerKey/reveal")
    public ApiKeyResp revealWorkerKey() {
        return extApiService.revealUserApiKey(WORKER_KEY_TYPE);
    }

    // ========== 样本候选池（会话收集 -> 改写 -> 审核 -> 导出） ==========

    @Operation(summary = "搜索候选样本 | Search sample pool")
    @PostMapping("/samplePool/search")
    public Page<FinetuneSamplePool> searchSamplePool(@RequestParam(value = "status", required = false) String status,
                                                     @RequestParam(value = "keyword", required = false) String keyword,
                                                     @NotNull @Min(1) Integer currentPage,
                                                     @NotNull @Min(10) Integer pageSize) {
        return finetuneSamplePoolService.search(status, keyword, currentPage, pageSize);
    }

    @Operation(summary = "候选样本详情 | Sample pool detail")
    @GetMapping("/samplePool/{uuid}")
    public FinetuneSamplePool samplePoolDetail(@PathVariable String uuid) {
        return finetuneSamplePoolService.getByUuid(uuid);
    }

    @Operation(summary = "LLM 改写单条样本 | Rewrite one sample via LLM")
    @PostMapping("/samplePool/rewrite/{id}")
    public FinetuneSamplePool rewriteSample(@PathVariable Long id) {
        return finetuneSamplePoolService.rewrite(id);
    }

    @Operation(summary = "批量改写 | Batch rewrite (pending_rewrite / discarded)")
    @PostMapping("/samplePool/rewriteBatch")
    public Map<String, Object> rewriteBatch(@RequestBody List<Long> ids) {
        int ok = 0;
        int failed = 0;
        for (Long id : ids) {
            FinetuneSamplePool row = finetuneSamplePoolService.rewrite(id);
            if (null != row && FinetuneSamplePoolService.STATUS_GENERATED.equals(row.getStatus())) {
                ok++;
            } else {
                failed++;
            }
        }
        return Map.of("success", ok, "failed", failed);
    }

    @Operation(summary = "批量确认样本 | Confirm samples (generated -> confirmed)")
    @PostMapping("/samplePool/confirm")
    public void confirmSamples(@RequestBody List<Long> ids) {
        finetuneSamplePoolService.confirm(ids);
    }

    @Operation(summary = "批量驳回样本 | Reject samples")
    @PostMapping("/samplePool/reject")
    public void rejectSamples(@RequestBody List<Long> ids,
                              @RequestParam(value = "reason", required = false) String reason) {
        finetuneSamplePoolService.reject(ids, reason);
    }

    @Operation(summary = "导出确认样本为数据集 | Export confirmed samples as finetune dataset")
    @PostMapping("/samplePool/export")
    public Map<String, Object> exportSamples(@RequestBody List<Long> ids,
                                             @RequestParam(value = "name", required = false) String name,
                                             @RequestParam(value = "description", required = false) String description) {
        int count = finetuneSamplePoolService.exportToDataset(ids, name, description);
        return Map.of("exported", count);
    }
}
