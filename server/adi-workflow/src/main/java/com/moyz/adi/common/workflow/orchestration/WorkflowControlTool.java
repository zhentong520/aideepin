package com.moyz.adi.common.workflow.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.service.WorkflowRuntimeService;
import com.moyz.adi.common.util.JsonUtil;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 业务编排 Harness M2 对话控制工具（2026-10-30）。
 * <p>
 * 两个固定名工具，让 LLM 能在对话里响应用户的"取消/暂停这个工作流"：
 * <ul>
 *   <li>{@code cancel_workflow}：终止一个运行中/停驻中的工作流 runtime；</li>
 *   <li>{@code pause_workflow}：在节点边界暂停一个执行中的工作流 runtime（可稍后恢复）。</li>
 * </ul>
 * 归属校验在 {@link WorkflowRuntimeService} 的 cancelForUser/pauseForUser 内完成
 * （仅属主可操作），本工具层只做参数解析与错误转译。
 * 与 {@link LaunchWorkflowTool} 同构：错误按约定返回 {@code ERROR: <可读原因>}。
 * <p>
 * Chat-side control tools (cancel/pause) for workflow runtimes. Ownership is enforced
 * in the runtime service; errors are returned as {@code ERROR: <reason>} for the LLM.
 */
@Slf4j
@Component
public class WorkflowControlTool {

    public static final String CANCEL_TOOL_NAME = "cancel_workflow";
    public static final String PAUSE_TOOL_NAME = "pause_workflow";

    @Resource
    private WorkflowRuntimeService workflowRuntimeService;

    /**
     * cancel_workflow 工具规格。
     */
    public ToolSpecification buildCancelSpecification() {
        return ToolSpecification.builder()
                .name(CANCEL_TOOL_NAME)
                .description("取消（终止）一个正在执行或等待用户输入/已暂停的业务工作流。当用户明确表示不想继续某个工作流时调用。终止后不可恢复。")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("runtime_uuid", JsonObjectSchema.builder()
                                .description("要取消的工作流 runtime_uuid（对话中出现过的工作流卡片/启动结果中的 uuid）")
                                .build())
                        .required("runtime_uuid")
                        .build())
                .build();
    }

    /**
     * pause_workflow 工具规格。
     */
    public ToolSpecification buildPauseSpecification() {
        return ToolSpecification.builder()
                .name(PAUSE_TOOL_NAME)
                .description("暂停一个正在执行中的业务工作流。工作流会在当前节点执行完毕后的边界处暂停，之后可通过恢复操作继续执行。仅对执行中的工作流有效。")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("runtime_uuid", JsonObjectSchema.builder()
                                .description("要暂停的工作流 runtime_uuid")
                                .build())
                        .required("runtime_uuid")
                        .build())
                .build();
    }

    /**
     * 执行取消。argsJson 形如 {"runtime_uuid": "..."}。
     */
    public String invokeCancel(String argsJson, Long userId) {
        return invokeControl(argsJson, userId, true);
    }

    /**
     * 执行暂停。argsJson 形如 {"runtime_uuid": "..."}。
     */
    public String invokePause(String argsJson, Long userId) {
        return invokeControl(argsJson, userId, false);
    }

    private String invokeControl(String argsJson, Long userId, boolean cancel) {
        if (null == userId) {
            return "ERROR: no user context, cannot control workflow";
        }
        JsonNode parsed;
        try {
            parsed = JsonUtil.toJsonNode(StringUtils.defaultIfBlank(argsJson, "{}"));
        } catch (Exception e) {
            log.warn("workflow control arguments parse failed:{}", StringUtils.abbreviate(argsJson, 200));
            return "ERROR: invalid arguments (must be a JSON object)";
        }
        if (!(parsed instanceof ObjectNode args)) {
            return "ERROR: invalid arguments (must be a JSON object)";
        }
        JsonNode uuidNode = args.path("runtime_uuid");
        if (!uuidNode.isTextual() || StringUtils.isBlank(uuidNode.asText())) {
            return "ERROR: runtime_uuid is required";
        }
        String runtimeUuid = uuidNode.asText().trim();
        try {
            boolean ok = cancel
                    ? workflowRuntimeService.cancelForUser(runtimeUuid, userId)
                    : workflowRuntimeService.pauseForUser(runtimeUuid, userId);
            if (!ok) {
                return cancel
                        ? "ERROR: cannot cancel, runtime_uuid=" + runtimeUuid + " not found, not owned by you, or already finished"
                        : "ERROR: cannot pause, runtime_uuid=" + runtimeUuid + " not found, not owned by you, or not currently running";
            }
            if (cancel) {
                return JsonUtil.createObjectNode()
                        .put("runtime_uuid", runtimeUuid)
                        .put("status", "cancel_requested")
                        .put("hint", "取消请求已受理。执行中的工作流会在当前节点结束后终止；等待输入/已暂停的工作流已直接取消。请向用户简述已取消。")
                        .toString();
            }
            return JsonUtil.createObjectNode()
                    .put("runtime_uuid", runtimeUuid)
                    .put("status", "pause_requested")
                    .put("hint", "暂停请求已受理。工作流会在当前节点执行完毕后的边界处暂停。请向用户简述已发起暂停，暂停后可恢复。")
                    .toString();
        } catch (Exception e) {
            log.warn("workflow control rejected/failed, action:{}, runtimeUuid:{}, userId:{}",
                    cancel ? "cancel" : "pause", runtimeUuid, userId, e);
            return "ERROR: " + StringUtils.defaultIfBlank(e.getMessage(), "operation failed");
        }
    }
}
