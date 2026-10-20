package com.moyz.adi.common.workflow.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T6-14 验收：AgentMetrics 指标补全——新字段随多态 Jackson 序列化/反序列化完整往返。
 */
class AgentMetricsTest {

    @Test
    void serializesWithTypeAndAllNewFields() throws Exception {
        AgentMetrics metrics = new AgentMetrics();
        metrics.setDurationMs(1234L);
        metrics.setInputTokens(100);
        metrics.setOutputTokens(50);
        metrics.setModelName("deepseek-chat");
        metrics.setRetrievalCount(5);
        metrics.setCharacterUuid("char-1");
        // T6-14 新增指标
        metrics.setLlmCallCount(1);
        metrics.setTotalTokens(150);
        metrics.setToolCallCount(2);
        metrics.setLlmDurationMs(900L);
        metrics.setDegradedCount(0);
        metrics.setBudgetAbortCount(0);

        String json = JsonUtil.toJson(metrics);
        JsonNode node = JsonUtil.toJsonNode(json);

        assertEquals("agent", node.get("type").asText(), "多态 type 必须是 agent，漏了指标入库不显示");
        assertNotNull(node.get("llmCallCount"));
        assertNotNull(node.get("totalTokens"));
        assertNotNull(node.get("toolCallCount"));
        assertNotNull(node.get("llmDurationMs"));
        assertNotNull(node.get("degradedCount"));
        assertNotNull(node.get("budgetAbortCount"));

        NodeExecutionMetrics roundTrip = JsonUtil.fromJson(json, NodeExecutionMetrics.class);
        AgentMetrics back = (AgentMetrics) roundTrip;
        assertEquals(1, back.getLlmCallCount());
        assertEquals(150, back.getTotalTokens());
        assertEquals(2, back.getToolCallCount());
        assertEquals(900L, back.getLlmDurationMs());
        assertEquals(0, back.getDegradedCount());
        assertEquals(0, back.getBudgetAbortCount());
        assertEquals(5, back.getRetrievalCount());
        assertEquals("char-1", back.getCharacterUuid());
    }
}
