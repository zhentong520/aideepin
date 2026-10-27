package com.moyz.adi.common.helper;

import com.moyz.adi.common.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S1-14：SSE 发送失败韧性回归。
 * 客户端中途断开时，sendAudio/sendThinking/parseAndSendPartialMsg/sendPartial
 * 不得上抛异常中断 LLM 流消费（否则 onCompleteResponse 不触发，消息不落库、
 * token 统计丢失）；发送失败后 entry 被注销，后续收尾调用安全降级。
 */
class SseManagerSendResilienceTest {

    private SseManager manager;
    private SseEmitter brokenEmitter;

    @BeforeEach
    void setUp() {
        manager = new SseManager();
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        ReflectionTestUtils.setField(manager, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(manager, "maxWebConcurrent", 5);
        manager.holdSelfInstance();

        brokenEmitter = mock(SseEmitter.class);
    }

    private void registerBrokenClient(String uuid, Exception failure) throws IOException {
        manager.register(uuid, brokenEmitter, 42L);
        doThrow(failure).when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void sendAudioDoesNotThrowOnClientDisconnect() throws IOException {
        registerBrokenClient("audio-1", new IOException("broken pipe"));

        assertDoesNotThrow(() -> SseManager.sendAudio("audio-1", "base64-audio"));
        assertTrue(manager.isCompleted("audio-1"), "entry must be unregistered after failed send");
    }

    @Test
    void sendThinkingDoesNotThrowOnClientDisconnect() throws IOException {
        registerBrokenClient("thinking-1", new IOException("broken pipe"));

        assertDoesNotThrow(() -> SseManager.sendThinking("thinking-1", "let me think"));
        assertTrue(manager.isCompleted("thinking-1"));
    }

    @Test
    void parseAndSendPartialMsgDoesNotThrowOnClientDisconnect() throws IOException {
        registerBrokenClient("partial-1", new IOException("broken pipe"));

        assertDoesNotThrow(() -> SseManager.parseAndSendPartialMsg("partial-1", "message", "chunk"));
        assertTrue(manager.isCompleted("partial-1"));
    }

    @Test
    void sendPartialDoesNotThrowOnIllegalState() throws IOException {
        registerBrokenClient("partial-2", new IllegalStateException("response already committed"));

        assertDoesNotThrow(() -> SseManager.sendPartial("partial-2", "message", "chunk"));
        assertTrue(manager.isCompleted("partial-2"));
    }

    @Test
    void finalizeAfterDisconnectIsSafe() throws IOException {
        //模拟"客户端断开 → send 失败注销 → 回调收尾仍继续执行"的完整时序
        registerBrokenClient("final-1", new IOException("broken pipe"));
        SseManager.sendPartial("final-1", "message", "chunk");
        assertTrue(manager.isCompleted("final-1"));

        //entry 已注销，sendComplete 走 "already completed" 分支，不再抛异常也不再发送
        assertDoesNotThrow(() -> manager.sendComplete(42L, "final-1", "meta"));
    }

    @Test
    void startSseSurvivesIllegalState() throws IOException {
        manager.register("start-1", brokenEmitter, 42L);
        doThrow(new IllegalStateException("response already committed"))
                .when(brokenEmitter).send(any(SseEmitter.SseEventBuilder.class));
        ReflectionTestUtils.setField(manager, "rateLimitHelper", mock(RateLimitHelper.class));

        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);

        assertDoesNotThrow(() -> manager.startSse(user, "start-1", brokenEmitter, "hi"));
        assertTrue(manager.isCompleted("start-1"));
    }
}
