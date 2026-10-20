package com.moyz.adi.common.languagemodel.data;

import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.interfaces.TriConsumer;
import com.moyz.adi.common.languagemodel.data.LLMResponseContent;
import com.moyz.adi.common.service.skill.SkillToolService;
import com.moyz.adi.common.vo.AnswerMeta;
import com.moyz.adi.common.vo.PromptMeta;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class InnerStreamChatParam {
    private String uuid;
    /**
     * SSE 请求标识，用于从注册中心获取 SseEmitter
     * <p>
     * SSE request identifier, used to look up SseEmitter from the registry.
     * </p>
     */
    private String sseUuid;
    private List<McpClient> mcpClients;
    //Agent Skills 内置工具上下文，无技能时为 null
    private SkillToolService.SkillBundle skillBundle;
    /**
     * 二开新增（2026-09-21）：当前对话角色 uuid，供 create_skill 工具绑定新技能。
     */
    private String characterUuid;
    private StreamingChatModel streamingChatModel;
    private ChatRequest chatRequest;
    private Integer answerContentType;
    private TriConsumer<LLMResponseContent, PromptMeta, AnswerMeta> consumer;
    private User user;
    @Builder.Default
    private int toolCallDepth = 0;

    // ==================== T6-08/T6-09/T6-10 二开新增（2026-10-20） ====================

    /**
     * T6-08：是否已向客户端输出首 token（含 thinking 片段）。
     * ⚠️ 一旦为 true，超时/错误后只中止不重试——重试会产生重复输出。
     */
    @Builder.Default
    private java.util.concurrent.atomic.AtomicBoolean firstTokenSent = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * T6-08/T6-09：streaming 已尝试次数（首次失败后 = 1），供 StreamingRetryPolicy 决策。
     */
    @Builder.Default
    private int streamAttempt = 0;

    /**
     * T6-09：本次请求已尝试过的模型名（含主模型），降级链选下一个备用模型时排除。
     */
    @Builder.Default
    private java.util.Set<String> triedModels = new java.util.LinkedHashSet<>();

    /**
     * T6-08/T6-09：构建 StreamingChatModel 用的模型属性（降级切换时重建模型需要）。
     */
    private com.moyz.adi.common.vo.ChatModelBuilderProperties modelProperties;

    /**
     * T6-10：工具循环三重预算（深度 + 墙钟时间 + token 累计）。
     */
    @Builder.Default
    private ToolLoopBudget toolLoopBudget = ToolLoopBudget.fromConfig();

    public void markFirstToken() {
        firstTokenSent.set(true);
    }

    public boolean isFirstTokenSent() {
        return firstTokenSent.get();
    }
}
