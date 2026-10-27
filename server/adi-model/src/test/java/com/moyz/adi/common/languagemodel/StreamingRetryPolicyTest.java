package com.moyz.adi.common.languagemodel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-08/T6-09 streaming 重试决策单测。
 * ⚠️ 核心：未出首 token 可重试（同模型→降级链）；已出首 token 一律 ABORT（防重复输出）。
 */
class StreamingRetryPolicyTest {

    @Test
    void noFirstTokenRetriableRetriesSameModel() {
        // 首次失败（attemptsUsed=1），同模型重试额度 1 → 同模型重试
        assertEquals(StreamingRetryPolicy.StreamingDecision.RETRY_SAME,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, false, 1, 1, 2));
    }

    @Test
    void noFirstTokenThenWalksFallbackChain() {
        // 同模型重试已用完（attemptsUsed=2 > sameModelRetries=1），降级链还有 2 个 → 走降级
        assertEquals(StreamingRetryPolicy.StreamingDecision.RETRY_FALLBACK,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, false, 2, 1, 2));
        assertEquals(StreamingRetryPolicy.StreamingDecision.RETRY_FALLBACK,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, false, 3, 1, 2));
    }

    @Test
    void fallbackChainExhaustedAborts() {
        // attemptsUsed=4：同模型 1 次 + 降级 2 次全部失败 → ABORT（链耗尽）
        assertEquals(StreamingRetryPolicy.StreamingDecision.ABORT,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, false, 4, 1, 2));
        // 未配置降级链（fallbackCount=0）且同模型重试用完 → ABORT
        assertEquals(StreamingRetryPolicy.StreamingDecision.ABORT,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, false, 2, 1, 0));
    }

    @Test
    void firstTokenSentAlwaysAborts_evenRetriable() {
        // ⚠️ 已输出首 token 后：即使 RETRIABLE 也只中止不重试（重试会产生重复输出）
        assertEquals(StreamingRetryPolicy.StreamingDecision.ABORT,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, true, 1, 1, 2));
        assertEquals(StreamingRetryPolicy.StreamingDecision.ABORT,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, true, 1, 0, 0));
    }

    @Test
    void permanentErrorNeverRetries() {
        // PERMANENT（鉴权/参数/配置错）降级也没用，直接报错不浪费额度
        assertEquals(StreamingRetryPolicy.StreamingDecision.ABORT,
                StreamingRetryPolicy.decide(LLMErrorType.PERMANENT, false, 1, 1, 2));
        assertEquals(StreamingRetryPolicy.StreamingDecision.ABORT,
                StreamingRetryPolicy.decide(LLMErrorType.PERMANENT, true, 1, 1, 2));
    }

    @Test
    void zeroSameModelRetriesGoesStraightToFallback() {
        // sameModelRetries=0：首次失败后直接走降级链
        assertEquals(StreamingRetryPolicy.StreamingDecision.RETRY_FALLBACK,
                StreamingRetryPolicy.decide(LLMErrorType.RETRIABLE, false, 1, 0, 1));
    }
}
