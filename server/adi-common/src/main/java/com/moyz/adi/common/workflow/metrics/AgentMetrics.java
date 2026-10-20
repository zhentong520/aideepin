package com.moyz.adi.common.workflow.metrics;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * Agent 节点可观测指标
 * <p>
 * Agent node observability metrics — extends LLMMetrics with RAG and Character info.
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetrics extends LLMMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识检索结果数量
     * <p>
     * Knowledge retrieval result count.
     * </p>
     */
    private Integer retrievalCount;

    /**
     * 关联的角色 UUID
     * <p>
     * Associated Character UUID.
     * </p>
     */
    private String characterUuid;

    // ---------------- T6-14 指标补全（2026-10-20） ----------------

    /**
     * LLM 调用次数（含工具循环中的多次调用）
     * <p>
     * LLM invocation count within this agent run (including tool-loop calls).
     * </p>
     */
    private Integer llmCallCount;

    /**
     * token 总量（输入+输出）
     * <p>
     * Total token count (input + output).
     * </p>
     */
    private Integer totalTokens;

    /**
     * 工具（MCP/web search 等）调用次数
     * <p>
     * Tool invocation count (MCP tools, web search, ...).
     * </p>
     */
    private Integer toolCallCount;

    /**
     * LLM 调用累计耗时（毫秒）
     * <p>
     * Accumulated LLM call duration in ms.
     * </p>
     */
    private Long llmDurationMs;

    /**
     * 降级次数（T6-09 模型降级路由预留字段，当前恒 0）
     * <p>
     * Degradation count (reserved for T6-09 model fallback routing).
     * </p>
     */
    private Integer degradedCount;

    /**
     * 预算中止次数（T6-10 工具循环预算预留字段：深度/墙钟/token 任一超限即 +1）
     * <p>
     * Budget-abort count (reserved for T6-10 tool-loop budget: depth/wall-clock/token).
     * </p>
     */
    private Integer budgetAbortCount;
}
