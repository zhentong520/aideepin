package com.moyz.adi.common.dto.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * T3-10：单节点试跑请求。startNodeUuid 指定从哪个节点起执行（跳过上游），
 * inputs 为可选的用户输入（仅当起始节点是 START 节点时校验必填项）。
 */
@Data
public class WorkflowTestRunReq {
    @NotBlank
    private String startNodeUuid;
    private List<ObjectNode> inputs;
}
