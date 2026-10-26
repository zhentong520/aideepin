package com.moyz.adi.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of executing a Character in agent mode.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentResult {

    /** The agent's text response. */
    private String answer;

    /** Thinking content from reasoning models, may be null. */
    private String thinking;

    /** Input token count. */
    private Integer inputTokens;

    /** Output token count. */
    private Integer outputTokens;

    /** Knowledge retrieval result count. */
    private Integer retrievalCount;

    // ---------------- T6-14 指标补全（2026-10-20） ----------------

    /** LLM 调用次数（本实现单次 invoke 一次调用；工具循环实现后累加） | LLM invocation count. */
    private Integer llmCallCount;

    /** token 总量（输入+输出） | Total token count. */
    private Integer totalTokens;

    /** 工具调用次数 | Tool invocation count. */
    private Integer toolCallCount;

    /** LLM 调用累计耗时毫秒 | Accumulated LLM duration ms. */
    private Long llmDurationMs;

    /** 降级次数（T6-09 预留） | Degradation count (reserved). */
    private Integer degradedCount;

    /** 预算中止次数（T6-10 预留） | Budget-abort count (reserved). */
    private Integer budgetAbortCount;
}
