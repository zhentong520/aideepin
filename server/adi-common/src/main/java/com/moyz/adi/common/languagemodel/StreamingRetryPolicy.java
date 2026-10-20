package com.moyz.adi.common.languagemodel;

/**
 * streaming 错误后的重试决策（T6-08/T6-09，纯逻辑，可单测）。
 * <p>
 * ⚠️ 铁律：firstTokenSent == true 时一律 ABORT——已向用户输出部分内容后重试会产生
 * 重复输出（前端会把两段拼接），宁可中止也不能重。首 token 之前才允许重试：
 * 先同模型重试（≤ adi.llm.stream-same-model-retries），再走降级模型链（T6-09）。
 * <p>
 * Streaming retry decision (T6-08/T6-09). Once the first token has been sent to the
 * client, retrying would duplicate partial output — abort instead. Before the first
 * token, retry the same model up to the configured count, then walk the fallback chain.
 */
public final class StreamingRetryPolicy {

    private StreamingRetryPolicy() {
    }

    public enum StreamingDecision {
        /** 同模型重试（首 token 前超时/网络类错误） */
        RETRY_SAME,
        /** 切换到降级链中的下一个备用模型 */
        RETRY_FALLBACK,
        /** 中止：已出首 token / PERMANENT 错误 / 重试与降级链耗尽 */
        ABORT
    }

    /**
     * @param type             错误分类（T6-09）
     * @param firstTokenSent   是否已向用户输出过首 token（含 thinking 片段）
     * @param attemptsUsed     已尝试次数（首次调用失败后 = 1）
     * @param sameModelRetries 允许的同模型重试次数（adi.llm.stream-same-model-retries）
     * @param fallbackCount    降级链备用模型数量
     */
    public static StreamingDecision decide(LLMErrorType type, boolean firstTokenSent,
                                           int attemptsUsed, int sameModelRetries, int fallbackCount) {
        // 铁律：已输出部分内容后只中止不重试（防止重复输出）
        if (firstTokenSent) {
            return StreamingDecision.ABORT;
        }
        // PERMANENT 错误（鉴权/参数/配置）降级也没用，直接报错不浪费额度
        if (type == LLMErrorType.PERMANENT) {
            return StreamingDecision.ABORT;
        }
        int sameModelTries = Math.max(0, sameModelRetries);
        // 尝试次数 <= 同模型重试次数 → 还可以再用同一模型试一次
        if (attemptsUsed <= sameModelTries) {
            return StreamingDecision.RETRY_SAME;
        }
        // 之后依次走降级链
        int fallbackUsed = attemptsUsed - 1 - sameModelTries;
        if (fallbackUsed < fallbackCount) {
            return StreamingDecision.RETRY_FALLBACK;
        }
        return StreamingDecision.ABORT;
    }
}
