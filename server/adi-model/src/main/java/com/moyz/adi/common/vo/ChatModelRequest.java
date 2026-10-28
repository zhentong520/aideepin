package com.moyz.adi.common.vo;

import com.moyz.adi.common.interfaces.BizToolSpi;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import com.moyz.adi.common.multiagent.AgentCallChain;
import dev.langchain4j.mcp.client.McpClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 使用http与模型进行交互时需要用到的的参数
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatModelRequest {
    private String memoryId;
    private String systemMessage;
    private String userMessage;
    //Image URL, only effective for multimodal LLM
    private List<String> imageUrls;
    private List<McpClient> mcpClients;
    //Agent Skills 内置工具上下文（清单+工具集+技能映射），无技能时为 null
    private SkillToolSpi.SkillBundle skillBundle;
    //T9-M2（2026-10-28）：业务工具插件上下文（manifest+工具集），未启用插件时为 null
    private BizToolSpi.ToolBundle bizToolBundle;
    /**
     * 二开新增（2026-09-21）：对话用户 id 与角色 uuid，供 create_skill 工具归属与绑定
     * （阻塞路径工具执行线程 ThreadContext 不可达，需显式传递）。
     */
    private Long chatUserId;
    private String characterUuid;
    private String responseFormat;
    private Boolean returnThinking;
    private Boolean enableWebSearch;

    /**
     * T7-02/T7-04（2026-10-20）：当前 Agent 调用链，供 delegate_agent 工具在
     * LLM 工具回调线程做嵌套深度限制与双向互调防环（工具线程 ThreadContext
     * 不可达，链必须随参数显式传递，与 chatUserId/characterUuid 同理）。
     */
    private AgentCallChain agentCallChain;
}
