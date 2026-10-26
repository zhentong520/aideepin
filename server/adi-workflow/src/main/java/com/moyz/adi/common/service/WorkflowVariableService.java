package com.moyz.adi.common.service;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.workflow.WfAvailableVarDto;
import com.moyz.adi.common.dto.workflow.WfTemplateValidateResp;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.WfIODataTypeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.PrivilegeUtil;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.def.WfNodeParamRef;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_NODE_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_NOT_FOUND;

/**
 * T3-13：工作流变量服务——字段级变量引用的前端配套。
 * <ul>
 *   <li>{@link #listAvailableVars}：指定节点的可用变量列表（含类型/示例值），
 *       对象/数组类型递归展开 children 至 2 层，控制响应体量；</li>
 *   <li>{@link #validateTemplate}：模板串校验，所有 {@code {{var.path}}} 引用
 *       逐一解析，写错立即提示（缺根变量 / 路径断点）。</li>
 * </ul>
 * 示例值来自当前用户在本工作流上最近一次成功执行的输出（is_test=false）；无历史时
 * 示例值为空，此时仅能校验「根变量是否存在」，字段路径合法性要等有运行数据后才能确认。
 */
@Slf4j
@Service
public class WorkflowVariableService {

    /**
     * 字段级引用递归展开深度（任务书：展开到 2 层深，控制响应体量）。
     */
    private static final int FIELD_EXPAND_DEPTH = 2;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowNodeService workflowNodeService;

    @Resource
    private WorkflowRuntimeService workflowRuntimeService;

    /**
     * 指定节点的可用变量列表。变量 = 节点 inputConfig.refInputs（根变量名），
     * 来源 = 其引用的上游节点输出参数；开始节点则列出其 userInputs 定义。
     */
    public List<WfAvailableVarDto> listAvailableVars(String wfUuid, String nodeUuid) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, workflowService.query(), A_WF_NOT_FOUND);
        List<WorkflowNode> nodes = workflowNodeService.listByWorkflowId(workflow.getId());
        WorkflowNode target = requireNode(nodes, nodeUuid);
        return buildAvailableVars(nodes, target, loadHistoryContext(workflow.getId()));
    }

    /**
     * 模板串校验：解析所有 {@code {{var.path}}} 引用。errors 非空即不合法。
     */
    public WfTemplateValidateResp validateTemplate(String wfUuid, String nodeUuid, String template) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, workflowService.query(), A_WF_NOT_FOUND);
        List<WorkflowNode> nodes = workflowNodeService.listByWorkflowId(workflow.getId());
        WorkflowNode target = requireNode(nodes, nodeUuid);
        return doValidateTemplate(target, loadHistoryContext(workflow.getId()), template);
    }

    private WorkflowNode requireNode(List<WorkflowNode> nodes, String nodeUuid) {
        return nodes.stream()
                .filter(item -> item.getUuid().equals(nodeUuid))
                .findFirst()
                .orElseThrow(() -> new BaseException(A_WF_NODE_NOT_FOUND));
    }

    private Map<String, List<NodeIOData>> loadHistoryContext(Long workflowId) {
        return workflowRuntimeService.loadLastSuccessContext(workflowId, ThreadContext.getCurrentUserId());
    }

    /**
     * 构建可用变量列表（包私有以便单测）。变量 = refInputs（根变量名 = refInput.name），
     * 类型/示例值来自引用参数在最近一次成功执行中的输出。
     */
    List<WfAvailableVarDto> buildAvailableVars(List<WorkflowNode> nodes, WorkflowNode target,
                                               Map<String, List<NodeIOData>> historyContext) {
        List<WfAvailableVarDto> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        //开始节点：列出 userInputs 定义（无 refInputs）
        if (null != target.getInputConfig() && !CollectionUtils.isEmpty(target.getInputConfig().getUserInputs())) {
            target.getInputConfig().getUserInputs().forEach(userInput -> {
                WfAvailableVarDto dto = new WfAvailableVarDto();
                dto.setName(userInput.getName());
                dto.setSourceNodeUuid(target.getUuid());
                dto.setSourceNodeTitle(target.getTitle());
                dto.setSourceParamName(userInput.getName());
                WfIODataTypeEnum typeEnum = WfIODataTypeEnum.getByValue(userInput.getType());
                dto.setType(null != typeEnum ? StringUtils.lowerCase(typeEnum.getDesc()) : "unknown");
                dto.setExample(null);
                result.add(dto);
                seen.add(userInput.getName());
            });
        }
        if (null == target.getInputConfig() || CollectionUtils.isEmpty(target.getInputConfig().getRefInputs())) {
            return result;
        }
        for (WfNodeParamRef ref : target.getInputConfig().getRefInputs()) {
            if (StringUtils.isBlank(ref.getName()) || !seen.add(ref.getName())) {
                continue;
            }
            WorkflowNode source = nodes.stream()
                    .filter(item -> item.getUuid().equals(ref.getNodeUuid()))
                    .findFirst().orElse(null);
            NodeIOData exampleData = null;
            if (null != source) {
                List<NodeIOData> sourceOutputs = historyContext.getOrDefault(source.getUuid(), List.of());
                exampleData = sourceOutputs.stream()
                        .filter(item -> item.getName().equalsIgnoreCase(ref.getNodeParamName()))
                        .findFirst().orElse(null);
            }
            WfAvailableVarDto dto = new WfAvailableVarDto();
            dto.setName(ref.getName());
            dto.setSourceNodeUuid(ref.getNodeUuid());
            dto.setSourceNodeTitle(null != source ? source.getTitle() : null);
            dto.setSourceParamName(ref.getNodeParamName());
            fillTypeAndExample(dto, exampleData, FIELD_EXPAND_DEPTH);
            result.add(dto);
        }
        return result;
    }

    /**
     * 校验模板引用（包私有以便单测）。
     */
    WfTemplateValidateResp doValidateTemplate(WorkflowNode target, Map<String, List<NodeIOData>> historyContext,
                                              String template) {
        List<NodeIOData> valueContext = buildValueContext(target, historyContext);
        List<WfTemplateValidateResp.WfVarError> errors = new ArrayList<>();
        Matcher matcher = WorkflowUtil.FIELD_REF_PATTERN.matcher(StringUtils.defaultString(template));
        while (matcher.find()) {
            String ref = matcher.group(1);
            try {
                WorkflowUtil.resolveFieldRef(ref.split("\\."), valueContext);
            } catch (BaseException e) {
                errors.add(new WfTemplateValidateResp.WfVarError(ref, e.getCode(), e.getInfo()));
            }
        }
        return new WfTemplateValidateResp(errors.isEmpty(), errors);
    }

    /**
     * 校验用的变量上下文：refInput.name -> 带示例值（或空文本占位）的 NodeIOData。
     */
    private List<NodeIOData> buildValueContext(WorkflowNode target, Map<String, List<NodeIOData>> historyContext) {
        List<NodeIOData> result = new ArrayList<>();
        if (null == target.getInputConfig() || CollectionUtils.isEmpty(target.getInputConfig().getRefInputs())) {
            return result;
        }
        for (WfNodeParamRef ref : target.getInputConfig().getRefInputs()) {
            if (StringUtils.isBlank(ref.getName())) {
                continue;
            }
            NodeIOData example = historyContext.values().stream()
                    .flatMap(List::stream)
                    .filter(item -> item.getName().equalsIgnoreCase(ref.getNodeParamName()))
                    .findFirst().orElse(null);
            if (null != example) {
                //带原值克隆 NodeIOData（换根变量名），content 只读复用
                result.add(NodeIOData.builder().name(ref.getName()).content(example.getContent()).build());
            } else {
                //无历史：根变量以空文本占位，保证「根变量不存在」的判断仍然准确
                result.add(NodeIOData.createByText(ref.getName(), ref.getNodeParamName(), ""));
            }
        }
        return result;
    }

    /**
     * 填充类型与示例值；对象/数组示例值递归展开 children（深度受 maxDepth 限制）。
     */
    private void fillTypeAndExample(WfAvailableVarDto dto, NodeIOData data, int maxDepth) {
        if (null == data) {
            dto.setType("unknown");
            dto.setExample(null);
            return;
        }
        WfIODataTypeEnum typeEnum = WfIODataTypeEnum.getByValue(data.getContent().getType());
        dto.setType(null != typeEnum ? StringUtils.lowerCase(typeEnum.getDesc()) : "unknown");
        JsonNode valueNode = toJsonNodeLoose(data.getContent().getValue());
        if (null == valueNode) {
            dto.setExample(String.valueOf(data.getContent().getValue()));
            return;
        }
        //文本值若本身是 JSON（如 HTTP 节点返回的响应体字符串），按结构展开
        if (valueNode.isTextual()) {
            String trimmed = valueNode.asText().trim();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                JsonNode parsed = parseOrNull(trimmed);
                if (null != parsed && (parsed.isObject() || parsed.isArray())) {
                    valueNode = parsed;
                    dto.setType(parsed.isObject() ? "object" : "array");
                }
            }
        }
        if (valueNode.isValueNode()) {
            dto.setExample(valueNode.isTextual() ? valueNode.asText() : valueNode.toString());
        } else if (maxDepth > 0) {
            dto.setExample(null);
            dto.setChildren(expandChildren(valueNode, maxDepth));
        } else {
            dto.setExample(null);
        }
    }

    private List<WfAvailableVarDto> expandChildren(JsonNode node, int remainDepth) {
        List<WfAvailableVarDto> children = new ArrayList<>();
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> iterator = node.fields();
            while (iterator.hasNext()) {
                Map.Entry<String, JsonNode> entry = iterator.next();
                children.add(buildChildDto(entry.getKey(), entry.getValue(), remainDepth - 1));
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                children.add(buildChildDto(String.valueOf(i), node.get(i), remainDepth - 1));
            }
        }
        return children;
    }

    private WfAvailableVarDto buildChildDto(String name, JsonNode node, int remainDepth) {
        WfAvailableVarDto child = new WfAvailableVarDto();
        child.setName(name);
        child.setType(node.isObject() ? "object"
                : node.isArray() ? "array"
                : node.isTextual() ? "text"
                : node.isNumber() ? "number"
                : node.isBoolean() ? "bool"
                : node.isNull() ? "null" : "unknown");
        if (node.isValueNode()) {
            child.setExample(node.isTextual() ? node.asText() : node.toString());
        } else if (remainDepth > 0) {
            child.setChildren(expandChildren(node, remainDepth));
        }
        return child;
    }

    private JsonNode toJsonNodeLoose(Object value) {
        try {
            return JsonUtil.getObjectMapper().readTree(JsonUtil.toJson(value));
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode parseOrNull(String text) {
        try {
            return JsonUtil.getObjectMapper().readTree(text);
        } catch (Exception e) {
            return null;
        }
    }
}
