package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.RagQualityDashboardResp;
import com.moyz.adi.common.dto.RagQualityDetailItem;
import com.moyz.adi.common.dto.RagQualityDetailReq;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.RagFeedback;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.mapper.RagFeedbackMapper;
import com.moyz.adi.common.mapper.RagQueryLogMapper;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 二开新增（T7-01，2026-10-20）：RAG 质量看板服务。
 * <p>
 * 数据源 adi_rag_query_log（T1-01 检索日志）+ adi_rag_feedback（T7-01 反馈表）：
 * <ul>
 *   <li>{@link #dashboard}：五指标聚合（命中率/平均命中文档数/平均 Top 分数/低分占比/耗时 P95）
 *       + 按知识库下钻 + 反馈统计。⚠️ 数据源故障返回 UNAVAILABLE 且不带任何数字——
 *       0 会被误读为「检索质量完美」</li>
 *   <li>{@link #detailPage}：明细表分页（时间/知识库/用户/范围筛选），topScore 在 Java 侧
 *       按聚合 SQL 同口径计算，反馈按页批量查（无 N+1）</li>
 * </ul>
 */
@Slf4j
@Service
public class RagQualityService extends ServiceImpl<RagQueryLogMapper, RagQueryLog> {

    /** kb uuid 只允许短 uuid 字符集（与 RagQueryLogService.pageQuery 同口径） */
    private static final Pattern KB_UUID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Resource
    private RagFeedbackMapper ragFeedbackMapper;

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 质量看板聚合。
     * <p>⚠️ 三态语义：OK（含指标）/ NO_DATA（范围内无检索）/ UNAVAILABLE（数据源故障，无任何数字）。</p>
     */
    public RagQualityDashboardResp dashboard(LocalDateTime begin, LocalDateTime end) {
        RagQualityDashboardResp resp = new RagQualityDashboardResp();
        Map<String, Object> summaryRow;
        List<Map<String, Object>> kbRows;
        Map<String, Object> feedbackRow;
        double threshold = adiProperties.getRag().getLowScoreThreshold();
        try {
            summaryRow = baseMapper.qualitySummary(begin, end, threshold);
            kbRows = baseMapper.qualityByKb(begin, end, threshold);
            feedbackRow = ragFeedbackMapper.feedbackSummary(begin, end);
        } catch (Exception e) {
            log.error("rag quality dashboard data source failed", e);
            resp.setStatus(RagQualityDashboardResp.STATUS_UNAVAILABLE);
            resp.setReason(StringUtils.defaultIfBlank(e.getMessage(), "data source error"));
            return resp;
        }

        long totalQueries = longVal(summaryRow.get("totalQueries"));
        if (totalQueries <= 0 && CollectionUtils.isEmpty(kbRows)) {
            resp.setStatus(RagQualityDashboardResp.STATUS_NO_DATA);
            return resp;
        }
        resp.setStatus(RagQualityDashboardResp.STATUS_OK);

        RagQualityDashboardResp.QualitySummary summary = resp.getSummary();
        summary.setTotalQueries(totalQueries);
        summary.setHitQueries(longVal(summaryRow.get("hitQueries")));
        summary.setLowScoreQueries(longVal(summaryRow.get("lowScoreQueries")));
        summary.setLowScoreThreshold(threshold);
        if (totalQueries > 0) {
            summary.setHitRate(ratioPercent(summary.getHitQueries(), totalQueries));
            summary.setLowScoreShare(ratioPercent(summary.getLowScoreQueries(), totalQueries));
        }
        summary.setAvgHitCount(dbl(summaryRow.get("avgHitCount")));
        // AVG/percentile 在「全部检索都无命中」时为 NULL —— 保持 null，前端展示「-」而不是 0
        summary.setAvgTopScore(dbl(summaryRow.get("avgTopScore")));
        summary.setTopScoreP50(dbl(summaryRow.get("topScoreP50")));
        summary.setElapsedP95(dbl(summaryRow.get("elapsedP95")));
        summary.setAvgElapsedMs(dbl(summaryRow.get("avgElapsedMs")));

        // 按知识库下钻（标题 best-effort：查不到就展示 uuid，不让看板整体失败）
        Map<String, String> kbTitles = resolveKbTitles(kbRows);
        for (Map<String, Object> row : kbRows) {
            RagQualityDashboardResp.KbQualityItem item = new RagQualityDashboardResp.KbQualityItem();
            String kbUuid = str(row.get("kbUuid"));
            item.setKbUuid(kbUuid);
            item.setKbTitle(kbTitles.getOrDefault(kbUuid, kbUuid));
            item.setQueryCount(longVal(row.get("queryCount")));
            item.setHitQueries(longVal(row.get("hitQueries")));
            if (item.getQueryCount() > 0) {
                item.setHitRate(ratioPercent(item.getHitQueries(), item.getQueryCount()));
            }
            item.setAvgHitCount(dbl(row.get("avgHitCount")));
            item.setAvgTopScore(dbl(row.get("avgTopScore")));
            item.setAvgElapsedMs(dbl(row.get("avgElapsedMs")));
            resp.getByKb().add(item);
        }

        RagQualityDashboardResp.FeedbackSummary feedback = resp.getFeedback();
        feedback.setPassCount(longVal(feedbackRow.get("passCount")));
        feedback.setFailCount(longVal(feedbackRow.get("failCount")));
        return resp;
    }

    /**
     * 明细表分页（下钻）。scope 口径与看板低分占比一致：low_score = 无命中或最高分低于阈值。
     */
    public Page<RagQualityDetailItem> detailPage(RagQualityDetailReq req, int currentPage, int pageSize) {
        double threshold = adiProperties.getRag().getLowScoreThreshold();
        LambdaQueryWrapper<RagQueryLog> wrapper = new LambdaQueryWrapper<>();
        // begin/end 可选：留空时不加时间范围条件（parse 不做 null 拆箱）
        LocalDateTime begin = StringUtils.isNotBlank(req.getBeginTime()) ? LocalDateTimeUtil.parse(req.getBeginTime()) : null;
        LocalDateTime end = StringUtils.isNotBlank(req.getEndTime()) ? LocalDateTimeUtil.parse(req.getEndTime()) : null;
        if (null != begin && null != end) {
            wrapper.between(RagQueryLog::getCreateTime, begin, end);
        }
        if (StringUtils.isNotBlank(req.getUserUuid())) {
            wrapper.eq(RagQueryLog::getUserUuid, req.getUserUuid());
        }
        if (StringUtils.isNotBlank(req.getKbUuid())) {
            if (!KB_UUID_PATTERN.matcher(req.getKbUuid()).matches()) {
                return emptyPage(currentPage, pageSize);
            }
            wrapper.apply("kb_uuids @> {0}", "[\"" + req.getKbUuid() + "\"]");
        }
        String scope = StringUtils.defaultIfBlank(req.getScope(), RagQualityDetailReq.SCOPE_ALL);
        switch (scope) {
            case RagQualityDetailReq.SCOPE_MISSED -> wrapper.eq(RagQueryLog::getHitCount, 0);
            // 低分 = 无命中（子查询对空数组返回 NULL，COALESCE 成 -1 恒低于阈值）或最高分低于阈值
            case RagQualityDetailReq.SCOPE_LOW_SCORE -> wrapper.apply(
                    "(SELECT COALESCE(max((e->>'score')::float8), -1) FROM jsonb_array_elements(hit_segments) e) < {0}",
                    threshold);
            case RagQualityDetailReq.SCOPE_FAILED -> wrapper.apply(
                    "EXISTS (SELECT 1 FROM adi_rag_feedback f WHERE f.query_id = adi_rag_query_log.query_id "
                            + "AND f.rating = 'FAIL' AND f.is_deleted = false)");
            default -> {
            }
        }
        wrapper.eq(RagQueryLog::getIsDeleted, false);
        wrapper.orderByDesc(RagQueryLog::getCreateTime);

        Page<RagQueryLog> page = baseMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        List<RagQueryLog> rows = page.getRecords();
        if (CollectionUtils.isEmpty(rows)) {
            Page<RagQualityDetailItem> result = new Page<>(currentPage, pageSize);
            result.setTotal(page.getTotal());
            return result;
        }
        Map<String, RagFeedback> feedbackMap = baseMapperFeedbackMap(rows);

        Page<RagQualityDetailItem> result = new Page<>(currentPage, pageSize);
        result.setTotal(page.getTotal());
        result.setRecords(rows.stream().map(row -> toDetailItem(row, feedbackMap.get(row.getQueryId()))).toList());
        return result;
    }

    /**
     * CSV 导出上限（防止误点导出全表把内存打爆；超限时截断并在末行标注）。
     */
    public static final int CSV_EXPORT_MAX_ROWS = 10000;

    /** CSV 导出翻页大小（与 detailPage 同筛选口径循环取数，避免一次性 load 全表） */
    private static final int CSV_EXPORT_PAGE_SIZE = 500;

    /** CSV 表头（与 {@link #writeCsvRow} 列序一致） */
    private static final String[] CSV_HEADERS = {
            "create_time", "query_id", "kb_uuids", "query_text", "hit_count",
            "top_score", "elapsed_ms", "embedding_model", "feedback_rating", "feedback_comment", "user_uuid"};

    /**
     * 按明细筛选口径导出 CSV（T7-01）。
     * <p>⚠️ 安全口径：
     * <ul>
     *   <li>UTF-8 带 BOM——Excel 直接双击打开中文不乱码；</li>
     *   <li>CSV 注入防护：以 = + - @ 或制表符开头的单元格前缀单引号（OWASP CSV Injection）；</li>
     *   <li>超 {@link #CSV_EXPORT_MAX_ROWS} 截断，末尾写一行标注，不静默截断。</li>
     * </ul>
     */
    public void exportCsv(RagQualityDetailReq req, Writer out) throws IOException {
        for (String h : CSV_HEADERS) {
            out.write(h);
            out.write(',');
        }
        out.write('\n');

        int current = 1;
        int written = 0;
        boolean truncated = false;
        while (written < CSV_EXPORT_MAX_ROWS) {
            Page<RagQualityDetailItem> page = detailPage(req, current, CSV_EXPORT_PAGE_SIZE);
            List<RagQualityDetailItem> records = page.getRecords();
            if (CollectionUtils.isEmpty(records)) {
                break;
            }
            for (RagQualityDetailItem item : records) {
                if (written >= CSV_EXPORT_MAX_ROWS) {
                    truncated = true;
                    break;
                }
                writeCsvRow(out, item);
                written++;
            }
            if (written >= CSV_EXPORT_MAX_ROWS || page.getRecords().size() < CSV_EXPORT_PAGE_SIZE) {
                break;
            }
            current++;
        }
        if (truncated) {
            out.write(csvCell("...(exceeded max rows " + CSV_EXPORT_MAX_ROWS + ", export truncated)..."));
            out.write("\n");
        }
    }

    private static void writeCsvRow(Writer out, RagQualityDetailItem item) throws IOException {
        StringBuilder sb = new StringBuilder(256);
        sb.append(csvCell(LocalDateTimeUtil.format(item.getCreateTime()))).append(',')
                .append(csvCell(item.getQueryId())).append(',')
                .append(csvCell(String.join("|", item.getKbUuids()))).append(',')
                .append(csvCell(item.getQueryText())).append(',')
                .append(csvCell(null == item.getHitCount() ? "" : String.valueOf(item.getHitCount()))).append(',')
                .append(csvCell(null == item.getTopScore() ? "" : String.valueOf(item.getTopScore()))).append(',')
                .append(csvCell(null == item.getElapsedMs() ? "" : String.valueOf(item.getElapsedMs()))).append(',')
                .append(csvCell(item.getEmbeddingModel())).append(',')
                .append(csvCell(item.getFeedbackRating())).append(',')
                .append(csvCell(item.getFeedbackComment())).append(',')
                .append(csvCell(item.getUserUuid()));
        out.write(sb.toString());
        out.write('\n');
    }

    /**
     * 单元格转义：恒加双引号包裹；内部双引号翻倍；
     * 公式注入防护——= + - @ 或 \t 开头的前缀单引号（会被 Excel 识别为文本）。
     */
    private static String csvCell(String value) {
        String v = null == value ? "" : value.replace("\r", "").replace("\n", " ");
        if (!v.isEmpty() && ("=+-@\t".indexOf(v.charAt(0)) >= 0)) {
            v = "'" + v;
        }
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    /**
     * 转评测题候选查询：与明细表同筛选口径，按时间倒序取前 limit 条（只取必要列）。
     */
    public List<RagQueryLog> toEvalCandidates(LocalDateTime begin, LocalDateTime end, String kbUuid,
                                              String scope, int limit) {
        LambdaQueryWrapper<RagQueryLog> wrapper = new LambdaQueryWrapper<>();
        if (null != begin && null != end) {
            wrapper.between(RagQueryLog::getCreateTime, begin, end);
        }
        if (StringUtils.isNotBlank(kbUuid)) {
            if (!KB_UUID_PATTERN.matcher(kbUuid).matches()) {
                return Collections.emptyList();
            }
            wrapper.apply("kb_uuids @> {0}", "[\"" + kbUuid + "\"]");
        }
        String effectiveScope = StringUtils.defaultIfBlank(scope, RagQualityDetailReq.SCOPE_LOW_SCORE);
        switch (effectiveScope) {
            case RagQualityDetailReq.SCOPE_MISSED -> wrapper.eq(RagQueryLog::getHitCount, 0);
            case RagQualityDetailReq.SCOPE_FAILED -> wrapper.apply(
                    "EXISTS (SELECT 1 FROM adi_rag_feedback f WHERE f.query_id = adi_rag_query_log.query_id "
                            + "AND f.rating = 'FAIL' AND f.is_deleted = false)");
            default -> wrapper.apply(
                    "(SELECT COALESCE(max((e->>'score')::float8), -1) FROM jsonb_array_elements(hit_segments) e) < {0}",
                    adiProperties.getRag().getLowScoreThreshold());
        }
        wrapper.eq(RagQueryLog::getIsDeleted, false);
        wrapper.orderByDesc(RagQueryLog::getCreateTime);
        wrapper.last("limit " + Math.max(1, limit));
        return baseMapper.selectList(wrapper);
    }

    /**
     * 单次检索最高分：hit_segments 内最大 score；无命中返回 null（不返回 0——0 分和没分数是两件事）。
     */
    public static Double topScore(JsonNode hitSegments) {
        if (null == hitSegments || !hitSegments.isArray() || hitSegments.isEmpty()) {
            return null;
        }
        Double max = null;
        for (JsonNode hit : hitSegments) {
            JsonNode score = hit.get("score");
            if (null == score || !score.isNumber()) {
                continue;
            }
            double v = score.asDouble();
            if (null == max || v > max) {
                max = v;
            }
        }
        return max;
    }

    private Map<String, RagFeedback> baseMapperFeedbackMap(List<RagQueryLog> rows) {
        List<String> queryIds = rows.stream().map(RagQueryLog::getQueryId).toList();
        if (queryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RagFeedback> feedbackRows = ragFeedbackMapper.selectList(new LambdaQueryWrapper<RagFeedback>()
                .in(RagFeedback::getQueryId, queryIds)
                .eq(RagFeedback::getIsDeleted, false));
        Map<String, RagFeedback> map = new LinkedHashMap<>();
        for (RagFeedback f : feedbackRows) {
            map.putIfAbsent(f.getQueryId(), f);
        }
        return map;
    }

    private static RagQualityDetailItem toDetailItem(RagQueryLog row, RagFeedback feedback) {
        RagQualityDetailItem item = new RagQualityDetailItem();
        item.setQueryId(row.getQueryId());
        item.setCreateTime(row.getCreateTime());
        item.setUserUuid(row.getUserUuid());
        item.setQueryText(row.getQueryText());
        item.setKbUuids(jsonArrayToList(row.getKbUuids()));
        item.setHitCount(row.getHitCount());
        item.setTopScore(topScore(row.getHitSegments()));
        item.setElapsedMs(row.getElapsedMs());
        item.setEmbeddingModel(row.getEmbeddingModel());
        if (null != feedback) {
            item.setFeedbackRating(StringUtils.defaultString(feedback.getRating()));
            item.setFeedbackComment(StringUtils.defaultString(feedback.getComment()));
        }
        return item;
    }

    private static List<String> jsonArrayToList(JsonNode node) {
        if (null == node || !node.isArray()) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>(node.size());
        node.forEach(e -> list.add(e.asText()));
        return list;
    }

    private Map<String, String> resolveKbTitles(List<Map<String, Object>> kbRows) {
        List<String> kbUuids = kbRows.stream()
                .map(r -> str(r.get("kbUuid")))
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        if (kbUuids.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            List<KnowledgeBase> kbs = knowledgeBaseService.lambdaQuery()
                    .in(KnowledgeBase::getUuid, kbUuids)
                    .list();
            Map<String, String> titles = new LinkedHashMap<>();
            for (KnowledgeBase kb : kbs) {
                titles.put(kb.getUuid(), StringUtils.defaultIfBlank(kb.getTitle(), kb.getUuid()));
            }
            return titles;
        } catch (Exception e) {
            // 标题解析失败不影响看板：回落展示 uuid
            log.warn("resolve kb titles failed,fallback to uuid,error:{}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Page<RagQualityDetailItem> emptyPage(int currentPage, int pageSize) {
        return new Page<>(currentPage, pageSize);
    }

    private static double ratioPercent(long part, long total) {
        return part * 100.0 / total;
    }

    private static long longVal(Object v) {
        return null == v ? 0L : ((Number) v).longValue();
    }

    private static Double dbl(Object v) {
        if (null == v) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String str(Object v) {
        return null == v ? "" : v.toString();
    }
}
