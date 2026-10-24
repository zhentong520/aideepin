package com.moyz.adi.common.multiagent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-04 A2A 双向互调（防环）专属单测。
 * <p>
 * 场景语义：Agent A 调用 B，B 可以再调用其它 Agent（如 A 或 C）——互调能力本身不设限，
 * 但<b>调用链上不允许出现重复 Agent</b>，否则死循环。任务书点名的四个用例：
 * <ol>
 *   <li>A→B→C 正常；</li>
 *   <li>A→B→A 被拒并报错（错误信息带链状态）；</li>
 *   <li>自递归（A→A）被拒；</li>
 *   <li>链状态可查询（snapshot / contains / size）。</li>
 * </ol>
 * 另覆盖系统默认深度（maxDepth=1）下的互调：A→B 后 B 想回调 A 时，
 * 防环检查必须先于深度检查命中（报环错误而非深度错误），错误可读。
 */
class A2AMutualCallTest {

    @BeforeAll
    static void installMessageSourceStub() throws Exception {
        MultiAgentTestSupport.installMessageSourceStub();
    }

    @Test
    @DisplayName("T7-04 A→B→C 正常：链上无重复 Agent 时放行")
    void aToBToCIsAllowed() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("agent-a");
        chain.enter("agent-b");
        assertDoesNotThrow(() -> chain.enter("agent-c"));
        assertEquals(List.of("agent-a", "agent-b", "agent-c"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 A→B→A 被拒并报错：双向互调成环即拒，信息带重复 Agent 与完整链")
    void aToBToAIsRejectedWithReadableError() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("agent-a");
        chain.enter("agent-b");
        AgentCallCycleException ex =
                assertThrows(AgentCallCycleException.class, () -> chain.enter("agent-a"));
        assertEquals("A0081", ex.getCode());
        String message = ex.getMessage();
        assertTrue(message.contains("agent-a"), "错误信息必须指出重复的 Agent，实际：" + message);
        assertTrue(message.contains("agent-a -> agent-b"), "错误信息必须带上调用链，实际：" + message);
        // 被拒不动链，B 的调用上下文完整保留
        assertEquals(List.of("agent-a", "agent-b"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 自递归被拒：A 直接调 A 同样按环处理")
    void selfRecursionIsRejected() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("agent-a");
        AgentCallCycleException ex =
                assertThrows(AgentCallCycleException.class, () -> chain.enter("agent-a"));
        assertEquals("A0081", ex.getCode());
        assertTrue(ex.getMessage().contains("agent-a"), "错误信息必须指出自递归的 Agent");
    }

    @Test
    @DisplayName("T7-04 链状态可查询：snapshot / contains / size / getMaxDepth")
    void chainStateIsQueryable() {
        AgentCallChain chain = new AgentCallChain(2);
        assertEquals(2, chain.getMaxDepth());
        assertFalse(chain.contains("agent-a"));
        chain.enter("agent-a");
        assertTrue(chain.contains("agent-a"));
        assertEquals(1, chain.size());
        chain.enter("agent-b");
        assertTrue(chain.contains("agent-b"));
        assertEquals(2, chain.size());
        assertEquals(List.of("agent-a", "agent-b"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 系统默认深度1下互调：A→B 后 B 回跳 A 报环错误（防环先于深度命中）")
    void mutualCallUnderDefaultDepthOneReportsCycleNotDepth() {
        // DelegationService.newChain() 在无 Spring 上下文时默认 maxDepth=1，这是真实默认场景
        AgentCallChain chain = DelegationService.newChain();
        assertEquals(1, chain.getMaxDepth());
        chain.enter("agent-a");
        chain.enter("agent-b");
        // B 想回调 A：链上已有 a，必须先报环（A0081）而不是深度（A0080）
        AgentCallCycleException ex =
                assertThrows(AgentCallCycleException.class, () -> chain.enter("agent-a"));
        assertEquals("A0081", ex.getCode());
        assertEquals(List.of("agent-a", "agent-b"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 互调结束后出链恢复：B 执行完退出，A 可继续与其它 Agent 互调")
    void chainRecoversAfterMutualCallEnds() {
        AgentCallChain chain = new AgentCallChain(1);
        chain.enter("agent-a");
        chain.enter("agent-b");
        chain.exit("agent-b"); // B 的调用结束
        // A 仍可调 C（不构成环），验证防环只针对"当前还在链上"的 Agent
        assertDoesNotThrow(() -> chain.enter("agent-c"));
        assertEquals(List.of("agent-a", "agent-c"), chain.snapshot());
        // A 也退出后，B 又可以作为新链的起点被调用（跨调用链不残留）
        chain.exit("agent-c");
        chain.exit("agent-a");
        assertDoesNotThrow(() -> chain.enter("agent-b"));
        assertEquals(List.of("agent-b"), chain.snapshot());
    }
}
