package com.moyz.adi.common.languagemodel;

import com.moyz.adi.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM 错误分类器（T6-09）：把 Throwable 归类为 {@link LLMErrorType#RETRIABLE} 或
 * {@link LLMErrorType#PERMANENT}，供降级路由决定"换模型重试"还是"直接报错"。
 * <p>
 * 分类依据（按优先级）：
 * 1. {@link BaseException}（本地业务/配置错误）→ PERMANENT；
 * 2. HTTP 状态码（从异常消息中提取）：401/403/404/400/422 → PERMANENT；
 *    429/500/502/503/504 → RETRIABLE；
 * 3. 关键词：timeout/connection/network/reset/broken pipe/rate limit → RETRIABLE；
 *    unauthorized/forbidden/invalid api key/api key not valid/model not found → PERMANENT；
 * 4. 未知错误 → RETRIABLE（网络瞬时故障概率更高；降级链有次数上限，不会无限烧额度）。
 * <p>
 * LLM error classifier (T6-09).
 */
@Slf4j
public final class LLMErrorClassifier {

    private LLMErrorClassifier() {
    }

    /** 从异常消息中提取 HTTP 状态码（形如 "401 Unauthorized"、"HTTP 429"） */
    private static final Pattern STATUS_CODE = Pattern.compile("\\b(?:http\\s*[:,/]?\\s*)?(4\\d{2}|5\\d{2})\\b");

    private static final String[] PERMANENT_KEYWORDS = {
            "unauthorized", "forbidden", "invalid api key", "api key not valid", "invalid_api_key",
            "authentication", "model not found", "model_not_found", "does not exist",
            "invalid request", "invalid_request", "bad request", "unsupported",
            "secret key", "permission denied", "quota configuration", "not configured"
    };

    private static final String[] RETRIABLE_KEYWORDS = {
            "timeout", "timed out", "connection", "connect", "network", "reset",
            "broken pipe", "eof", "rate limit", "rate_limit", "too many requests",
            "overloaded", "unavailable", "internal server error", "bad gateway",
            "service unavailable", "gateway timeout", "temporarily", "retry"
    };

    public static LLMErrorType classify(Throwable error) {
        if (null == error) {
            // 无异常信息时按可重试处理（保守：给降级链一次机会）
            return LLMErrorType.RETRIABLE;
        }
        if (error instanceof BaseException) {
            // 本地业务/配置错误（参数缺失、服务未配置 key 等）——降级也没用
            return LLMErrorType.PERMANENT;
        }
        String message = StringUtils.defaultString(error.getMessage()) + " "
                + StringUtils.defaultString(error.getClass().getSimpleName());
        String lower = message.toLowerCase();

        // 1. 显式 HTTP 状态码
        Matcher matcher = STATUS_CODE.matcher(message);
        if (matcher.find()) {
            int code = Integer.parseInt(matcher.group(1));
            return switch (code) {
                case 401, 403, 404, 400, 422 -> LLMErrorType.PERMANENT;
                case 429, 500, 502, 503, 504 -> LLMErrorType.RETRIABLE;
                default -> null == classifyByKeyword(lower) ? LLMErrorType.RETRIABLE : classifyByKeyword(lower);
            };
        }

        // 2. 关键词兜底
        LLMErrorType byKeyword = classifyByKeyword(lower);
        if (null != byKeyword) {
            return byKeyword;
        }

        // 3. 未知错误默认可重试（降级链有次数上限）
        return LLMErrorType.RETRIABLE;
    }

    private static LLMErrorType classifyByKeyword(String lowerMessage) {
        for (String kw : PERMANENT_KEYWORDS) {
            if (lowerMessage.contains(kw)) {
                return LLMErrorType.PERMANENT;
            }
        }
        for (String kw : RETRIABLE_KEYWORDS) {
            if (lowerMessage.contains(kw)) {
                return LLMErrorType.RETRIABLE;
            }
        }
        return null;
    }
}
