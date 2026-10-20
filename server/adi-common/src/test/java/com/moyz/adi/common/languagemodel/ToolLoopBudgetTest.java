package com.moyz.adi.common.languagemodel;

import com.moyz.adi.common.languagemodel.data.ToolLoopBudget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-10 三重预算单测。
 * ⚠️ 三个预算各自独立覆盖（只测一个等于没测）+ 组合超限中止原因正确。
 */
class ToolLoopBudgetTest {

    @Test
    void depthBudgetAlone() {
        ToolLoopBudget budget = new ToolLoopBudget(3, 60_000, 1_000_000);
        assertTrue(budget.consumeDepth());
        assertTrue(budget.consumeDepth());
        assertTrue(budget.consumeDepth());
        // 第 4 轮被拒：深度超限，原因 = DEPTH
        assertFalse(budget.consumeDepth());
        assertEquals(ToolLoopBudget.REASON_DEPTH, budget.checkExceeded().orElse(null));
        assertEquals(3, budget.getDepthUsed());
    }

    @Test
    void wallClockBudgetAlone() throws InterruptedException {
        ToolLoopBudget budget = new ToolLoopBudget(100, 0, 1_000_000);
        assertTrue(budget.consumeDepth());
        Thread.sleep(5);
        // 墙钟超限，原因 = WALL_CLOCK（深度未耗尽）
        assertEquals(ToolLoopBudget.REASON_WALL_CLOCK, budget.checkExceeded().orElse(null));
    }

    @Test
    void tokenBudgetAlone() {
        ToolLoopBudget budget = new ToolLoopBudget(100, 60_000, 100);
        assertTrue(budget.consumeDepth());
        budget.accumulateTokens(60);
        assertTrue(budget.checkExceeded().isEmpty(), "未超限应为空");
        budget.accumulateTokens(50);
        // token 累计超限，原因 = TOKENS
        assertEquals(ToolLoopBudget.REASON_TOKENS, budget.checkExceeded().orElse(null));
        assertEquals(110, budget.getTokensUsed());
    }

    @Test
    void combinedExceededRecordsCorrectReason() {
        // 组合场景：深度额度还剩，但 token 已爆 → 中止原因必须是 TOKENS（不能误报 DEPTH）
        ToolLoopBudget budget = new ToolLoopBudget(10, 60_000, 100);
        assertTrue(budget.consumeDepth());
        assertTrue(budget.consumeDepth());
        budget.accumulateTokens(500);
        assertEquals(ToolLoopBudget.REASON_TOKENS, budget.checkExceeded().orElse(null));
    }

    @Test
    void abortReasonIsStableOnceTripped() {
        ToolLoopBudget budget = new ToolLoopBudget(1, 60_000, 1000);
        budget.accumulateTokens(99); // 未超
        assertTrue(budget.consumeDepth());  // 第 1 轮 ok（深度用尽额度）
        assertTrue(budget.checkExceeded().isEmpty());
        assertFalse(budget.consumeDepth()); // 第 2 轮被拒 → DEPTH
        assertEquals(ToolLoopBudget.REASON_DEPTH, budget.checkExceeded().orElse(null));
        // 之后即使 token 也超限，中止原因也不漂移（首次超限为准）
        budget.accumulateTokens(5000);
        assertEquals(ToolLoopBudget.REASON_DEPTH, budget.checkExceeded().orElse(null));
    }

    @Test
    void negativeTokensIgnored() {
        ToolLoopBudget budget = new ToolLoopBudget(5, 60_000, 100);
        budget.accumulateTokens(-5);
        assertEquals(0, budget.getTokensUsed());
        assertTrue(budget.checkExceeded().isEmpty());
    }

    @Test
    void fromConfigFallsBackWithoutSpring() {
        // 无 Spring 上下文：默认 5 / 300s / 200000（深度与历史硬编码 MAX_TOOL_CALL_DEPTH=5 一致）
        ToolLoopBudget budget = ToolLoopBudget.fromConfig();
        assertEquals(5, budget.getMaxDepth());
        assertEquals(300_000L, budget.getMaxWallMs());
        assertEquals(200_000L, budget.getMaxTotalTokens());
    }
}
