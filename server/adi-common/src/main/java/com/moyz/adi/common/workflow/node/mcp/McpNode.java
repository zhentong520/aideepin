package com.moyz.adi.common.workflow.node.mcp;

import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.UserMcpService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.McpMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.cosntant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * 工作流 MCP 工具节点：调用当前用户 MCP 服务暴露的某个工具一次，把结果传给下游。
 * <p>
 * Workflow MCP node — invokes a single tool exposed by one of the current user's MCP servers
 * and passes the result downstream.
 * </p>
 * <p>
 * 与 Agent 节点的区别：Agent 节点把 MCP 工具交给 LLM 自行决定是否调用（黑盒），
 * 本节点是「确定性地调这一个工具」，适合把外部能力编排进固定流程。
 * Unlike the Agent node (which hands MCP tools to an LLM and lets it decide), this node
 * deterministically calls exactly one tool.
 * </p>
 */
@Slf4j
public class McpNode extends AbstractWfNode {

    public McpNode(WorkflowComponent wfComponent, WorkflowNode node,
                   WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new McpMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        McpNodeConfig config = checkAndGetConfig(McpNodeConfig.class);
        if (StringUtils.isBlank(config.getToolName())) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "mcp node tool_name is empty");
        }
        //Workflow nodes run on @Async threads: pass the user explicitly instead of relying on ThreadContext
        User user = getWfState().getUser();
        if (null == user) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "mcp node cannot resolve the workflow user");
        }

        //参数模板：{name} 占位符引用上游输入，替换值做 JSON 转义，渲染结果必须是合法 JSON
        String arguments = WorkflowUtil.renderJsonTemplate(config.getArguments(), state.getInputs());
        log.info("MCP node invoking tool:{}, mcpIds:{}, arguments:{}", config.getToolName(), config.getMcpIds(), arguments);

        UserMcpService userMcpService = SpringUtil.getBean(UserMcpService.class);
        UserMcpService.McpToolCallResult callResult = userMcpService.callTool(
                user.getId(), config.getMcpIds(), config.getToolName(), arguments);

        String content = "json".equalsIgnoreCase(config.getOutputFormat())
                ? toJsonOrWrapped(callResult.resultText())
                : callResult.resultText();

        McpMetrics metrics = (McpMetrics) state.getMetrics();
        metrics.setMcpTitle(callResult.mcpTitle());
        metrics.setToolName(config.getToolName());
        metrics.setResultLength(content.length());

        List<NodeIOData> outputData = new ArrayList<>();
        outputData.add(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "MCP 工具返回", content));
        outputData.add(NodeIOData.createByText("tool_name", "工具名", config.getToolName()));
        outputData.add(NodeIOData.createByText("mcp_title", "MCP 服务", StringUtils.defaultString(callResult.mcpTitle())));
        return NodeProcessResult.builder().content(outputData).build();
    }

    /**
     * 输出 JSON 时：MCP 工具一般本身就返回 JSON 文本，能解析就直接透传；
     * 否则包成 {"result": "..."}，保证下游拿到的一定是合法 JSON。
     */
    private String toJsonOrWrapped(String resultText) {
        if (StringUtils.isBlank(resultText)) {
            return "{}";
        }
        try {
            JsonUtil.getObjectMapper().readTree(resultText);
            return resultText;
        } catch (Exception e) {
            return JsonUtil.toJson(Map.of("result", resultText));
        }
    }
}
