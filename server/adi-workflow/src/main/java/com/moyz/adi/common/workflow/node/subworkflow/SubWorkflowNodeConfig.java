package com.moyz.adi.common.workflow.node.subworkflow;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * T9-07：子工作流节点配置（nodeConfig 即 DSL）。
 * <pre>{@code
 * {
 *   "workflow_uuid": "uuid-of-sub-flow"   // 被引用的子工作流（必须启用且公开/归属运行用户）
 * }
 * }</pre>
 * 防递归（引用链去重 + 深度上限 3）见 {@link SubWorkflowInvoker}。
 */
@Data
public class SubWorkflowNodeConfig {

    /** 被引用的子工作流 uuid */
    @JsonProperty("workflow_uuid")
    @NotBlank
    private String workflowUuid;
}
