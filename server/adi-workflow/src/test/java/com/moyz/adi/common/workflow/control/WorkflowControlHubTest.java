package com.moyz.adi.common.workflow.control;

import com.moyz.adi.common.workflow.control.WorkflowControlHub.ControlException;
import com.moyz.adi.common.workflow.control.WorkflowControlHub.Signal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 业务编排 Harness M2-T2：控制中枢单元测试（纯内存，无 Spring/DB 依赖）。
 * 覆盖：信号登记 → 边界检查抛 ControlException → clear 防泄漏 → findIn 穿透包装链
 * → null uuid 兜底放行。
 */
class WorkflowControlHubTest {

    @Test
    void checkWithoutSignalPassesSilently() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        hub.clear("rt-no-signal");
        hub.check("rt-no-signal");
        assertSame(Signal.NONE, hub.peek("rt-no-signal"));
    }

    @Test
    void requestCancelThenCheckThrowsWithCancelSignal() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        assertTrue(hub.requestCancel("rt-cancel-1"));
        ControlException ex = assertThrows(ControlException.class, () -> hub.check("rt-cancel-1"));
        assertSame(Signal.CANCEL, ex.getSignal());
        assertTrue(hub.peek("rt-cancel-1") == Signal.CANCEL);
        hub.clear("rt-cancel-1");
        assertSame(Signal.NONE, hub.peek("rt-cancel-1"));
    }

    @Test
    void requestPauseThenCheckThrowsWithPauseSignal() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        assertTrue(hub.requestPause("rt-pause-1"));
        ControlException ex = assertThrows(ControlException.class, () -> hub.check("rt-pause-1"));
        assertSame(Signal.PAUSE, ex.getSignal());
        hub.clear("rt-pause-1");
        assertSame(Signal.NONE, hub.peek("rt-pause-1"));
    }

    @Test
    void clearRemovesSignalSoCheckPassesAgain() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        hub.requestCancel("rt-clear-1");
        assertThrows(ControlException.class, () -> hub.check("rt-clear-1"));
        hub.clear("rt-clear-1");
        hub.check("rt-clear-1");
    }

    @Test
    void clearOnUnknownUuidIsNoop() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        hub.clear("rt-unknown-clear");
        assertSame(Signal.NONE, hub.peek("rt-unknown-clear"));
    }

    @Test
    void findInTraversesWrappedCauseChain() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        hub.requestPause("rt-find-1");
        ControlException origin = new ControlException(Signal.PAUSE, "rt-find-1");
        //langgraph4j/异步 future 常见多层 wrap：RuntimeException → IllegalStateException → ControlException
        RuntimeException wrapped = new RuntimeException("outer", new IllegalStateException("mid", origin));
        ControlException found = WorkflowControlHub.findIn(wrapped);
        assertSame(Signal.PAUSE, found.getSignal());
        hub.clear("rt-find-1");
    }

    @Test
    void findInReturnsNullForPlainException() {
        assertNull(WorkflowControlHub.findIn(new RuntimeException("plain failure")));
        assertNull(WorkflowControlHub.findIn(null));
    }

    @Test
    void nullUuidRequestsAreRejectedAndCheckPassesThrough() {
        WorkflowControlHub hub = WorkflowControlHub.getInstance();
        assertFalse(hub.requestCancel(null));
        assertFalse(hub.requestPause(null));
        //null uuid 兜底放行（ConcurrentHashMap 不允许 null key，不能 NPE）
        hub.check(null);
    }
}
