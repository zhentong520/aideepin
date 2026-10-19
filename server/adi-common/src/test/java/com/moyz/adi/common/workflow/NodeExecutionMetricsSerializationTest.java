package com.moyz.adi.common.workflow;

import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.metrics.McpMetrics;
import com.moyz.adi.common.workflow.metrics.SkillMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NodeExecutionMetrics} 多态序列化测试（纯逻辑，不启 Spring）。
 * <p>
 * 背景：节点指标以基类声明、按子类序列化后写进 {@code adi_workflow_runtime_node.metadata}(jsonb)。
 * 新增节点如果漏在 {@code @JsonSubTypes} 登记，编译期毫无提示——失败发生在「运行期写指标」时，
 * 而且不同调用路径的表现不一样（写库报错 vs 读出来变成基类、子类字段全部丢失）。
 * 因此这里把「新类型可往返」与「旧数据（无 type）仍可读」两条都锁死。
 * </p>
 * <p>
 * 断言约定：每条断言都要求「功能坏掉就会红」——因此断言具体字段值，不用「非空」这类
 * 在失败路径上也成立的表述；并把 JSON 里的 type 字段一并断言。
 * </p>
 */
class NodeExecutionMetricsSerializationTest {

    @Test
    @DisplayName("McpMetrics 往返：type=mcp 落在 JSON 里，字段值与耗时都不丢")
    void mcpMetricsRoundTrips() {
        McpMetrics metrics = new McpMetrics();
        metrics.setDurationMs(1234L);
        metrics.setMcpTitle("高德地图");
        metrics.setToolName("maps_weather");
        metrics.setResultLength(88);

        String json = JsonUtil.toJson(metrics);
        assertTrue(json.contains("\"type\":\"mcp\""), "JSON 必须带 type=mcp，实际：" + json);

        NodeExecutionMetrics restored = JsonUtil.fromJson(json, NodeExecutionMetrics.class);
        McpMetrics typed = assertInstanceOf(McpMetrics.class, restored, "必须还原成 McpMetrics 而不是基类");
        assertEquals("高德地图", typed.getMcpTitle());
        assertEquals("maps_weather", typed.getToolName());
        assertEquals(88, typed.getResultLength());
        assertEquals(1234L, typed.getDurationMs());
    }

    @Test
    @DisplayName("SkillMetrics 往返：type=skill、mode/script 字段不丢")
    void skillMetricsRoundTrips() {
        SkillMetrics metrics = new SkillMetrics();
        metrics.setDurationMs(7L);
        metrics.setSkillName("pdf-summary");
        metrics.setMode("script");
        metrics.setScript("scripts/main.py");
        metrics.setResultLength(1024);

        String json = JsonUtil.toJson(metrics);
        assertTrue(json.contains("\"type\":\"skill\""), "JSON 必须带 type=skill，实际：" + json);

        NodeExecutionMetrics restored = JsonUtil.fromJson(json, NodeExecutionMetrics.class);
        SkillMetrics typed = assertInstanceOf(SkillMetrics.class, restored, "必须还原成 SkillMetrics 而不是基类");
        assertEquals("pdf-summary", typed.getSkillName());
        assertEquals("script", typed.getMode());
        assertEquals("scripts/main.py", typed.getScript());
        assertEquals(1024, typed.getResultLength());
    }

    @Test
    @DisplayName("历史数据（没有 type 字段）仍能读成基类，不能因为多态改造而反序列化失败")
    void legacyMetricsWithoutTypeStillDeserialize() {
        NodeExecutionMetrics restored = JsonUtil.fromJson("{\"durationMs\":42}", NodeExecutionMetrics.class);

        assertInstanceOf(NodeExecutionMetrics.class, restored);
        assertEquals(42L, restored.getDurationMs());
    }
}
