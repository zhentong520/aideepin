package com.moyz.adi.common.helper;

import com.moyz.adi.common.util.JsonUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * S2-06：RedisSsePushGateway 行为。
 * push 成功 → 只发布、不本地直投（投递统一走订阅回调，单一路径防重复投递）；
 * Redis 故障 → 降级本地直投（S1-10）；onMessage 解析后投递本地注册表。
 */
@ExtendWith(MockitoExtension.class)
class RedisSsePushGatewayTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private final SseNotificationRegistry registry = new SseNotificationRegistry();

    private RedisSsePushGateway newGateway() {
        return new RedisSsePushGateway(stringRedisTemplate, registry);
    }

    @Test
    void pushPublishesEnvelopeWithoutLocalDelivery() {
        RedisSsePushGateway gateway = newGateway();
        List<String> received = new ArrayList<>();
        CapturingRegistryListener listener = new CapturingRegistryListener();
        registry.register(1L, listener.emitter);

        gateway.push(1L, "[TASK_STATE_CHANGED]", "{\"status\":\"success\"}");

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).convertAndSend(eq("sse:push:channel"), bodyCaptor.capture());
        // 信封可解析回原始字段（发布/订阅两端契约）
        SsePushMessage msg = JsonUtil.fromJson(bodyCaptor.getValue(), SsePushMessage.class);
        assertNotNull(msg);
        assertEquals(1L, msg.getUserId());
        assertEquals("[TASK_STATE_CHANGED]", msg.getEventName());
        assertEquals("{\"status\":\"success\"}", msg.getData());
        // 关键：发布端不本地直投（否则订阅回执会造成本实例重复投递）
        assertEquals(0, received.size() + listener.emitter.sent.size());
    }

    @Test
    void pushDegradesToLocalDeliveryOnRedisFailure() {
        doThrow(new DataAccessResourceFailureException("down"))
                .when(stringRedisTemplate).convertAndSend(anyString(), anyString());
        RedisSsePushGateway gateway = newGateway();
        CapturingRegistryListener listener = new CapturingRegistryListener();
        registry.register(7L, listener.emitter);

        assertDoesNotThrow(() -> gateway.push(7L, "[TASK_STATE_CHANGED]", "{\"status\":\"fail\"}"));

        // 降级：本实例持有的连接仍收到
        assertEquals(1, listener.emitter.sent.size());
    }

    @Test
    void pushNeverThrowsOnSerializationError() {
        RedisSsePushGateway gateway = newGateway();
        // data 为 null 也能走通（信封允许 null data）
        assertDoesNotThrow(() -> gateway.push(1L, "[TASK_STATE_CHANGED]", null));
        verify(stringRedisTemplate).convertAndSend(eq("sse:push:channel"), anyString());
    }

    @Test
    void onMessageDeliversToLocallyHeldConnections() {
        RedisSsePushGateway gateway = newGateway();
        CapturingRegistryListener listener = new CapturingRegistryListener();
        registry.register(3L, listener.emitter);
        CapturingRegistryListener otherUser = new CapturingRegistryListener();
        registry.register(4L, otherUser.emitter);

        String body = JsonUtil.toJson(new SsePushMessage(3L, "[TASK_STATE_CHANGED]", "{\"taskType\":\"draw\"}"));
        gateway.onMessage(body);

        assertEquals(1, listener.emitter.sent.size());
        assertEquals(0, otherUser.emitter.sent.size());
    }

    @Test
    void onMessageSwallowsMalformedPayload() {
        RedisSsePushGateway gateway = newGateway();
        assertDoesNotThrow(() -> gateway.onMessage("not-json"));
        assertDoesNotThrow(() -> gateway.onMessage(""));
        assertDoesNotThrow(() -> gateway.onMessage(null));
    }

    /** capture 用的注册表监听者（真实 emitter 捕获 send） */
    private static class CapturingRegistryListener {
        final CapturingEmitter emitter = new CapturingEmitter();
    }

    /** 与 SseNotificationRegistryTest 相同的捕获 emitter（测试类独立，避免跨测试类耦合） */
    private static class CapturingEmitter extends org.springframework.web.servlet.mvc.method.annotation.SseEmitter {
        final List<String> sent = new ArrayList<>();

        CapturingEmitter() {
            super(Long.MAX_VALUE);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) {
            sent.add("event");
        }
    }
}
