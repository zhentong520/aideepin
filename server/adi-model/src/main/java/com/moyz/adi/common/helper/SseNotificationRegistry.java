package com.moyz.adi.common.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * S2-06：用户级 SSE 通知连接注册表（/notify/sse 长连通道的本实例持有者）。
 * <p>
 * 与 {@link SseManager} 分离：SseManager 管请求级聊天/工作流流（短生命周期、带限流），
 * 这里管用户通知长连（随页面开闭，无业务限流语义）。sendToUser 只投递本实例持有的连接，
 * 跨实例由 {@link SsePushGateway} 负责路由。
 * <p>
 * User-level SSE notification connection registry (backs the /notify/sse channel).
 * Deliberately separate from {@link SseManager}: that one manages request-scoped
 * chat/workflow streams with rate limiting; this one holds long-lived notification
 * connections. Cross-instance routing is the gateway's job.
 */
@Slf4j
@Service
public class SseNotificationRegistry {

    private final Map<Long, Set<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    public void register(long userId, SseEmitter emitter) {
        userEmitters.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(emitter);
    }

    public void unregister(long userId, SseEmitter emitter) {
        Set<SseEmitter> set = userEmitters.get(userId);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                userEmitters.remove(userId, set);
            }
        }
    }

    public boolean hasConnection(long userId) {
        Set<SseEmitter> set = userEmitters.get(userId);
        return set != null && !set.isEmpty();
    }

    /**
     * 向该用户在本实例上的所有通知连接发送事件（尽力而为，永不抛出）。
     * 断开的连接在发送失败时移除，等待客户端重连重新注册。
     * <p>
     * Best-effort send to all local notification connections of the user; broken
     * connections are dropped on send failure and re-registered on client reconnect.
     */
    public void sendToUser(long userId, String eventName, String data) {
        Set<SseEmitter> set = userEmitters.get(userId);
        if (set == null || set.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                log.info("notify send failed (client likely disconnected), userId:{}, event:{}", userId, eventName);
                unregister(userId, emitter);
            } catch (Exception e) {
                // 未知异常同样不外抛：通知是附加加速信息，主流程由轮询兜底
                log.warn("notify send unexpected error, userId:{}, event:{}", userId, eventName, e);
            }
        }
    }
}
