package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyz.adi.common.dto.CostDashboardResp;
import com.moyz.adi.common.entity.ModelPrice;
import com.moyz.adi.common.mapper.LLMCallRecordMapper;
import com.moyz.adi.common.mapper.ModelPriceMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T6-13 验收：成本看板聚合正确性与「无数据/不可用」语义。
 * <p>⚠️ 数据源故障必须返回 UNAVAILABLE（不能把异常吞成 0——0 会被误读为没花钱）。</p>
 * <p>OPT-15 批4：随被测类 LLMCallRecordService 迁至 adi-common；
 * ModelPriceService 注入已改为 ModelPriceMapper，测试相应 mock mapper 并内联 loadPriceMap 打桩。</p>
 */
class CostDashboardTest {

    private LLMCallRecordService service;
    private LLMCallRecordMapper mapper;
    private ModelPriceMapper priceMapper;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), com.moyz.adi.common.entity.LLMCallRecord.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ModelPrice.class);
        service = new LLMCallRecordService();
        mapper = mock(LLMCallRecordMapper.class);
        priceMapper = mock(ModelPriceMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "modelPriceMapper", priceMapper);
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(mapper, priceMapper);
    }

    private void stubPriceMap(Map<String, ModelPrice> priceMap) {
        when(priceMapper.selectList(any())).thenReturn(List.copyOf(priceMap.values()));
    }

    private static Map<String, ModelPrice> priceMapOf(Object... pairs) {
        Map<String, ModelPrice> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (ModelPrice) pairs[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> modelRow(String platform, String name, long calls, long inTok, long outTok) {
        Map<String, Object> row = new HashMap<>();
        row.put("modelPlatform", platform);
        row.put("modelName", name);
        row.put("callCount", calls);
        row.put("inputTokens", inTok);
        row.put("outputTokens", outTok);
        row.put("durationMs", 0L);
        return row;
    }

    private static Map<String, Object> channelRow(int sourceType, String agent, long calls, long inTok, long outTok) {
        Map<String, Object> row = new HashMap<>();
        row.put("sourceType", sourceType);
        row.put("sourceAgent", agent);
        row.put("callCount", calls);
        row.put("inputTokens", inTok);
        row.put("outputTokens", outTok);
        return row;
    }

    @Test
    void aggregatesByModelWithCost() {
        stubPriceMap(priceMapOf(
                "deepseek-chat", price("deepseek-chat", "1", "2"),
                "deepseek|qwen-max", price("qwen-max", "3", "4")));
        when(mapper.aggregateByModel(any(), any())).thenReturn(List.of(
                modelRow("", "deepseek-chat", 2, 1000, 500),
                modelRow("deepseek", "qwen-max", 1, 2000, 1000)));
        when(mapper.aggregateByChannel(any(), any())).thenReturn(List.of(
                channelRow(5, "翻译助手", 3, 3000, 1500)));

        CostDashboardResp resp = service.costDashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(CostDashboardResp.STATUS_OK, resp.getStatus());
        assertEquals(2, resp.getByModel().size());
        // deepseek-chat：1000/1000*1 + 500/1000*2 = 2.0
        CostDashboardResp.CostAggItem ds = resp.getByModel().stream()
                .filter(i -> "deepseek-chat".equals(i.getGroupKey())).findFirst().orElseThrow();
        assertTrue(ds.isPriced());
        assertEquals(0, ds.getInputCost().compareTo(new BigDecimal("1.000000")));
        assertEquals(0, ds.getOutputCost().compareTo(new BigDecimal("1.000000")));
        assertEquals(0, ds.getTotalCost().compareTo(new BigDecimal("2.000000")));
        // 平台精确单价优先：qwen-max 用 deepseek 平台价（3/4），不是通用价
        CostDashboardResp.CostAggItem qwen = resp.getByModel().stream()
                .filter(i -> "qwen-max".equals(i.getGroupKey())).findFirst().orElseThrow();
        assertEquals(0, qwen.getTotalCost().compareTo(new BigDecimal("10.000000")));
        // 渠道聚合：agent 记录归并为「翻译助手」单一渠道
        assertEquals(1, resp.getByChannel().size());
        assertEquals("翻译助手", resp.getByChannel().get(0).getGroupKey());
        // summary
        assertEquals(3L, resp.getSummary().getCallCount());
        assertEquals(3000L, resp.getSummary().getInputTokens());
        assertTrue(resp.getSummary().isPriced());
    }

    @Test
    void unpricedModelHasNullCostButTokensShown() {
        stubPriceMap(new HashMap<>());
        when(mapper.aggregateByModel(any(), any())).thenReturn(List.of(
                modelRow("", "some-model", 1, 100, 100)));
        when(mapper.aggregateByChannel(any(), any())).thenReturn(List.of(
                channelRow(1, "", 1, 100, 100)));

        CostDashboardResp resp = service.costDashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(CostDashboardResp.STATUS_OK, resp.getStatus());
        CostDashboardResp.CostAggItem item = resp.getByModel().get(0);
        assertFalse(item.isPriced());
        assertNull(item.getTotalCost());
        assertEquals(100L, item.getInputTokens());
        // 渠道名回落到 source_type 描述
        assertEquals("character_chat", resp.getByChannel().get(0).getGroupKey());
    }

    @Test
    void emptyRangeReturnsNoDataNotZero() {
        when(mapper.aggregateByModel(any(), any())).thenReturn(List.of());
        when(mapper.aggregateByChannel(any(), any())).thenReturn(List.of());
        stubPriceMap(new HashMap<>());

        CostDashboardResp resp = service.costDashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(CostDashboardResp.STATUS_NO_DATA, resp.getStatus());
        assertTrue(resp.getByModel().isEmpty());
        assertTrue(resp.getByChannel().isEmpty());
    }

    @Test
    void dataSourceFailureReturnsUnavailableNeverZero() {
        when(priceMapper.selectList(any())).thenThrow(new RuntimeException("table adi_model_price missing"));

        CostDashboardResp resp = service.costDashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(CostDashboardResp.STATUS_UNAVAILABLE, resp.getStatus());
        assertNotNull(resp.getReason());
        assertTrue(resp.getByModel().isEmpty());
        assertTrue(resp.getSummary().getInputTokens() == 0L);
        assertTrue(resp.getSummary().getTotalCost() == null);
    }

    @Test
    void mapperFailureReturnsUnavailable() {
        stubPriceMap(new HashMap<>());
        when(mapper.aggregateByModel(any(), any())).thenThrow(new RuntimeException("connection refused"));

        CostDashboardResp resp = service.costDashboard(LocalDateTime.now().minusDays(1), LocalDateTime.now());

        assertEquals(CostDashboardResp.STATUS_UNAVAILABLE, resp.getStatus());
        assertEquals("connection refused", resp.getReason());
    }

    private static ModelPrice price(String modelName, String input, String output) {
        ModelPrice p = new ModelPrice();
        p.setModelName(modelName);
        p.setInputPrice(new BigDecimal(input));
        p.setOutputPrice(new BigDecimal(output));
        p.setCurrency("CNY");
        p.setPriceUnit("1k_tokens");
        return p;
    }
}
