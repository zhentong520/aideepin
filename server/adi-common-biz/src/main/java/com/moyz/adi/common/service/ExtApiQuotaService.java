package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.RedisKeyConstant;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * External API 限流与配额的 Redis 计数实现（T1-09，2026-10-19）。
 * <p>
 * 三层计数，全部以「keyHash（原始 ext key 的 SHA-256 前 32 位）」为维度，
 * 与 {@link com.moyz.adi.common.filter.ExtApiRateLimitFilter} 配套：
 * <ol>
 *   <li>QPS：Redis 固定 1 秒窗口，{@code INCR + EXPIRE 2s}；</li>
 *   <li>日调用次数：{@code INCR}，首次写入时设置当日过期（多留 1 小时缓冲防时钟漂移）；</li>
 *   <li>日 token：聊天类 ext 接口在 LLM 调用返回处调 {@link #recordTokens} 累计
 *       prompt+completion；请求进入时用 {@link #isTokenQuotaExceeded} 做前置检查
 *       （token 数在响应后才知道，只能事后累计 + 事前拦截）。</li>
 * </ol>
 * 计数语义：<b>先计数后判断</b>（{@code INCR} 返回值即当前窗口内第 N 次调用），
 * 第 quota 次调用放行、第 quota+1 次 429——单测必须钉死这个边界（历史事故：曾出现
 * 额度检查 {@code len>0} 判 PASS 的假绿）。
 */
@Slf4j
@Service
public class ExtApiQuotaService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** QPS 窗口 key 的过期时间：2 秒，覆盖窗口边界 */
    private static final Duration QPS_EXPIRE = Duration.ofSeconds(2);

    /** 日配额 key 过期：当日 0 点 + 1 小时缓冲，防时钟漂移导致计数器提前清零 */
    private static final Duration DAILY_EXPIRE_BUFFER = Duration.ofHours(1);

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 总开关（铁律 10）：关闭时三层限制全部放行 */
    public boolean isEnabled() {
        return null != adiProperties.getExt() && adiProperties.getExt().isEnabled();
    }

    /**
     * QPS 限流：返回 true 表示放行。固定 1 秒窗口，超过 qps-limit 拒绝。
     */
    public boolean tryAcquireQps(String keyHash) {
        int limit = adiProperties.getExt().getQpsLimit();
        long epochSecond = System.currentTimeMillis() / 1000;
        String key = MessageFormat.format(RedisKeyConstant.EXT_RATE_LIMIT_QPS, keyHash, epochSecond);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (null != count && count == 1L) {
            stringRedisTemplate.expire(key, QPS_EXPIRE);
        }
        return null == count || count <= limit;
    }

    /**
     * 日调用次数配额：INCR 后判断，返回 true 表示放行。
     * 第 daily-request-quota 次调用放行，第 quota+1 次拒绝。
     */
    public boolean tryConsumeDailyRequest(String keyHash) {
        int quota = adiProperties.getExt().getDailyRequestQuota();
        String key = dailyRequestKey(keyHash);
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (null != count && count == 1L) {
            stringRedisTemplate.expire(key, dailyExpire());
        }
        return null == count || count <= quota;
    }

    /**
     * 日 token 配额前置检查：当日累计（含本次预估）是否已超配额。
     * token 消耗发生在响应之后，进入时只能拿「已累计值」做拦截。
     */
    public boolean isTokenQuotaExceeded(String keyHash, int estimatedTokens) {
        long quota = adiProperties.getExt().getDailyTokenQuota();
        if (quota <= 0) {
            // 0 = 不限制
            return false;
        }
        return todayTokens(keyHash) + Math.max(0, estimatedTokens) > quota;
    }

    /**
     * LLM 调用返回处上报本次 token 消耗（prompt+completion），返回累计后的当日总量。
     * 聊天类 ext 接口必须接入；纯检索接口（T1-08 retrieve / MCP kb_search）不消耗 token，不调用。
     */
    public long recordTokens(String keyHash, int promptTokens, int completionTokens) {
        int delta = Math.max(0, promptTokens) + Math.max(0, completionTokens);
        if (delta <= 0) {
            return todayTokens(keyHash);
        }
        String key = dailyTokenKey(keyHash);
        Long total = stringRedisTemplate.opsForValue().increment(key, delta);
        if (null != total && total == delta) {
            stringRedisTemplate.expire(key, dailyExpire());
        }
        return null == total ? 0 : total;
    }

    public long todayTokens(String keyHash) {
        String val = stringRedisTemplate.opsForValue().get(dailyTokenKey(keyHash));
        return null == val ? 0 : parseLong(val);
    }

    public long todayRequests(String keyHash) {
        String val = stringRedisTemplate.opsForValue().get(dailyRequestKey(keyHash));
        return null == val ? 0 : parseLong(val);
    }

    /**
     * 距当日结束的秒数（429 Retry-After 用，向上取整）
     */
    public static long secondsUntilMidnight() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        long secondsToday = 86400L - now.toSecondOfDay();
        return Math.max(1, secondsToday);
    }

    public static String sha256Short(String rawKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 32);
        } catch (java.security.NoSuchAlgorithmException e) {
            // JVM 必有 SHA-256，不可达；退化为 hashCode 防止完全无法限流
            return String.format("%032x", rawKey.hashCode());
        }
    }

    private String dailyRequestKey(String keyHash) {
        return MessageFormat.format(RedisKeyConstant.EXT_DAILY_REQUEST_COUNT, keyHash, LocalDate.now().format(DAY_FORMAT));
    }

    private String dailyTokenKey(String keyHash) {
        return MessageFormat.format(RedisKeyConstant.EXT_DAILY_TOKEN_COUNT, keyHash, LocalDate.now().format(DAY_FORMAT));
    }

    private Duration dailyExpire() {
        return Duration.ofSeconds(secondsUntilMidnight()).plus(DAILY_EXPIRE_BUFFER);
    }

    private static long parseLong(String val) {
        try {
            return Long.parseLong(val);
        } catch (NumberFormatException e) {
            log.warn("Invalid quota counter value:{}", val);
            return 0;
        }
    }
}
