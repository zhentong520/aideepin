package com.moyz.adi.common.workflow.node.template;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.workflow.*;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;

@Slf4j
public class TemplateNode extends AbstractWfNode {

    public TemplateNode(WorkflowComponent wfComponent, WorkflowNode node, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
    }

    @Override
    protected NodeProcessResult onProcess() {
        TemplateNodeConfig nodeConfig = checkAndGetConfig(TemplateNodeConfig.class);
        log.info("Template node config:{}", nodeConfig);
        WfNodeIODataUtil.changeFilesContentToMarkdown(state.getInputs());
        //T3-13：字段级变量引用（{{var.path}}），带开关 adi.wf.field-ref-enabled，关闭时回退宽松行为
        String content = WorkflowUtil.renderTemplateStrict(nodeConfig.getTemplate(), state.getInputs());
        NodeIOData output = NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "", content);
        return NodeProcessResult.builder().content(List.of(output)).build();
    }
}
