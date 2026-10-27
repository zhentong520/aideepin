package com.moyz.adi.common.helper;

import com.moyz.adi.common.vo.RequestRateLimit;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RateLimitHelper {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisGuard redisGuard;

    /**
     * 按固定时间窗口计算请求次数
     * Calculate request count using a fixed time window
     *
     * @param requestTimesKey redis key
     * @param rateLimitConfig 请求频率限制配置 / Request rate limit configuration
     * @return
     */
    public boolean checkRequestTimes(String requestTimesKey, RequestRateLimit rateLimitConfig) {
        // 限流配置为空时直接放行并记错误日志：宁可少拦一次请求，也不能因为一份坏配置
        // 让对话/工作流/绘图等所有文本接口集体 500。
        // When the config is missing, allow the request and log an error: a single bad config must not
        // take down every text endpoint with a server error.
        if (null == rateLimitConfig) {
            log.error("Rate limit config is null, request allowed without rate limiting, key:{}", requestTimesKey);
            return true;
        }
        // S1-10：Redis 故障时 fail-open（放行）——故障期间限流暂不生效，但对话链路不塌；
        // 计数仍留在 Redis 里，恢复后从原计数继续，不会重复发放额度。
        String rateLimitVal = redisGuard.call("rate-limit-check",
                () -> stringRedisTemplate.opsForValue().get(requestTimesKey), null);
        int requestCountInTimeWindow = 0;
        if (StringUtils.isNotBlank(rateLimitVal)) {
            requestCountInTimeWindow = Integer.parseInt(rateLimitVal);
        }
        return requestCountInTimeWindow < rateLimitConfig.getTimes();
    }

    public void increaseRequestTimes(String requestTimesKey, RequestRateLimit rateLimitConfig) {
        if (null == rateLimitConfig) {
            return;
        }
        // S1-10：Redis 故障时跳过计数（仅少记一次），不向调用链抛异常
        redisGuard.run("rate-limit-increase", () -> {
            Long expire = stringRedisTemplate.getExpire(requestTimesKey);
            long expireTime = expire == null ? -2L : expire;
            if (expireTime == -1) {
                stringRedisTemplate.opsForValue().increment(requestTimesKey);
                stringRedisTemplate.opsForValue().set(requestTimesKey, String.valueOf(1), rateLimitConfig.getMinutes(), TimeUnit.MINUTES);
            } else if (expireTime > 3) {
                //If expireTime <= 3, too short to cache
                stringRedisTemplate.opsForValue().increment(requestTimesKey);
            }
        });
    }

}
