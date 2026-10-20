package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * T9-03：Parallel 并行节点可观测指标。
 * <p>
 * branchCount / successCount / failCount 暴露分支健壮性，elapsedMs 反映并行墙钟收益。
 * <p>
 * T9-03 Parallel node observability metrics.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class ParallelMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分支总数 | Total branches
     */
    private Integer branchCount;

    /**
     * 成功分支数 | Successful branches
     */
    private Integer successCount;

    /**
     * 失败分支数 | Failed branches
     */
    private Integer failCount;

    /**
     * 并行段墙钟耗时（毫秒） | Wall-clock time of the parallel section (ms)
     */
    private Long parallelElapsedMs;
}
