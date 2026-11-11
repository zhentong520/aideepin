package com.moyz.adi.common.workflow.control;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 业务编排 Harness M2-T2（2026-11-11）：运行控制中枢（内存态控制信号登记处）。
 * <p>
 * 单机部署以内存为准；DB 状态（CANCELLING/PAUSED 相关落库）由
 * {@code WorkflowRuntimeService.cancelFromChat/pauseFromChat} 同步处理。
 * 引擎在<b>每个节点执行前</b>调用 {@link #check(String)}（见 WorkflowEngine.addNodeToStateGraph
 * 的 controlGuard 包装）——命中信号即抛 {@link ControlException}，实现<b>节点边界生效</b>的
 * 协作式中止/暂停：正在执行的节点（LLM 调用/外部 API）跑完当前节点后停，不产生半截节点输出。
 * <p>
 * 信号生命周期：cancel/pause 登记时 put → 引擎终态落库完成后 {@link #clear}；
 * 正常完成路径也必须 clear（防泄漏）。多节点部署 TODO 与 InterruptedFlow 相同。
 */
@Slf4j
public final class WorkflowControlHub {

    public enum Signal {NONE, CANCEL, PAUSE}

    /**
     * 节点边界控制异常：CANCEL → CANCELED 终态；PAUSE → PAUSED（进程内 PausedFlow 注册，可 resume）。
     * 注意：该异常必须抛在 runNode 的 try/catch <b>之外</b>（即节点动作包装层），
     * 否则会被 T9-07 onError 分支接管成普通节点失败。
     */
    public static class ControlException extends RuntimeException {
        private final Signal signal;

        public ControlException(Signal signal, String runtimeUuid) {
            super("workflow control signal: " + signal + ", runtime: " + runtimeUuid);
            this.signal = signal;
        }

        public Signal getSignal() {
            return signal;
        }
    }

    private static final WorkflowControlHub INSTANCE = new WorkflowControlHub();

    public static WorkflowControlHub getInstance() {
        return INSTANCE;
    }

    private WorkflowControlHub() {
    }

    private final ConcurrentHashMap<String, Signal> signals = new ConcurrentHashMap<>();

    /** 登记取消意图（节点边界生效）。cancelFromChat 落 CANCELLING 后调用。 */
    public boolean requestCancel(String runtimeUuid) {
        if (null == runtimeUuid) {
            return false;
        }
        signals.put(runtimeUuid, Signal.CANCEL);
        log.info("WorkflowControlHub: CANCEL registered, runtime:{}", runtimeUuid);
        return true;
    }

    /** 登记暂停意图（节点边界生效）。pauseFromChat 校验 DOING 后调用，DB 状态由引擎边界落 PAUSED。 */
    public boolean requestPause(String runtimeUuid) {
        if (null == runtimeUuid) {
            return false;
        }
        signals.put(runtimeUuid, Signal.PAUSE);
        log.info("WorkflowControlHub: PAUSE registered, runtime:{}", runtimeUuid);
        return true;
    }

    /** 引擎节点边界检查：无信号静默返回；有信号抛 {@link ControlException}。uuid 为空（极端兜底）时静默放行。 */
    public void check(String runtimeUuid) {
        if (null == runtimeUuid) {
            return;
        }
        Signal s = signals.getOrDefault(runtimeUuid, Signal.NONE);
        if (s == Signal.CANCEL || s == Signal.PAUSE) {
            throw new ControlException(s, runtimeUuid);
        }
    }

    /** 运行终态落库（或正常完成）后清理信号，防 uuid 复用与内存泄漏。 */
    public void clear(String runtimeUuid) {
        Signal removed = signals.remove(runtimeUuid);
        if (null != removed) {
            log.info("WorkflowControlHub: signal cleared({}), runtime:{}", removed, runtimeUuid);
        }
    }

    /** 兜底探测（不动信号）：流式消费结束后排查被 langgraph4j 吞掉的信号用。 */
    public Signal peek(String runtimeUuid) {
        return signals.getOrDefault(runtimeUuid, Signal.NONE);
    }

    /** 穿透包装异常链找控制异常（langgraph4j/异步 future 可能多层 wrap）。 */
    public static ControlException findIn(Throwable e) {
        Throwable cur = e;
        while (null != cur) {
            if (cur instanceof ControlException ce) {
                return ce;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
