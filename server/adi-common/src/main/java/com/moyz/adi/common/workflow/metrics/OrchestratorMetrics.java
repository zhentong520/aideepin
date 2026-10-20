package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * T7-03：Orchestrator 编排节点可观测指标。
 * <p>
 * 编排节点的指标 chip 必须自己一类（铁律：新节点指标不能静默缺失）——
 * 前端 RuntimeNodes 的 METRIC_RENDERERS 按 metrics 类型渲染，
 * 分派数/成功数/失败数直接暴露"这次编排健壮性"，summaryLength 反映汇总产出体量。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class OrchestratorMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分派的子任务总数 | Total subtasks dispatched
     */
    private Integer subTaskCount;

    /**
     * 成功回写的子任务数 | Subtasks that wrote a successful result
     */
    private Integer successCount;

    /**
     * 失败回写的子任务数 | Subtasks that wrote a failure record
     */
    private Integer failCount;

    /**
     * 汇总产出长度（字符） | Length of the final summary text (chars)
     */
    private Integer summaryLength;

    /**
     * 子任务累计 token（各子 Agent input+output 之和） | Summed tokens across sub-agents
     */
    private Long totalTokens;
}
