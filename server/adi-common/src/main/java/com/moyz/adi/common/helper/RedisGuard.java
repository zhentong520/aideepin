package com.moyz.adi.common.helper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Redis 故障降级守卫（S1-10）
 * <p>
 * 把"Redis 不可用"从一场 500 事故降级为一次可预期的降级行为：
 * 调用方传入兜底值/兜底动作，Redis 故障时走降级路径而不是把异常炸到调用链上。
 * 恢复自动痊愈：守卫不缓存任何"Redis 挂了"状态，每次调用都真实尝试，
 * Redis 回来后下一个请求即恢复正常。
 * </p>
 * <p>
 * Redis failure guard (S1-10): turns "Redis unavailable" from an unhandled 500
 * into an expected degradation. Callers supply a fallback; the guard never
 * caches failure state — every call really attempts Redis, so recovery is
 * automatic on the next request after Redis comes back.
 * </p>
 * <p>
 * 只捕获 {@link DataAccessException}（Spring 对 Redis/数据访问异常的统一抽象，
 * Lettuce 连接失败表现为 RedisConnectionFailureException / RedisSystemException）。
 * 其它异常（NPE 等程序缺陷）不吞，让它照常暴露。
 * </p>
 */
@Slf4j
@Component
public class RedisGuard {

    /** 告警节流间隔：故障期间每个场景每 30s 至多一条 WARN，避免日志风暴 */
    private static final long WARN_INTERVAL_MS = 30_000;

    private final AtomicLong lastWarnAt = new AtomicLong(0);

    /**
     * 执行带返回值的 Redis 操作，失败时返回兜底值
     * <p>
     * Run a Redis read/write with a fallback value on failure.
     * </p>
     *
     * @param scene    场景标识（进日志，用于定位是哪处降级）/ scene tag for logs
     * @param op       Redis 操作 / the Redis operation
     * @param fallback 降级返回值 / fallback result
     */
    public <T> T call(String scene, Supplier<T> op, T fallback) {
        try {
            return op.get();
        } catch (DataAccessException e) {
            warnThrottled(scene, e);
            return fallback;
        }
    }

    /**
     * 执行无返回值的 Redis 操作，失败时静默跳过（记节流 WARN）
     * <p>
     * Run a void Redis operation, skipping silently (throttled warn) on failure.
     * </p>
     */
    public void run(String scene, Runnable op) {
        try {
            op.run();
        } catch (DataAccessException e) {
            warnThrottled(scene, e);
        }
    }

    private void warnThrottled(String scene, DataAccessException e) {
        long now = System.currentTimeMillis();
        long last = lastWarnAt.get();
        if (now - last >= WARN_INTERVAL_MS && lastWarnAt.compareAndSet(last, now)) {
            log.warn("Redis degraded, scene:{}, cause:{}", scene, e.getMessage());
        }
    }
}
