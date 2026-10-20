package com.moyz.adi.chat.controller;

import com.moyz.adi.common.dto.AskReq;
import com.moyz.adi.common.dto.RagFeedbackReq;
import com.moyz.adi.common.entity.RagFeedback;
import com.moyz.adi.common.service.CharacterChatService;
import com.moyz.adi.common.service.RagFeedbackService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 聊天控制器：负责 SSE 聊天端点。
 * <p>
 * Chat controller: handles SSE chat endpoint.
 * </p>
 */
@Tag(name = "Chat")
@RestController
@RequestMapping("/chat")
@Validated
public class ChatController {

    @Resource
    private CharacterChatService characterChatService;

    @Resource
    private RagFeedbackService ragFeedbackService;

    @Operation(summary = "发送一个prompt给模型 | Send Prompt to Model")
    @PostMapping(value = "/process", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ask(@RequestBody @Validated AskReq askReq) {
        return characterChatService.sseAsk(askReq);
    }

    /**
     * T7-01：对带知识库来源的回答提交 👍/👎 反馈。
     * <p>
     * 必须登录（ThreadContext 取当前用户）；同一用户同一检索一票，可改票。
     * ⚠️ user-web 页面接入由用户确认后进行——本批只交付 API + 实体（数据结构准备）。
     */
    @Operation(summary = "提交RAG回答反馈（👍/👎）| Submit RAG answer feedback",
            description = "queryId 为本次检索的 adi_rag_query_log.query_id；rating: PASS=👍 / FAIL=👎")
    @PostMapping("/rag-feedback")
    public RagFeedback ragFeedback(@RequestBody @Valid RagFeedbackReq req) {
        return ragFeedbackService.submit(req.getQueryId(), req.getMessageUuid(), req.getRating(), req.getComment());
    }
}
