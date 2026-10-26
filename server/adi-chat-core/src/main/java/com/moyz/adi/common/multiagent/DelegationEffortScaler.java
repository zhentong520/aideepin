package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.languagemodel.data.ToolLoopBudget;
import lombok.Getter;

/**
 * T7-02（2026-10-20）：effort-scaling —— 按任务难度调节委派投入。
 * <p>
 * 难度信号来自委派契约的 objective（长度 + 句数 + 列举行数 + 问号数，见
 * {@link DelegationContract.DifficultySignal#complexityScore()}），分三档：
 * <ul>
 *   <li>{@link #LOW}（简单，score &lt; 20）：短平快子任务，收紧 token 预算与工具循环深度；</li>
 *   <li>{@link #MEDIUM}（中等，20 ≤ score &lt; 50）：常规子任务，默认档；</li>
 *   <li>{@link #HIGH}（复杂，score ≥ 50）：多步/多子项任务，放宽 token 预算。</li>
 * </ul>
 * 每档给出「工具循环预算」——复用 T6-10 {@link ToolLoopBudget} 的三重预算思路
 * （深度 + 墙钟 + token），按难度缩放各档位，避免难任务被紧预算掐断、
 * 易任务浪费额度。模型选择由调用方按档位映射（如 HIGH 档切更强模型），
 * 本类只负责难度→预算的纯函数映射，不依赖 Spring 上下文，可单测。
 * <p>
 * Effort scaling: the delegation contract's objective drives a LOW/MEDIUM/HIGH
 * effort tier, which scales the ToolLoopBudget-style triple budget
 * (depth + wall-clock + tokens).
 */
public final class DelegationEffortScaler {

    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";

    /** 复杂度分档阈值：低于 LOW_THRESHOLD 为 LOW，低于 HIGH_THRESHOLD 为 MEDIUM，其余 HIGH。 */
    public static final int LOW_THRESHOLD = 20;
    public static final int HIGH_THRESHOLD = 50;

    private DelegationEffortScaler() {
    }

    /**
     * 由契约推断投入档位（纯函数）。
     */
    public static String tierOf(DelegationContract contract) {
        if (null == contract) {
            return MEDIUM;
        }
        return tierOf(contract.getObjective());
    }

    public static String tierOf(String objective) {
        int score = new DelegationContract.DifficultySignal(objective).complexityScore();
        if (score < LOW_THRESHOLD) {
            return LOW;
        }
        if (score < HIGH_THRESHOLD) {
            return MEDIUM;
        }
        return HIGH;
    }

    /**
     * 按档位生成工具循环预算（T6-10 三重预算的思路按难度缩放）：
     * <table>
     *   <tr><td>LOW</td><td>depth 3 / 120s / 50k tokens</td></tr>
     *   <tr><td>MEDIUM</td><td>depth 5 / 300s / 200k tokens</td></tr>
     *   <tr><td>HIGH</td><td>depth 8 / 600s / 500k tokens</td></tr>
     * </table>
     * MEDIUM 档与系统默认 ToolLoopBudget 一致（保证默认行为不回退）。
     */
    public static ToolLoopBudget budgetForTier(String tier) {
        return switch (tier) {
            case LOW -> new ToolLoopBudget(3, 120_000L, 50_000L);
            case HIGH -> new ToolLoopBudget(8, 600_000L, 500_000L);
            default -> new ToolLoopBudget(5, 300_000L, 200_000L);
        };
    }

    /** 便捷方法：契约 → 预算。 */
    public static ToolLoopBudget budgetFor(DelegationContract contract) {
        return budgetForTier(tierOf(contract));
    }
}
