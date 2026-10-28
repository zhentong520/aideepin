package com.moyz.adi.common.helper;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2-06：用户级通知连接注册表行为。
 * 用发送回调收集事件的方式断言「发到了谁」——emitter.send 的效果通过子类
 * 捕获而非 mock，保证断言的是真实发送路径。
 */
class SseNotificationRegistryTest {

    /** 捕获 send 内容的 emitter（覆盖 send 方法，其余行为真实） */
    private static class CapturingEmitter extends SseEmitter {
        final List<String> sent = new CopyOnWriteArrayList<>();
        boolean failOnSend;

        CapturingEmitter() {
            super(Long.MAX_VALUE);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("broken pipe");
            }
            sent.add("event");
        }
    }

    @Test
    void sendReachesAllConnectionsOfUser() throws Exception {
        SseNotificationRegistry registry = new SseNotificationRegistry();
        CapturingEmitter e1 = new CapturingEmitter();
        CapturingEmitter e2 = new CapturingEmitter();
        registry.register(1L, e1);
        registry.register(1L, e2);
        registry.register(2L, new CapturingEmitter());

        registry.sendToUser(1L, "[TASK_STATE_CHANGED]", "{\"status\":\"success\"}");

        assertEquals(1, e1.sent.size());
        assertEquals(1, e2.sent.size());
    }

    @Test
    void sendDoesNotLeakToOtherUsers() throws Exception {
        SseNotificationRegistry registry = new SseNotificationRegistry();
        CapturingEmitter other = new CapturingEmitter();
        registry.register(2L, other);

        registry.sendToUser(1L, "[TASK_STATE_CHANGED]", "{}");

        assertEquals(0, other.sent.size());
    }

    @Test
    void brokenConnectionRemovedOnSendFailure() {
        SseNotificationRegistry registry = new SseNotificationRegistry();
        CapturingEmitter broken = new CapturingEmitter();
        broken.failOnSend = true;
        registry.register(1L, broken);
        assertTrue(registry.hasConnection(1L));

        assertDoesNotThrow(() -> registry.sendToUser(1L, "[TASK_STATE_CHANGED]", "{}"));

        // 断开的连接被移除，hasConnection 变 false；再次发送不再触碰坏连接
        assertFalse(registry.hasConnection(1L));
        CapturingEmitter healthy = new CapturingEmitter();
        registry.register(1L, healthy);
        assertDoesNotThrow(() -> registry.sendToUser(1L, "[TASK_STATE_CHANGED]", "{}"));
        assertEquals(1, healthy.sent.size());
    }

    @Test
    void sendToUnknownUserIsNoop() {
        SseNotificationRegistry registry = new SseNotificationRegistry();
        assertDoesNotThrow(() -> registry.sendToUser(999L, "[TASK_STATE_CHANGED]", "{}"));
    }

    @Test
    void unregisterRemovesConnection() throws Exception {
        SseNotificationRegistry registry = new SseNotificationRegistry();
        CapturingEmitter emitter = new CapturingEmitter();
        registry.register(1L, emitter);
        registry.unregister(1L, emitter);
        assertFalse(registry.hasConnection(1L));

        registry.sendToUser(1L, "[TASK_STATE_CHANGED]", "{}");
        assertEquals(0, emitter.sent.size());
    }

    @Test
    void registerIsIdempotentPerEmitter() throws Exception {
        SseNotificationRegistry registry = new SseNotificationRegistry();
        CapturingEmitter emitter = new CapturingEmitter();
        registry.register(1L, emitter);
        registry.register(1L, emitter);

        registry.sendToUser(1L, "[TASK_STATE_CHANGED]", "{}");
        // Set 语义：同一 emitter 只投一次
        assertEquals(1, emitter.sent.size());
    }
}
