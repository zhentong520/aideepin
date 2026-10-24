package com.moyz.adi.common.workflow.node.subworkflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.enums.WfIODataTypeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfFeatureGate;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.SubWorkflowMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.constant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * T9-07：子工作流节点（SubWorkflow）。
 * <p>
 * 节点引用另一个工作流作为子流程执行：复用 {@code WorkflowStarter.blocking}
 * （子流程有自己的运行记录/可观测性，与手工运行完全同链路，不旁路实现）。
 * 防递归：{@link SubWorkflowInvoker} 引用链去重 + 深度上限 3。
 * 归属校验：子工作流必须启用，且为公开或归属当前运行用户（与 Skill 节点同口径，
 * 防止公开工作流成为执行他人私有流程的通道）。
 * 输入映射：当前节点全部输入转为子工作流的用户输入（{@code name/content.type/content.value}）。
 * 输出：子工作流最终 outputs 透传为本节点输出（子流程 END 节点的 output → 本节点 output）。
 * 开关（铁律 10）：sys_config {@code wf_sub_workflow_enabled} / adi.wf.sub-workflow-enabled。
 */
@Slf4j
public class SubWorkflowNode extends AbstractWfNode {

    public SubWorkflowNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new SubWorkflowMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        if (!WfFeatureGate.subWorkflowEnabled()) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "sub-workflow node is disabled by switch (wf_sub_workflow_enabled)");
        }
        SubWorkflowNodeConfig config = checkAndGetConfig(SubWorkflowNodeConfig.class);
        if (StringUtils.isBlank(config.getWorkflowUuid())) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "sub-workflow node requires workflow_uuid");
        }
        User user = getWfState().getUser();

        SubWorkflowMetrics metrics = (SubWorkflowMetrics) state.getMetrics();
        metrics.setSubWorkflowUuid(config.getWorkflowUuid());
        metrics.setChainDepth(SubWorkflowInvoker.depth() + 1);
        metrics.setChainPath(SubWorkflowInvoker.chainText());

        //防递归：入链（重复/超深即抛 A_WF_SUB_WORKFLOW_RECURSIVE / DEPTH_EXCEEDED），finally 出链
        SubWorkflowInvoker.checkAndEnter(config.getWorkflowUuid());
        try {
            WorkflowStarterBridge starter = new WorkflowStarterBridge();
            Map<String, Object> result = starter.blocking(user, config.getWorkflowUuid(), toUserInputs(state.getInputs()));

            //子流程失败时 blocking 返回 {message: ...} 而非抛异常——显式转成节点失败，防静默
            if (null != result && result.containsKey("message") && !result.containsKey("outputs")) {
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "sub-workflow failed: " + result.get("message"));
            }
            Object outputs = null == result ? null : result.get("outputs");
            String outputText = null == outputs ? "" : JsonUtil.toJson(outputs);
            List<NodeIOData> outputData = new ArrayList<>();
            outputData.add(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "子工作流输出", outputText));
            outputData.add(NodeIOData.createByText("sub_workflow_uuid", "子工作流", config.getWorkflowUuid()));
            log.info("sub-workflow node done, subUuid:{}, output length:{}", config.getWorkflowUuid(), outputText.length());
            return NodeProcessResult.builder().content(outputData).build();
        } finally {
            SubWorkflowInvoker.exit();
        }
    }

    /**
     * 当前节点输入 → 子工作流用户输入（ObjectNode：name + content.type/title/value）。
     * 文本类直接透传；非文本类型统一降级为文本（子流程 START 定义以文本为主，宽松兼容）。
     */
    static List<ObjectNode> toUserInputs(List<NodeIOData> inputs) {
        List<ObjectNode> result = new ArrayList<>();
        if (null == inputs) {
            return result;
        }
        for (NodeIOData input : inputs) {
            ObjectNode wrapper = JsonUtil.createObjectNode();
            wrapper.put("name", input.getName());
            ObjectNode content = wrapper.putObject("content");
            content.put("type", WfIODataTypeEnum.TEXT.getValue());
            content.put("title", input.getName());
            content.put("value", input.valueToString());
            result.add(wrapper);
        }
        return result;
    }

    /**
     * 对 {@code WorkflowStarter.blocking} 的轻量桥接（包内可覆盖，单测注入桩实现），
     * 避免节点直接持有 Spring bean。
     */
    static class WorkflowStarterBridge {
        Map<String, Object> blocking(User user, String workflowUuid, List<ObjectNode> userInputs) {
            return com.moyz.adi.common.util.SpringUtil.getBean(com.moyz.adi.common.workflow.WorkflowStarter.class)
                    .blocking(user, workflowUuid, userInputs, "sub_workflow");
        }
    }
}
