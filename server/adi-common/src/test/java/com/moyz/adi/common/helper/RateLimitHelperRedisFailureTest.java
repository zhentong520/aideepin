package com.moyz.adi.common.helper;

import com.moyz.adi.common.vo.RequestRateLimit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S1-10：限流计数在 Redis 故障时的降级行为。
 * <p>
 * 契约：Redis 挂掉时 checkRequestTimes 放行（fail-open，对话链路不塌）、
 * increaseRequestTimes 静默跳过（不抛异常）；Redis 恢复后计数从 Redis 原值继续，
 * 不会因为降级把窗口计数清零造成额度重复发放。
 * </p>
 */
class RateLimitHelperRedisFailureTest {

    private RateLimitHelper helper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        helper = new RateLimitHelper();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(helper, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(helper, "redisGuard", new RedisGuard());
    }

    private RequestRateLimit config() {
        RequestRateLimit config = new RequestRateLimit();
        config.setTimes(10);
        config.setMinutes(1);
        return config;
    }

    @DisplayName("S1-10：Redis 故障时限流检查 fail-open 放行，不抛 500")
    @Test
    void checkRequestTimesFailsOpenWhenRedisDown() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        assertTrue(helper.checkRequestTimes("rl:test", config()),
                "Redis 故障时 checkRequestTimes 应放行");
    }

    @DisplayName("S1-10：Redis 故障时 increaseRequestTimes 静默跳过")
    @Test
    void increaseRequestTimesSkipsWhenRedisDown() {
        when(redisTemplate.getExpire(anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        assertDoesNotThrow(() -> helper.increaseRequestTimes("rl:test", config()));
    }

    @DisplayName("S1-10：getExpire 返回 null 不再 NPE（键不存在等边界）")
    @Test
    void increaseRequestTimesToleratesNullExpire() {
        when(redisTemplate.getExpire(anyString())).thenReturn(null);
        assertDoesNotThrow(() -> helper.increaseRequestTimes("rl:test", config()));
    }
}
