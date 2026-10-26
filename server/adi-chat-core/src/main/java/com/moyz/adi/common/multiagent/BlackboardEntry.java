package com.moyz.adi.common.multiagent;

import java.util.concurrent.atomic.AtomicReference;

/**
 * T7-03（2026-10-20）：共享黑板（blackboard）上的一个任务条目。
 * <p>
 * 一条子任务在黑板上的完整生命周期：PENDING（已分派未开始）→ RUNNING（执行中）
 * → SUCCESS / FAIL（终态）。失败也是终态之一且必须落黑板——「某子任务失败时
 * 黑板上有失败记录且不阻塞其他子任务」是编排可观测的基本要求：汇总方要能看到
 * 谁失败了、为什么，而不是只看到少了几个结果。
 * </p>
 * 字段全部 volatile：黑板由多个子任务线程并行读写（{@link Blackboard}）。
 */
public final class BlackboardEntry {

    public enum Status {
        PENDING, RUNNING, SUCCESS, FAIL
    }

    private final String taskId;
    private final String taskText;
    private final long createTime;

    private volatile Status status;
    private volatile String result;
    private volatile String errorMessage;
    private volatile long updateTime;

    BlackboardEntry(String taskId, String taskText) {
        this.taskId = taskId;
        this.taskText = taskText;
        this.createTime = System.currentTimeMillis();
        this.status = Status.PENDING;
        this.updateTime = this.createTime;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTaskText() {
        return taskText;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public boolean isDone() {
        return status == Status.SUCCESS || status == Status.FAIL;
    }

    void markRunning() {
        this.status = Status.RUNNING;
        this.updateTime = System.currentTimeMillis();
    }

    void writeResult(String result) {
        this.result = null == result ? "" : result;
        this.status = Status.SUCCESS;
        this.updateTime = System.currentTimeMillis();
    }

    void writeFailure(String errorMessage) {
        this.errorMessage = null == errorMessage ? "" : errorMessage;
        this.status = Status.FAIL;
        this.updateTime = System.currentTimeMillis();
    }

    /**
     * 渲染成一行人类可读摘要（汇总输出用）。
     */
    public String toSummaryLine() {
        return switch (status) {
            case SUCCESS -> "[" + taskId + "] 成功：" + (null == result ? "" : result);
            case FAIL -> "[" + taskId + "] 失败：" + (null == errorMessage ? "" : errorMessage);
            case RUNNING -> "[" + taskId + "] 执行中";
            default -> "[" + taskId + "] 待执行";
        };
    }
}
