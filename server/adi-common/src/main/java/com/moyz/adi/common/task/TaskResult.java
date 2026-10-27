package com.moyz.adi.common.task;

/**
 * S2-01：任务执行结果。handler 抛异常等价于 fail(error)。
 * <p>
 * S2-02 补充 defer 语义：任务因<b>资源忙</b>暂不能执行（如同文档索引任务串行排队）时，
 * 用 {@link #defer(long)} 延后重跑——重试计数<b>不前进</b>、永不因此进 DEAD，
 * 对齐旧自建队列「行留在 pending 直到可抢」的行为；普通失败仍走指数退避、耗尽即 DEAD。
 */
public record TaskResult(boolean success, String error, long deferSeconds) {

    /** defer 的下限：避免 busy 场景空转抢库 */
    public static final long MIN_DEFER_SECONDS = 30;

    public TaskResult {
        if (deferSeconds > 0 && deferSeconds < MIN_DEFER_SECONDS) {
            deferSeconds = MIN_DEFER_SECONDS;
        }
    }

    public static TaskResult ok() {
        return new TaskResult(true, null, 0);
    }

    public static TaskResult fail(String error) {
        return new TaskResult(false, error, 0);
    }

    /** 资源忙：deferSeconds 后原样重跑（retry_count 不前进，不进 DEAD） */
    public static TaskResult defer(long seconds, String reason) {
        return new TaskResult(false, reason, seconds);
    }

    public boolean isDeferred() {
        return !success && deferSeconds > 0;
    }
}
