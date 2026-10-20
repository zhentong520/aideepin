package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.dto.RagQualityDashboardResp;
import com.moyz.adi.common.dto.RagQualityDetailItem;
import com.moyz.adi.common.dto.RagQualityDetailReq;
import com.moyz.adi.common.dto.RagToEvalReq;
import com.moyz.adi.common.dto.RagToEvalResp;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.service.RagEvalDatasetService;
import com.moyz.adi.common.service.RagQualityService;
import com.moyz.adi.common.service.RagQueryLogService;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 二开新增（T1-01，2026-10-16）：RAG 检索日志查询入口。
 * <p>
 * ACL（T1-05）上线后若过滤失效，靠这份日志按用户/库/时间定位。
 * <p>
 * 二开扩展（T7-01，2026-10-20）：RAG 质量看板 + 失败闭环——
 * 五指标聚合（quality-dashboard）、下钻明细（quality-detail）、
 * 失败/低分案例一键转评测题（to-eval）。
 */
@Tag(name = "RAG检索日志 | RAG Query Log", description = "RAG检索日志查询 | RAG Query Log Management")
@RestController
@RequestMapping("/admin/rag")
@Validated
@AdminOnly
public class AdminRagController {

    @Resource
    private RagQueryLogService ragQueryLogService;

    @Resource
    private RagQualityService ragQualityService;

    @Resource
    private RagEvalDatasetService ragEvalDatasetService;

    @Operation(summary = "检索日志分页 | Query log page",
            description = "按时间/用户/知识库筛选，时间格式 yyyy-MM-dd HH:mm:ss | Filter by time/user/kb")
    @GetMapping("/query-log")
    public Page<RagQueryLog> queryLog(@RequestParam(defaultValue = "") String userUuid,
                                      @RequestParam(defaultValue = "") String kbUuid,
                                      @RequestParam(defaultValue = "") String beginTime,
                                      @RequestParam(defaultValue = "") String endTime,
                                      @NotNull @Min(1) Integer currentPage,
                                      @NotNull @Min(1) Integer pageSize) {
        return ragQueryLogService.pageQuery(userUuid, kbUuid, beginTime, endTime, currentPage, pageSize);
    }

    /**
     * T7-01 质量看板：五指标 + 按知识库下钻 + 反馈统计。
     * <p>status 区分 OK / NO_DATA（范围内无检索）/ UNAVAILABLE（数据源故障，不展示任何 0）。</p>
     */
    @Operation(summary = "RAG 质量看板聚合 | RAG quality dashboard",
            description = "五指标：命中率/平均命中文档数/平均Top分数/低分占比/耗时P95；时间格式 yyyy-MM-dd HH:mm:ss")
    @PostMapping("/quality-dashboard")
    public RagQualityDashboardResp qualityDashboard(@RequestParam String beginTime,
                                                    @RequestParam String endTime) {
        return ragQualityService.dashboard(
                LocalDateTimeUtil.parse(beginTime), LocalDateTimeUtil.parse(endTime));
    }

    /**
     * T7-01 质量看板明细（下钻表）：按时间/知识库/用户/范围（all/missed/low_score/failed）分页。
     */
    @Operation(summary = "质量看板明细分页 | Quality detail page",
            description = "scope: all=全部 / missed=未命中 / low_score=低分（含未命中） / failed=差评")
    @PostMapping("/quality-detail")
    public Page<RagQualityDetailItem> qualityDetail(@RequestBody @Valid RagQualityDetailReq req,
                                                    @NotNull @Min(1) Integer currentPage,
                                                    @NotNull @Min(1) Integer pageSize) {
        return ragQualityService.detailPage(req, currentPage, pageSize);
    }

    /**
     * T7-01 明细 CSV 导出：与 quality-detail 同筛选口径，服务端全量导出（上限
     * {@link RagQualityService#CSV_EXPORT_MAX_ROWS} 行，超限截断并标注）。
     * <p>UTF-8 带 BOM（Excel 直开中文不乱码），文件名含导出时间。</p>
     */
    @Operation(summary = "质量明细 CSV 导出 | Export quality detail as CSV",
            description = "与 quality-detail 同筛选口径；上限 10000 行，超限截断并标注")
    @PostMapping("/quality-detail/export")
    public ResponseEntity<byte[]> qualityDetailExport(@RequestBody @Valid RagQualityDetailReq req)
            throws IOException {
        StringWriter sw = new StringWriter();
        ragQualityService.exportCsv(req, sw);
        // BOM：Excel 依据 BOM 识别 UTF-8，缺了中文在 Excel 里直接乱码
        byte[] body = ("\uFEFF" + sw).getBytes(StandardCharsets.UTF_8);

        String fileName = "rag-quality-detail-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    /**
     * T7-01 失败案例一键转评测题：按筛选范围生成 kb-eval 格式候选题，
     * append 到 &lt;dataset-dir&gt;/qa-dataset-derived.json（独立候选池，金标待人工补）。
     */
    @Operation(summary = "失败案例一键转评测题 | Convert failed cases to eval questions",
            description = "scope: low_score=低分（含未命中，默认） / missed=仅未命中 / failed=仅差评")
    @PostMapping("/to-eval")
    public RagToEvalResp toEval(@RequestBody @Valid RagToEvalReq req) throws IOException {
        return ragEvalDatasetService.convert(req);
    }
}
