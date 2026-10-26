package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.RagQualityDashboardResp;
import com.moyz.adi.common.dto.RagQualityDetailItem;
import com.moyz.adi.common.dto.RagQualityDetailReq;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.RagFeedback;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import com.moyz.adi.common.mapper.RagFeedbackMapper;
import com.moyz.adi.common.mapper.RagQueryLogMapper;
import com.moyz.adi.common.util.JsonUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-01 验收：质量看板五指标聚合、「无数据/不可用」语义、下钻明细筛选口径。
 * <p>⚠️ 数据源故障必须 UNAVAILABLE（不把异常吞成 0——0 会被误读为检索质量完美）；
 * 「全部检索无命中」时 avgTopScore 必须保持 null 而不是 0。</p>
 */
class RagQualityDashboardTest {

    private RagQualityService service;
    private RagQueryLogMapper queryLogMapper;
    private RagFeedbackMapper feedbackMapper;
    private KnowledgeBaseMapper kbMapper;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RagQueryLog.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), RagFeedback.class);
        service = new RagQualityService();
        queryLogMapper = mock(RagQueryLogMapper.class);
        feedbackMapper = mock(RagFeedbackMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", queryLogMapper);
        ReflectionTestUtils.setField(service, "ragFeedbackMapper", feedbackMapper);
        ReflectionTestUtils.setField(service, "adiProperties", new AdiProperties());
        KnowledgeBaseService kbService = mock(KnowledgeBaseService.class);
        kbMapper = mock(KnowledgeBaseMapper.class);
        when(kbService.lambdaQuery()).thenReturn(new LambdaQueryChainWrapper<KnowledgeBase>(kbMapper, KnowledgeBase.class));
        ReflectionTestUtils.setField(service, "knowledgeBaseService", kbService);
    }

    @AfterEach
    void tearDown() {
        org.mockito.Mockito.reset(queryLogMapper, feedbackMapper, kbMapper);
    }

    private static Map<String, Object> summaryRow(long total, long hit, double avgHits, long lowScore,
                                                  Double avgTop, Double p50, double avgMs, Double p95) {
        Map<String, Object> row = new HashMap<>();
        row.put("totalQueries", total);
        row.put("hitQueries", hit);
        row.put("avgHitCount", avgHits);
        row.put("lowScoreQueries", lowScore);
        row.put("avgTopScore", avgTop);
        row.put("topScoreP50", p50);
        row.put("avgElapsedMs", avgMs);
        row.put("elapsedP95", p95);
        return row;
    }

    @Test
    void fiveMetricsComputedFromSummaryRow() {
        when(queryLogMapper.qualitySummary(any(), any(), anyDouble()))
                .thenReturn(summaryRow(100, 70, 3.2, 15, 0.62, 0.58, 210.5, 980.3));
        when(queryLogMapper.qualityByKb(any(), any(), anyDouble()))
                .thenReturn(List.of());
        when(feedbackMapper.feedbackSummary(any(), any())).thenReturn(Map.of("passCount", 5L, "failCount", 2L));

        RagQualityDashboardResp resp = service.dashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(RagQualityDashboardResp.STATUS_OK, resp.getStatus());
        RagQualityDashboardResp.QualitySummary s = resp.getSummary();
        // 命中率 = 70/100
        assertEquals(70.0, s.getHitRate(), 1e-9);
        // 平均命中文档数
        assertEquals(3.2, s.getAvgHitCount(), 1e-9);
        // 平均 Top 分数
        assertEquals(0.62, s.getAvgTopScore(), 1e-9);
        // 低分占比 = 15/100
        assertEquals(15.0, s.getLowScoreShare(), 1e-9);
        // 耗时 P95
        assertEquals(980.3, s.getElapsedP95(), 1e-9);
        // 支撑字段：P50 / 均值 / 阈值
        assertEquals(0.58, s.getTopScoreP50(), 1e-9);
        assertEquals(210.5, s.getAvgElapsedMs(), 1e-9);
        assertEquals(0.5, s.getLowScoreThreshold(), 1e-9);
        assertEquals(100L, s.getTotalQueries());
        assertEquals(70L, s.getHitQueries());
        assertEquals(15L, s.getLowScoreQueries());
        // 反馈统计并入同一响应
        assertEquals(5L, resp.getFeedback().getPassCount());
        assertEquals(2L, resp.getFeedback().getFailCount());
    }

    @Test
    void noDataWhenRangeEmpty() {
        when(queryLogMapper.qualitySummary(any(), any(), anyDouble())).thenReturn(summaryRow(0, 0, 0, 0, null, null, 0, null));
        when(queryLogMapper.qualityByKb(any(), any(), anyDouble())).thenReturn(List.of());
        when(feedbackMapper.feedbackSummary(any(), any())).thenReturn(Map.of("passCount", 0L, "failCount", 0L));

        RagQualityDashboardResp resp = service.dashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(RagQualityDashboardResp.STATUS_NO_DATA, resp.getStatus());
        assertTrue(resp.getByKb().isEmpty());
        // NO_DATA 不携带任何指标（前端走空态文案，不看数字）
        assertNull(resp.getSummary().getHitRate());
        assertNull(resp.getSummary().getAvgTopScore());
    }

    @Test
    void dataSourceFailureReturnsUnavailableNeverZero() {
        when(queryLogMapper.qualitySummary(any(), any(), anyDouble()))
                .thenThrow(new RuntimeException("relation \"adi_rag_query_log\" does not exist"));

        RagQualityDashboardResp resp = service.dashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(RagQualityDashboardResp.STATUS_UNAVAILABLE, resp.getStatus());
        assertNotNull(resp.getReason());
        // 不可用时不返回任何指标行/数字
        assertTrue(resp.getByKb().isEmpty());
        assertNull(resp.getSummary().getHitRate());
        assertNull(resp.getSummary().getAvgTopScore());
        assertNull(resp.getSummary().getElapsedP95());
        assertEquals(0L, resp.getSummary().getTotalQueries());
    }

    @Test
    void byKbAggregationResolvesTitlesAndFallsBackToUuid() {
        when(queryLogMapper.qualitySummary(any(), any(), anyDouble())).thenReturn(summaryRow(10, 8, 2.5, 2, 0.7, 0.7, 100.0, 200.0));
        when(queryLogMapper.qualityByKb(any(), any(), anyDouble())).thenReturn(List.of(
                kbRow("kb-title-known", 6, 5, 2.5, 0.7, 100.0),
                kbRow("kb-title-missing", 4, 3, 2.0, 0.6, 90.0)));
        when(feedbackMapper.feedbackSummary(any(), any())).thenReturn(Map.of("passCount", 0L, "failCount", 0L));
        KnowledgeBase known = new KnowledgeBase();
        known.setUuid("kb-title-known");
        known.setTitle("产品文档库");
        when(kbMapper.selectList(any())).thenReturn(List.of(known));

        RagQualityDashboardResp resp = service.dashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(RagQualityDashboardResp.STATUS_OK, resp.getStatus());
        assertEquals(2, resp.getByKb().size());
        RagQualityDashboardResp.KbQualityItem first = resp.getByKb().get(0);
        assertEquals("kb-title-known", first.getKbUuid());
        assertEquals("产品文档库", first.getKbTitle());
        // 每库命中率 = 5/6
        assertEquals(5 * 100.0 / 6, first.getHitRate(), 1e-9);
        assertEquals(6L, first.getQueryCount());
        // 标题查不到的知识库回落展示 uuid（看板不因此失败）
        RagQualityDashboardResp.KbQualityItem second = resp.getByKb().get(1);
        assertEquals("kb-title-missing", second.getKbTitle());
    }

    @Test
    void allMissedKeepsAvgTopScoreNullNotZero() {
        when(queryLogMapper.qualitySummary(any(), any(), anyDouble()))
                .thenReturn(summaryRow(5, 0, 0.0, 5, null, null, 30.0, 40.0));
        when(queryLogMapper.qualityByKb(any(), any(), anyDouble())).thenReturn(List.of());
        when(feedbackMapper.feedbackSummary(any(), any())).thenReturn(Map.of("passCount", 0L, "failCount", 0L));

        RagQualityDashboardResp resp = service.dashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(RagQualityDashboardResp.STATUS_OK, resp.getStatus());
        RagQualityDashboardResp.QualitySummary s = resp.getSummary();
        // 命中率 0 是真实测量值，可以展示
        assertEquals(0.0, s.getHitRate(), 1e-9);
        // 无命中时平均分不是 0（0 分和没分数是两件事）——保持 null
        assertNull(s.getAvgTopScore());
        assertNull(s.getTopScoreP50());
        // 低分占比 = 5/5 = 100%（无命中计入低分，与明细 low_score 口径一致）
        assertEquals(100.0, s.getLowScoreShare(), 1e-9);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPageLowScoreScopeFiltersByTopScore() {
        when(queryLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<RagQueryLog>(1, 20));
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        service.detailPage(lowScoreReq(), 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<RagQueryLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(queryLogMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getTargetSql();
        // 低分口径：hit_segments 内最高分 < 阈值（空数组 COALESCE 成 -1，未命中恒命中该筛选）
        assertTrue(sql.contains("jsonb_array_elements"), "应按 top score 筛选: " + sql);
        assertTrue(sql.contains("<"), "应含阈值比较: " + sql);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPageMissedScopeFiltersByHitCount() {
        when(queryLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<RagQueryLog>(1, 20));
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        RagQualityDetailReq req = new RagQualityDetailReq();
        req.setScope(RagQualityDetailReq.SCOPE_MISSED);
        service.detailPage(req, 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<RagQueryLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(queryLogMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertTrue(sql.contains("hit_count"), "应按命中数筛选: " + sql);
        assertFalse(sql.contains("adi_rag_feedback"), "missed 不应 join 反馈表: " + sql);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPageFailedScopeFiltersByFeedback() {
        when(queryLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<RagQueryLog>(1, 20));
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        RagQualityDetailReq req = new RagQualityDetailReq();
        req.setScope(RagQualityDetailReq.SCOPE_FAILED);
        service.detailPage(req, 1, 20);

        ArgumentCaptor<LambdaQueryWrapper<RagQueryLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(queryLogMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertTrue(sql.contains("adi_rag_feedback"), "应按差评 EXISTS 筛选: " + sql);
        assertTrue(sql.contains("FAIL"), "应只筛选 FAIL: " + sql);
    }

    @Test
    void detailPageMapsRowsWithTopScoreAndFeedback() {
        RagQueryLog row = RagQueryLogService.build("q-map-1", "u-1", List.of("kb-a"),
                "f", "[{\"kb_uuid\":\"kb-a\",\"score\":0.31},{\"kb_uuid\":\"kb-a\",\"score\":0.72}]",
                2, 0, 812, "local:bge", "如何配置知识库？");
        Page<RagQueryLog> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(queryLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        RagFeedback feedback = new RagFeedback();
        feedback.setQueryId("q-map-1");
        feedback.setRating(RagFeedback.RATING_FAIL);
        feedback.setComment("答案不对");
        when(feedbackMapper.selectList(any())).thenReturn(List.of(feedback));

        Page<RagQualityDetailItem> result = service.detailPage(lowScoreReq(), 1, 20);

        assertEquals(1, result.getTotal());
        RagQualityDetailItem item = result.getRecords().get(0);
        assertEquals("q-map-1", item.getQueryId());
        assertEquals("如何配置知识库？", item.getQueryText());
        assertEquals(List.of("kb-a"), item.getKbUuids());
        // topScore = 命中里的最大分（与聚合 SQL max(score) 同口径）
        assertEquals(0.72, item.getTopScore(), 1e-9);
        assertEquals(812, item.getElapsedMs());
        // 反馈按页批量带上（无 N+1）
        assertEquals("FAIL", item.getFeedbackRating());
        assertEquals("答案不对", item.getFeedbackComment());
    }

    @Test
    void detailPageWithoutFeedbackLeavesRatingBlank() {
        RagQueryLog row = RagQueryLogService.build("q-map-2", "u-2", List.of(), "f", null, 0, 0, 5, "m", "问句");
        Page<RagQueryLog> page = new Page<>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(queryLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(feedbackMapper.selectList(any())).thenReturn(List.of());

        Page<RagQualityDetailItem> result = service.detailPage(new RagQualityDetailReq(), 1, 20);

        RagQualityDetailItem item = result.getRecords().get(0);
        assertEquals("", item.getFeedbackRating());
        // 无命中 → topScore 为 null 而不是 0
        assertNull(item.getTopScore());
    }

    @Test
    void topScoreStaticTakesMaxAndIgnoresBadValues() {
        JsonNode hits = JsonUtil.toJsonNode("[{\"score\":0.31},{\"score\":0.72},{\"score\":0.5}]");
        assertEquals(0.72, RagQualityService.topScore(hits), 1e-9);
        // 空数组 / 非数字 score → null（不返回 0）
        assertNull(RagQualityService.topScore(JsonUtil.toJsonNode("[]")));
        assertNull(RagQualityService.topScore(JsonUtil.toJsonNode("[{\"score\":null}]")));
        assertNull(RagQualityService.topScore(JsonUtil.toJsonNode("[{\"kb_uuid\":\"kb-a\"}]")));
        assertNull(RagQualityService.topScore(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailPageRejectsIllegalKbUuid() {
        when(queryLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<RagQueryLog>(1, 20));

        RagQualityDetailReq req = lowScoreReq();
        req.setKbUuid("kb'; drop table x");
        Page<RagQualityDetailItem> result = service.detailPage(req, 1, 20);

        // 非法 kbUuid 直接返回空页，不触库（与 RagQueryLogService.pageQuery 同口径）
        assertEquals(0, result.getTotal());
        verify(queryLogMapper, org.mockito.Mockito.never()).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    private static RagQualityDetailReq lowScoreReq() {
        RagQualityDetailReq req = new RagQualityDetailReq();
        req.setBeginTime("2026-10-01 00:00:00");
        req.setEndTime("2026-10-20 23:59:59");
        req.setScope(RagQualityDetailReq.SCOPE_LOW_SCORE);
        return req;
    }

    private static Map<String, Object> kbRow(String kbUuid, long queryCount, long hitQueries,
                                             double avgHits, Double avgTop, double avgMs) {
        Map<String, Object> row = new HashMap<>();
        row.put("kbUuid", kbUuid);
        row.put("queryCount", queryCount);
        row.put("hitQueries", hitQueries);
        row.put("avgHitCount", avgHits);
        row.put("avgTopScore", avgTop);
        row.put("avgElapsedMs", avgMs);
        return row;
    }
}
