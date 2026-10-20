package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * T9-07：SubWorkflow 子工作流节点可观测指标。
 * <p>
 * subWorkflowUuid 记录被引用的子流程，chainDepth / chainPath 记录引用链快照
 * （与 Agent 委派链审计同思想：递归防护必须有迹可查）。
 * <p>
 * T9-07 SubWorkflow node observability metrics.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SubWorkflowMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 被引用的子工作流 uuid | Referenced sub-workflow uuid
     */
    private String subWorkflowUuid;

    /**
     * 引用链深度（含本节点所在根流程） | Reference chain depth
     */
    private Integer chainDepth;

    /**
     * 引用链快照（uuid 以 -> 连接） | Reference chain snapshot (uuids joined by ->)
     */
    private String chainPath;
}
