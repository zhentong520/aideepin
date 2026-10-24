package com.moyz.adi.common.languagemodel;

import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-09 错误分类单测：RETRIABLE（网络/超时/429/5xx）vs PERMANENT（401/403/400/配置错）。
 */
class LLMErrorClassifierTest {

    @Test
    void baseExceptionIsPermanent() {
        // 本地业务/配置错误——降级也没用
        assertEquals(LLMErrorType.PERMANENT,
                LLMErrorClassifier.classify(new BaseException("B_XXX", "config missing")));
    }

    @Test
    void authErrorsArePermanent() {
        assertEquals(LLMErrorType.PERMANENT, LLMErrorClassifier.classify(new RuntimeException("401 Unauthorized")));
        assertEquals(LLMErrorType.PERMANENT, LLMErrorClassifier.classify(new RuntimeException("HTTP 403 Forbidden")));
        assertEquals(LLMErrorType.PERMANENT, LLMErrorClassifier.classify(new RuntimeException("400 Bad Request: invalid body")));
        assertEquals(LLMErrorType.PERMANENT, LLMErrorClassifier.classify(new RuntimeException("Invalid API key provided")));
        assertEquals(LLMErrorType.PERMANENT, LLMErrorClassifier.classify(new RuntimeException("model not found: gpt-99")));
    }

    @Test
    void rateLimitAndServerErrorsAreRetriable() {
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("429 Too Many Requests")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("500 Internal Server Error")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("502 Bad Gateway")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("503 Service Unavailable")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("504 Gateway Timeout")));
    }

    @Test
    void networkErrorsAreRetriable() {
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("connection refused")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("Read timed out")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new java.net.SocketTimeoutException("connect timed out")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("Connection reset by peer")));
    }

    @Test
    void unknownErrorDefaultsRetriable() {
        // 未知错误保守按可重试处理（降级链有次数上限，不会无限烧额度）
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(new RuntimeException("weird failure xyz")));
        assertEquals(LLMErrorType.RETRIABLE, LLMErrorClassifier.classify(null));
    }

    @Test
    void permanentKeywordBeatsAmbiguity() {
        // 同时含状态码与关键词时，鉴权类关键词必须判 PERMANENT（不浪费额度）
        assertEquals(LLMErrorType.PERMANENT,
                LLMErrorClassifier.classify(new RuntimeException("401 Unauthorized: api key not valid")));
    }
}
