package com.moyz.adi.common.languagemodel;

/**
 * LLM 错误分类（T6-09）：决定是否值得降级重试。
 * <p>
 * RETRIABLE：网络抖动 / 超时 / 429 限流 / 5xx 服务端错误 —— 换备用模型重试有意义；
 * PERMANENT：401/403 鉴权、400 参数、模型不存在、本地配置错误 —— 降级也没用，
 * 直接报错，不浪费备用模型额度。
 * <p>
 * LLM error classification (T6-09): RETRIABLE errors justify walking the fallback
 * chain; PERMANENT errors fail fast to avoid burning fallback-model quota.
 */
public enum LLMErrorType {
    RETRIABLE,
    PERMANENT
}
