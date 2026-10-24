package com.moyz.adi.common.workflow.node.orchestrator;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * T7-03（2026-10-20）：Orchestrator 编排节点配置（node_config DSL）。
 * <p>
 * JSON DSL 示例：
 * <pre>{@code
 * {
 *   "sub_agents": [
 *     {"character_uuid": "uuid-analyst", "task_id": "sales", "prompt": "分析销售数据：{input}"},
 *     {"character_uuid": "uuid-writer",  "task_id": "report"}
 *   ],
 *   "summary_prompt": "请把以下子任务结果汇总成一份报告：\n{results}",
 *   "fail_fast": false,
 *   "max_parallel": 4
 * }
 * }</pre>
 * 每个子 Agent 一项：未配 {@code prompt} 时直接透传上游输入文本；
 * {@code {input}} 占位符按上游输入渲染（同其他节点的 {@code renderTemplate} 口径）。
 */
@Data
public class OrchestratorNodeConfig {

    /**
     * 子任务分派清单（必填，至少一项）——按输入把子任务分派给多个子 Agent 并行执行。
     */
    @NotEmpty(message = "sub_agents 不能为空")
    @JsonProperty("sub_agents")
    private List<SubAgentSpec> subAgents;

    /**
     * 汇总提示词模板（可选）。{@code {results}} 占位符替换为黑板上全部成功结果的聚合文本；
     * 为空时直接输出黑板全量汇总（含失败记录）。
     */
    @JsonProperty("summary_prompt")
    private String summaryPrompt;

    /**
     * 失败即止（默认 false）：false = 某子任务失败只落黑板失败记录、不阻塞其他子任务
     * （任务书要求的行为）；true = 全部结束后若存在失败则整个节点判失败。
     */
    @JsonProperty("fail_fast")
    private Boolean failFast = false;

    /**
     * 最大并行度（默认 4，最小 1）。子任务数不足时按实际任务数起线程。
     */
    @JsonProperty("max_parallel")
    private Integer maxParallel = 4;

    /** 子 Agent 是否启用 RAG（默认 true）。 */
    @JsonProperty("enable_rag")
    private Boolean enableRag = true;

    /** 子 Agent 是否启用 MCP 工具（默认 true）。 */
    @JsonProperty("enable_mcp")
    private Boolean enableMcp = true;

    /**
     * 一个子任务的分派目标：调用哪个 Agent、任务文本用什么模板渲染。
     */
    @Data
    public static class SubAgentSpec {

        /** 目标 Agent（Character）uuid，必填。 */
        @NotBlank(message = "character_uuid 不能为空")
        @JsonProperty("character_uuid")
        private String characterUuid;

        /** 任务id（可选，缺省用 character_uuid）——黑板上的主键，编排内唯一。 */
        @JsonProperty("task_id")
        private String taskId;

        /** 子任务提示词模板（可选，缺省透传上游输入）。 */
        @JsonProperty("prompt")
        private String prompt;

        /** 模型平台（可选）。 */
        @JsonProperty("model_platform")
        private String modelPlatform;

        /** 模型名称（可选）。 */
        @JsonProperty("model_name")
        private String modelName;
    }
}
