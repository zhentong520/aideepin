package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * T9-02：Loop/Each 循环节点可观测指标。
 * <p>
 * iterations 反映实际执行轮数（与 max_iterations 对照即可看出是否触达熔断），
 * itemCount / resultCount 暴露数组规模与聚合产出规模，stopReason 记录结束原因。
 * <p>
 * T9-02 Loop/Each node observability metrics.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class LoopMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 实际执行轮数 | Actual iterations executed
     */
    private Integer iterations;

    /**
     * 数组输入项数（each 模式） | Array input size (each mode)
     */
    private Integer itemCount;

    /**
     * 聚合结果条数 | Aggregated result count
     */
    private Integer resultCount;

    /**
     * 结束原因：completed / empty_input / max_iterations | Stop reason
     */
    private String stopReason;
}
