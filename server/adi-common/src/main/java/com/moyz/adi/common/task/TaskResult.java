package com.moyz.adi.common.task;

/**
 * S2-01：任务执行结果。handler 抛异常等价于 fail(error)。
 */
public record TaskResult(boolean success, String error) {

    public static TaskResult ok() {
        return new TaskResult(true, null);
    }

    public static TaskResult fail(String error) {
        return new TaskResult(false, error);
    }
}
