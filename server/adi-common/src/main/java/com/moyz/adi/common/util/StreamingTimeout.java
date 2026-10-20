package com.moyz.adi.common.util;

import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * LLM streaming 超时配置（T6-08）：从 adi_sys_config 热配置读取，
 * 替换各 LLM Service 中硬编码的 60s。
 * <p>
 * 管理端保存系统配置后 {@code SysConfigService.edit → loadAndCache} 立即刷新
 * LocalCache，下次构建 StreamingChatModel 即用新值（每个请求都会重建模型，天然热生效）。
 * 未配置 / 值非法时回退默认 60s；最小 5s 防误配为 0 导致请求永不超时。
 * <p>
 * Streaming timeout from hot system config (T6-08), replacing the hardcoded 60s.
 */
@Slf4j
public final class StreamingTimeout {

    private StreamingTimeout() {
    }

    public static final int DEFAULT_SECONDS = 60;
    public static final int MIN_SECONDS = 5;

    public static Duration get() {
        return Duration.of(getSeconds(), ChronoUnit.SECONDS);
    }

    public static int getSeconds() {
        int seconds = DEFAULT_SECONDS;
        try {
            seconds = SysConfigService.getIntByKey(AdiConstant.SysConfigKey.LLM_STREAMING_TIMEOUT_SECONDS, DEFAULT_SECONDS);
        } catch (Exception e) {
            // 无 Spring 上下文（单测/启动早期）走默认值
        }
        if (seconds < MIN_SECONDS) {
            log.warn("llm_streaming_timeout_seconds={} too small, fallback to {}s", seconds, DEFAULT_SECONDS);
            return DEFAULT_SECONDS;
        }
        return seconds;
    }
}
