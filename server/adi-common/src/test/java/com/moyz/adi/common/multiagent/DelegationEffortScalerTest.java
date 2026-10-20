package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.languagemodel.data.ToolLoopBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-02 effort-scaling（{@link DelegationEffortScaler}）单测：难度 → 投入档位 → 三重预算。
 * <p>
 * 关键约束：MEDIUM 档必须与系统默认 {@link ToolLoopBudget} 一致（无 Spring 上下文的单测里
 * {@code fromConfig()} 回退默认 5/300s/200000），保证「常规子任务的行为不回退」；
 * LOW/HIGH 按难度收紧/放宽，避免易任务浪费额度、难任务被紧预算掐断。
 */
class DelegationEffortScalerTest {

    @Test
    @DisplayName("短平快 objective 判 LOW 档")
    void shortObjectiveIsLow() {
        assertEquals(DelegationEffortScaler.LOW, DelegationEffortScaler.tierOf("查一下明天天气"));
    }

    @Test
    @DisplayName("中等复杂度 objective 判 MEDIUM 档")
    void mediumObjectiveIsMedium() {
        // 长度 ~0 分 + 句子 5×4=20 + 列举 3×5=15 + 问号 1×3=3 → 38 分，落在 20~50
        String objective = "请完成以下三步：\n1. 读取数据\n2. 分析对比\n3. 输出结论。有疑问吗？";
        assertEquals(DelegationEffortScaler.MEDIUM, DelegationEffortScaler.tierOf(objective));
    }

    @Test
    @DisplayName("多步多子项长 objective 判 HIGH 档")
    void complexObjectiveIsHigh() {
        String objective = "请按顺序完成以下六项工作：\n"
                + "1. 拉取上周全部销售数据并按区域汇总\n"
                + "2. 找出环比下降最大的三个区域，分析可能原因\n"
                + "3. 结合库存与人力成本评估每个区域的整改优先级\n"
                + "4. 输出一份给管理层的决策建议，包含风险与依赖。是否需要补充外部数据？要不要同步财务？";
        assertEquals(DelegationEffortScaler.HIGH, DelegationEffortScaler.tierOf(objective));
    }

    @Test
    @DisplayName("null 契约兜底 MEDIUM（与系统默认一致，不放软也不加码）")
    void nullContractDefaultsToMedium() {
        assertEquals(DelegationEffortScaler.MEDIUM, DelegationEffortScaler.tierOf((DelegationContract) null));
    }

    @Test
    @DisplayName("三档预算按难度缩放：LOW 收紧 / MEDIUM 默认 / HIGH 放宽")
    void budgetsScalePerTier() {
        ToolLoopBudget low = DelegationEffortScaler.budgetForTier(DelegationEffortScaler.LOW);
        ToolLoopBudget medium = DelegationEffortScaler.budgetForTier(DelegationEffortScaler.MEDIUM);
        ToolLoopBudget high = DelegationEffortScaler.budgetForTier(DelegationEffortScaler.HIGH);

        assertEquals(3, low.getMaxDepth());
        assertEquals(120_000L, low.getMaxWallMs());
        assertEquals(50_000L, low.getMaxTotalTokens());

        assertEquals(8, high.getMaxDepth());
        assertEquals(600_000L, high.getMaxWallMs());
        assertEquals(500_000L, high.getMaxTotalTokens());

        assertTrue(low.getMaxTotalTokens() < medium.getMaxTotalTokens()
                        && medium.getMaxTotalTokens() < high.getMaxTotalTokens(),
                "token 预算必须随档位单调放宽");
        assertTrue(low.getMaxDepth() < medium.getMaxDepth() && medium.getMaxDepth() < high.getMaxDepth(),
                "深度预算必须随档位单调放宽");
    }

    @Test
    @DisplayName("MEDIUM 档与系统默认 ToolLoopBudget 一致（默认行为不回退）")
    void mediumTierMatchesSystemDefault() {
        ToolLoopBudget medium = DelegationEffortScaler.budgetForTier(DelegationEffortScaler.MEDIUM);
        ToolLoopBudget systemDefault = ToolLoopBudget.fromConfig();
        assertEquals(systemDefault.getMaxDepth(), medium.getMaxDepth());
        assertEquals(systemDefault.getMaxWallMs(), medium.getMaxWallMs());
        assertEquals(systemDefault.getMaxTotalTokens(), medium.getMaxTotalTokens());
    }

    @Test
    @DisplayName("未知档位名回退 MEDIUM（不猜、不放飞）")
    void unknownTierFallsBackToMedium() {
        ToolLoopBudget budget = DelegationEffortScaler.budgetForTier("NOT_A_TIER");
        assertEquals(5, budget.getMaxDepth());
        assertEquals(300_000L, budget.getMaxWallMs());
        assertEquals(200_000L, budget.getMaxTotalTokens());
    }

    @Test
    @DisplayName("端到端：契约 → 档位 → 预算")
    void contractToBudgetEndToEnd() {
        DelegationContract simple = DelegationContract.builder().objective("查一下汇率").build();
        assertEquals(3, DelegationEffortScaler.budgetFor(simple).getMaxDepth());

        DelegationContract complex = DelegationContract.builder()
                .objective("请按顺序完成以下六项工作：\n1. 拉取上周全部销售数据并按区域汇总\n"
                        + "2. 找出环比下降最大的三个区域，分析可能原因\n"
                        + "3. 结合库存与人力成本评估每个区域的整改优先级\n"
                        + "4. 输出一份给管理层的决策建议，包含风险与依赖。是否需要补充外部数据？要不要同步财务？")
                .build();
        assertEquals(8, DelegationEffortScaler.budgetFor(complex).getMaxDepth());
    }
}
