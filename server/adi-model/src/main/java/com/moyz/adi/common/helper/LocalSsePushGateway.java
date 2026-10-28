package com.moyz.adi.common.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * S2-06：本地直连实现（{@code adi.sse.push-mode=local} 时启用）。
 * 单实例部署或显式关闭 pub/sub 时使用，事件只投本实例持有的连接。
 * <p>
 * Local-only implementation ({@code adi.sse.push-mode=local}); events never
 * leave the instance. For single-instance deployments or when pub/sub is off.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "adi.sse.push-mode", havingValue = "local")
public class LocalSsePushGateway implements SsePushGateway {

    private final SseNotificationRegistry registry;

    public LocalSsePushGateway(SseNotificationRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void push(long userId, String eventName, String data) {
        try {
            registry.sendToUser(userId, eventName, data);
        } catch (Exception e) {
            log.warn("local SSE push failed, userId:{}, event:{}", userId, eventName, e);
        }
    }
}
