package com.moyz.adi.common.workflow.node.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.util.JsonUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-03：Orchestrator 节点 DSL（{@link OrchestratorNodeConfig}）解析单测。
 * <p>
 * 工作流 JSON DSL 能定义 orchestrator 节点（任务书要求）——这里锁死 DSL 的
 * 字段映射（snake_case ↔ camelCase）、缺省值与嵌套结构，防止前后端各写一套字段名。
 */
class OrchestratorNodeConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ObjectNode dsl(String raw) throws Exception {
        return (ObjectNode) MAPPER.readTree(raw);
    }

    @Test
    @DisplayName("完整 DSL：sub_agents / summary_prompt / fail_fast / max_parallel 全字段解析")
    void parsesFullDsl() throws Exception {
        OrchestratorNodeConfig config = JsonUtil.fromJson(dsl("""
                {
                  "sub_agents": [
                    {"character_uuid": "uuid-analyst", "task_id": "sales", "prompt": "分析销售数据：{input}"},
                    {"character_uuid": "uuid-writer", "task_id": "report"}
                  ],
                  "summary_prompt": "请把以下子任务结果汇总成一份报告：\\n{results}",
                  "fail_fast": true,
                  "max_parallel": 2
                }
                """), OrchestratorNodeConfig.class);

        assertNotNull(config);
        assertEquals(2, config.getSubAgents().size());
        OrchestratorNodeConfig.SubAgentSpec first = config.getSubAgents().get(0);
        assertEquals("uuid-analyst", first.getCharacterUuid());
        assertEquals("sales", first.getTaskId());
        assertEquals("分析销售数据：{input}", first.getPrompt());
        assertEquals("report", config.getSubAgents().get(1).getTaskId());
        assertTrue(config.getSummaryPrompt().contains("{results}"));
        assertEquals(Boolean.TRUE, config.getFailFast());
        assertEquals(2, config.getMaxParallel());
    }

    @Test
    @DisplayName("缺省值：fail_fast=false / max_parallel=4 / enable_rag=true（不阻塞其他是默认行为）")
    void defaultsMatchTaskBook() throws Exception {
        OrchestratorNodeConfig config = JsonUtil.fromJson(dsl("""
                {"sub_agents": [{"character_uuid": "uuid-1"}]}
                """), OrchestratorNodeConfig.class);
        assertEquals(Boolean.FALSE, config.getFailFast());
        assertEquals(4, config.getMaxParallel());
        assertEquals(Boolean.TRUE, config.getEnableRag());
        assertEquals(Boolean.TRUE, config.getEnableMcp());
    }

    @Test
    @DisplayName("可选字段容错：无 prompt 时子任务透传上游输入（task_id 缺省由节点兜底为 character_uuid）")
    void optionalFieldsTolerated() throws Exception {
        OrchestratorNodeConfig config = JsonUtil.fromJson(dsl("""
                {"sub_agents": [{"character_uuid": "uuid-1", "model_platform": "deepseek", "model_name": "deepseek-chat"}]}
                """), OrchestratorNodeConfig.class);
        OrchestratorNodeConfig.SubAgentSpec spec = config.getSubAgents().get(0);
        assertEquals(null, spec.getTaskId());
        assertEquals(null, spec.getPrompt());
        assertEquals("deepseek", spec.getModelPlatform());
        assertEquals("deepseek-chat", spec.getModelName());
    }

    @Test
    @DisplayName("契约校验注解在位：sub_agents @NotEmpty、character_uuid @NotBlank（checkAndGetConfig 的 validator 依据）")
    void validationAnnotationsPresent() throws Exception {
        OrchestratorNodeConfig empty = JsonUtil.fromJson(dsl("""
                {"sub_agents": []}
                """), OrchestratorNodeConfig.class);
        assertTrue(empty.getSubAgents().isEmpty());
        // jakarta 校验注解是 RUNTIME 保留，节点执行期由 validator 拦截；这里确认声明没被删
        assertNotNull(OrchestratorNodeConfig.class.getDeclaredField("subAgents")
                .getAnnotation(jakarta.validation.constraints.NotEmpty.class));
        assertNotNull(OrchestratorNodeConfig.SubAgentSpec.class.getDeclaredField("characterUuid")
                .getAnnotation(jakarta.validation.constraints.NotBlank.class));
    }
}
