package com.moyz.adi.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for executing a Character in agent mode.
 *
 * <p>Specifies which Character to execute and which capabilities to enable
 * (RAG, MCP tools, web search). Decoupled from workflow-specific configuration.</p>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentRequest {

    /** Character UUID to execute. */
    private String characterUuid;

    /** Model platform (optional, uses system default if null). */
    private String modelPlatform;

    /** Model name (optional, uses system default if null). */
    private String modelName;

    /** Input text (rendered prompt). */
    private String inputText;

    /** Enable RAG retrieval from Character's knowledge bases. */
    private boolean enableRag;

    /** Enable MCP tools configured on the Character. */
    private boolean enableMcp;

    /** Enable web search. */
    private boolean enableWebSearch;

    /**
     * T7-02/T7-04（2026-10-20）：当前 Agent 调用链（嵌套深度限制 + 防环）。
     * 父 Agent 委派时必须把自己持有的链传下来（链上已含父与目标 Agent），
     * 子 Agent 内部再发起 delegate_agent 时才能正确判深/判环；为 null 时由
     * 执行层以本 Agent 为链根播种新链。
     */
    private com.moyz.adi.common.multiagent.AgentCallChain callChain;
}
