package com.moyz.adi.common.workflow.node.classifier;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.data.NodeIODataTextContent;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import com.moyz.adi.common.workflow.metrics.LLMMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

import static com.moyz.adi.common.workflow.WfConstant.DEFAULT_OUTPUT_PARAM_NAME;
import static com.moyz.adi.common.enums.ErrorEnum.*;

/**
 * 【节点】LLM问题分类器 <br/>
 * 节点内容固定格式：ClassifierNodeConfig <br/>
 * <br/>
 * 决策链路改为「模型给出判断 + 代码决定路由」：<br/>
 * - 模型只负责在<b>有界候选</b>里选一个并自评置信度（见 {@link ClassifierPrompt}）；<br/>
 * - 解析、匹配、阈值判断全部在 {@link ClassifierDecision} 里完成，不依赖模型输出的格式洁癖；<br/>
 * - 兜底分支必须由编排者显式配置（fallback_target_node_uuid），没配就仍然抛错——
 * 宁可让工作流显式失败，也不要静默走进一条错分支。
 */
@Slf4j
public class ClassifierNode extends AbstractWfNode {

    public ClassifierNode(WorkflowComponent wfComponent, WorkflowNode nodeDef, WfState wfState, WfNodeState nodeState) {
        super(wfComponent, nodeDef, wfState, nodeState);
        state.setMetrics(new LLMMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        ClassifierNodeConfig nodeConfig = checkAndGetConfig(ClassifierNodeConfig.class);
        if (nodeConfig.getCategories().size() < 2) {
            log.warn("Classifier has too few categories, uuid:{}, title:{}", node.getUuid(), node.getTitle());
            throw new BaseException(A_WF_NODE_CONFIG_ERROR);
        }
        //处理类别名与判据中的变量
        for (ClassifierCategory classifierCategory : nodeConfig.getCategories()) {
            if (StringUtils.isNotBlank(classifierCategory.getCategoryName())) {
                classifierCategory.setCategoryName(
                        WorkflowUtil.renderTemplate(classifierCategory.getCategoryName(), state.getInputs()));
            }
            if (StringUtils.isNotBlank(classifierCategory.getCategoryDescription())) {
                classifierCategory.setCategoryDescription(
                        WorkflowUtil.renderTemplate(classifierCategory.getCategoryDescription(), state.getInputs()));
            }
        }
        Optional<NodeIOData> defaultInputOpt = state.getDefaultInput();
        if (defaultInputOpt.isEmpty()) {
            throw new BaseException(A_WF_INPUT_INVALID);
        }
        String prompt = ClassifierPrompt.createPrompt(defaultInputOpt.get().valueToString(), nodeConfig.getCategories());
        NodeIOData nodeIODataText = WorkflowUtil.invokeLLM(wfState, state, nodeConfig.getModelPlatform(), nodeConfig.getModelName(), prompt);

        ClassifierDecision.Decision decision = ClassifierDecision.decide(nodeIODataText.valueToString(), nodeConfig);
        boolean usable = decision.matched() && !decision.lowConfidence();
        if (usable) {
            log.info("Classifier node routed, uuid:{}, category:{}, confidence:{}, matchType:{}",
                    node.getUuid(), decision.category().getCategoryName(), decision.confidence(), decision.matchType());
            return buildResult(decision.category().getCategoryName(), decision.category().getTargetNodeUuid());
        }

        // 走到这里说明：解析失败 / 类别对不上 / 置信度不够
        log.warn("Classifier node degraded, uuid:{}, title:{}, reason:{}, matchType:{}, confidence:{}",
                node.getUuid(), node.getTitle(), decision.reason(), decision.matchType(), decision.confidence());
        String fallback = nodeConfig.getFallbackTargetNodeUuid();
        if (StringUtils.isBlank(fallback)) {
            // 没配兜底就沿用改动前的行为：让调用方看到明确失败，而不是随便挑一条分支走下去
            throw new BaseException(C_LLM_RESPONSE_INVALID);
        }
        return buildResult(StringUtils.EMPTY, fallback);
    }

    private NodeProcessResult buildResult(String value, String nextNodeUuid) {
        NodeIODataTextContent datContent = new NodeIODataTextContent();
        datContent.setValue(value);
        datContent.setTitle("default");
        List<NodeIOData> result = List.of(NodeIOData.builder().name(DEFAULT_OUTPUT_PARAM_NAME).content(datContent).build());
        return NodeProcessResult.builder().nextNodeUuid(nextNodeUuid).content(result).build();
    }
}
