package com.moyz.adi.common.workflow.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.enums.WfIODataTypeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.UserService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.WfNodeIODataUtil;
import com.moyz.adi.common.workflow.def.WfNodeIO;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 业务编排 Harness M1 触发工具（workflow-as-tool，2026-10-30）。
 * <p>
 * 每个场景绑定的工作流生成一个对话工具（name={@code wf_<uuid前8位>_<语义名>}），
 * LLM 调用即启动工作流：同步建 runtime（关联会话/提问/场景）→ 异步执行 →
 * 立即返回 runtime_uuid。与 {@code DelegateAgentTool} 同构：错误按约定返回
 * {@code ERROR: <可读原因>}（LLM 可读并转述/重试）。
 * <p>
 * Chat-side launch tool for scene-bound workflows. One tool per workflow; invoking it
 * launches the workflow asynchronously and returns the runtime uuid immediately.
 */
@Slf4j
@Component
public class LaunchWorkflowTool {

    public static final String TOOL_PREFIX = "wf_";

    @Resource
    private WorkflowOrchestrationService orchestrationService;

    @Resource
    private UserService userService;

    /**
     * 构造单个工作流的工具规格。description 直接放用途+入参说明——
     * 路由准确率的唯一杠杆（方案 §5.1：工具描述质量决定 LLM 选没选对）。
     */
    public ToolSpecification buildSpecification(Workflow workflow, String inputDesc) {
        String name = toolName(workflow);
        StringBuilder desc = new StringBuilder();
        desc.append("启动业务工作流「").append(StringUtils.defaultIfBlank(workflow.getTitle(), workflow.getUuid())).append("」。");
        if (StringUtils.isNotBlank(workflow.getRemark())) {
            desc.append("用途：").append(workflow.getRemark().trim()).append(" ");
        }
        desc.append("异步执行：调用后立即返回 runtime_uuid，不要等待执行结果。");
        if (StringUtils.isNotBlank(inputDesc)) {
            desc.append("入参：").append(inputDesc).append(" 缺失的必填参数必须先向用户追问，禁止编造。");
        }
        return ToolSpecification.builder()
                .name(name)
                .description(desc.toString())
                .parameters(JsonObjectSchema.builder()
                        .addProperty("input", JsonObjectSchema.builder()
                                .description("工作流入参（键=参数名，值=参数值）")
                                .build())
                        .required("input")
                        .build())
                .build();
    }

    /**
     * 工具名规则（buildSpecification 与 execute 反查必须一致）：
     * wf_ &lt;uuid前8位&gt; _ &lt;slug(title)&gt;
     */
    public String toolName(Workflow workflow) {
        return TOOL_PREFIX + workflow.getUuid().substring(0, Math.min(8, workflow.getUuid().length()))
                + "_" + slugify(workflow.getTitle());
    }

    /**
     * 执行启动。argsJson 形如 {"input": {"参数名": "值", ...}}。
     * 输入按 START 节点定义的类型转换为引擎输入结构（{name, content:{type,title,value}}），
     * 再调 WorkflowStarter.launchFromChat（同步建 runtime → 异步执行 → 立即返回 runtime_uuid）。
     */
    public String invoke(String argsJson, String workflowUuid, Long userId,
                         String characterUuid, String sceneUuid, String questionUuid) {
        if (null == userId) {
            return "ERROR: no user context, cannot launch workflow";
        }
        JsonNode parsed;
        try {
            parsed = JsonUtil.toJsonNode(StringUtils.defaultIfBlank(argsJson, "{}"));
        } catch (Exception e) {
            log.warn("launch_workflow arguments parse failed:{}", StringUtils.abbreviate(argsJson, 200));
            return "ERROR: invalid arguments (must be a JSON object)";
        }
        if (!(parsed instanceof ObjectNode args)) {
            return "ERROR: invalid arguments (must be a JSON object)";
        }
        JsonNode inputNode = args.path("input");
        if (!inputNode.isObject()) {
            return "ERROR: input is required (a JSON object of workflow parameters)";
        }
        try {
            List<ObjectNode> userInputs = convertInputs(workflowUuid, (ObjectNode) inputNode);
            User user = userService.getById(userId);
            if (null == user) {
                return "ERROR: user not found, id=" + userId;
            }
            String runtimeUuid = orchestrationService.launch(user, workflowUuid, userInputs,
                    characterUuid, questionUuid, sceneUuid);
            return orchestrationService.launchResultJson(runtimeUuid);
        } catch (BaseException e) {
            log.warn("launch_workflow rejected, workflowUuid:{}, userId:{}", workflowUuid, userId, e);
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            log.error("launch_workflow failed, workflowUuid:{}, userId:{}", workflowUuid, userId, e);
            return "ERROR: launch failed, " + e.getMessage();
        }
    }

    /**
     * 把 LLM 生成的平铺 input 对象转换为引擎输入 List&lt;ObjectNode&gt;。
     * 类型按 START 节点入参定义映射（TEXT/NUMBER/BOOL/OPTIONS/FILES），未定义的参数按 TEXT 兜底；
     * 转换失败的参数跳过并记日志（由 START 节点必填校验兜底报错）。
     */
    private List<ObjectNode> convertInputs(String workflowUuid, ObjectNode input) {
        Map<String, WfNodeIO> defined = orchestrationService.getStartInputDefs(workflowUuid);
        List<ObjectNode> result = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> it = input.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            JsonNode value = entry.getValue();
            WfNodeIO def = defined.get(name);
            Integer type = null != def ? def.getType() : WfIODataTypeEnum.TEXT.getValue();
            ObjectNode content = JsonUtil.createObjectNode();
            content.put("type", type);
            content.put("title", null != def && StringUtils.isNotBlank(def.getTitle()) ? def.getTitle() : name);
            content.set("value", value);
            ObjectNode wrapper = JsonUtil.createObjectNode();
            wrapper.put("name", name);
            wrapper.set("content", content);
            try {
                //createNodeIOData 做类型校验/转换，失败抛异常：跳过该参数，由 START 必填校验兜底
                WfNodeIODataUtil.createNodeIOData(wrapper);
                result.add(wrapper);
            } catch (Exception e) {
                log.warn("launch_workflow input convert failed, workflowUuid:{}, param:{}, type:{}", workflowUuid, name, type, e);
            }
        }
        return result;
    }

    /**
     * 工具名 slug：保留字母数字、其余折叠为下划线，截断 20 字符（中文名整体坍缩为 _ 时返回空串，
     * 最终工具名退化为 wf_&lt;uuid前8位&gt;，仍全局唯一）。
     */
    static String slugify(String title) {
        if (StringUtils.isBlank(title)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean lastUnderscore = true;
        for (char c : title.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(Character.toLowerCase(c));
                lastUnderscore = false;
            } else if (!lastUnderscore) {
                sb.append('_');
                lastUnderscore = true;
            }
        }
        String s = sb.toString();
        while (s.endsWith("_")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.length() > 20 ? s.substring(0, 20) : s;
    }
}
