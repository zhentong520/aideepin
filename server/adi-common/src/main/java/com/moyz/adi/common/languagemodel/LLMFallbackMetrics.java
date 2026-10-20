package com.moyz.adi.common.languagemodel;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 降级路由指标（T6-09）：进程内计数器 + 结构化日志。
 * 供监控采集/排查"今天降级了多少次、哪种错误最多、降级链是否耗尽过"。
 * <p>
 * Fallback routing metrics (T6-09): in-process counters plus structured logs.
 */
@Slf4j
public final class LLMFallbackMetrics {

    private LLMFallbackMetrics() {
    }

    private static final LLMFallbackMetrics INSTANCE = new LLMFallbackMetrics();

    public static LLMFallbackMetrics get() {
        return INSTANCE;
    }

    private final AtomicLong retriableDetected = new AtomicLong();
    private final AtomicLong permanentDetected = new AtomicLong();
    private final AtomicLong sameModelRetries = new AtomicLong();
    private final AtomicLong fallbackAttempts = new AtomicLong();
    private final AtomicLong fallbackSuccess = new AtomicLong();
    private final AtomicLong fallbackExhausted = new AtomicLong();

    public void recordDetected(LLMErrorType type, String primaryModel, String errorSummary) {
        if (type == LLMErrorType.RETRIABLE) {
            retriableDetected.incrementAndGet();
        } else {
            permanentDetected.incrementAndGet();
        }
        log.warn("llm-error-classified, type:{}, model:{}, error:{}", type, primaryModel, errorSummary);
    }

    public void recordSameModelRetry(String model, int attempt) {
        sameModelRetries.incrementAndGet();
        log.warn("llm-stream same-model retry, model:{}, attempt:{}", model, attempt);
    }

    public void recordFallbackAttempt(String fromModel, String toModel) {
        fallbackAttempts.incrementAndGet();
        log.warn("llm-fallback routing, from:{}, to:{}", fromModel, toModel);
    }

    public void recordFallbackSuccess(String model) {
        fallbackSuccess.incrementAndGet();
        log.info("llm-fallback success, model:{}", model);
    }

    public void recordFallbackExhausted(String primaryModel, String errorSummary) {
        fallbackExhausted.incrementAndGet();
        log.error("llm-fallback chain exhausted, primary:{}, last error:{}", primaryModel, errorSummary);
    }

    public long getRetriableDetected() {
        return retriableDetected.get();
    }

    public long getPermanentDetected() {
        return permanentDetected.get();
    }

    public long getSameModelRetries() {
        return sameModelRetries.get();
    }

    public long getFallbackAttempts() {
        return fallbackAttempts.get();
    }

    public long getFallbackSuccess() {
        return fallbackSuccess.get();
    }

    public long getFallbackExhausted() {
        return fallbackExhausted.get();
    }
}
