package com.moyz.adi.common.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RedisTemplateUtil {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private static final RedisScript<Long> UNLOCK_REDIS_SCRIPT = RedisScript.of(UNLOCK_SCRIPT, Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisTemplateUtil(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public boolean lock(String key, String clientId, long lockExpireInSecond) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, clientId, lockExpireInSecond, TimeUnit.SECONDS));
    }

    public boolean unlock(String key, String clientId) {
        Long result = stringRedisTemplate.execute(UNLOCK_REDIS_SCRIPT, Collections.singletonList(key), clientId);
        return result != null && result > 0;
    }

    /**
     * 自增计数并给首个计数设置过期（固定窗口限流用）。返回自增后的值；Redis 异常时由调用方处理 null。
     */
    public Long increment(String key, long expireSeconds) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            stringRedisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        }
        return count;
    }

}
