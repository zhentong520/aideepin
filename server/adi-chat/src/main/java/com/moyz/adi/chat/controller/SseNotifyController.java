package com.moyz.adi.chat.controller;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.constant.SseConstant;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.helper.SseNotificationRegistry;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * S2-06：用户级 SSE 通知通道（长连）。
 * <p>
 * 前端登录后保持一条连接，接收跨实例推送的任务终态事件
 * （{@code [TASK_STATE_CHANGED]}，由 worker 实例经 SsePushGateway 广播而来）。
 * 与聊天 SSE 分离：不占聊天并发配额、无业务限流语义。
 * 超时 30min，客户端（fetch-event-source）断线自动重连、重连即重新注册。
 * <p>
 * User-level SSE notification channel. Long-lived, one per tab; receives
 * cross-instance task terminal events. Separate from chat SSE (no chat
 * concurrency quota). 30-min timeout; the client reconnects automatically.
 */
@Slf4j
@RestController
@RequestMapping("/notify")
public class SseNotifyController {

    /** 通知长连超时：30min（到时客户端自动重连重注册，同时兜底清僵尸连接） */
    private static final long NOTIFY_SSE_TIMEOUT = 30 * 60 * 1000L;

    @Resource
    private SseNotificationRegistry sseNotificationRegistry;

    @Operation(summary = "任务状态通知 SSE 通道 | Task state notification SSE channel")
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        User user = ThreadContext.getCurrentUser();
        long userId = user.getId();
        SseEmitter emitter = new SseEmitter(NOTIFY_SSE_TIMEOUT);
        sseNotificationRegistry.register(userId, emitter);

        Runnable cleanup = () -> sseNotificationRegistry.unregister(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            log.info("notify sse timeout,userId:{}", userId);
            emitter.complete();
            cleanup.run();
        });
        emitter.onError(throwable -> {
            log.info("notify sse error,userId:{}", userId);
            cleanup.run();
        });

        // 立即回一个 START：连接建立成功信号，也防止代理认为空响应
        try {
            emitter.send(SseEmitter.event().name(SseConstant.SSEEventName.START));
        } catch (IOException e) {
            log.warn("notify sse start send failed (client disconnected),userId:{}", userId);
            cleanup.run();
        }
        return emitter;
    }
}
