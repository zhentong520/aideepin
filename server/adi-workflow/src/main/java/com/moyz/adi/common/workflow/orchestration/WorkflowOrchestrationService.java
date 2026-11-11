package com.moyz.adi.common.workflow.orchestration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.constant.AdiConstant;import com.moyz.adi.common.entity.CharacterMessage;
import com.moyz.adi.common.entity.SceneWorkflow;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowRuntime;
import com.moyz.adi.common.interfaces.WorkflowToolSpi;
import com.moyz.adi.common.mapper.CharacterMessageMapper;
import com.moyz.adi.common.mapper.WorkflowRunMapper;
import com.moyz.adi.common.service.SceneService;
import com.moyz.adi.common.service.SysConfigService;
import com.moyz.adi.common.workflow.WfComponentNameEnum;
import com.moyz.adi.common.workflow.WfNodeInputConfig;
import com.moyz.adi.common.workflow.WorkflowStarter;
import com.moyz.adi.common.workflow.def.WfNodeIO;
import com.moyz.adi.common.service.WorkflowComponentService;
import com.moyz.adi.common.service.WorkflowNodeService;
import com.moyz.adi.common.service.WorkflowService;
import com.moyz.adi.common.entity.WorkflowNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业务编排 Harness M1 编排服务（{@link WorkflowToolSpi} 的 adi-workflow 实现，2026-10-30）。
 * <p>
 * 职责：
 * <ul>
 *   <li>{@link #isEnabled()}：sys_config {@code wf_launch_enabled} 灰度总开关（缺省关）；</li>
 *   <li>{@link #prepare(String)}：为场景构造工具包（manifest 使用规则 + 每绑定工作流一条工具规格）；</li>
 *   <li>{@link #execute}：按工具名在「场景绑定集」内反查工作流后启动——绑定校验由集合匹配天然完成，
 *       未绑定的工作流不可能被对话触发（防越权的关键设计）；</li>
 *   <li>{@link #backfillMessageLink}：aiAnswer 落库后把 runtime uuid 回填消息（兜底，吞异常）。</li>
 * </ul>
 * 本类可能运行在 LLM 工具回调线程（ThreadContext 不可达），所有上下文随参数显式传递。
 * <p>
 * Orchestration service implementing {@link WorkflowToolSpi}: builds per-scene workflow tools,
 * resolves tool name within the scene's bound set, launches workflows and backfills message links.
 */
@Slf4j
@Service
public class WorkflowOrchestrationService implements WorkflowToolSpi {

    @Resource
    private SceneService sceneService;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowNodeService workflowNodeService;

    @Resource
    private WorkflowComponentService workflowComponentService;

    @Resource
    private WorkflowStarter workflowStarter;

    /**
     * {@link LaunchWorkflowTool} 反向注入本类（invoke 时反查编排服务），
     * 直接注入会形成循环依赖且 Spring Boot 默认禁止，故 @Lazy 延迟到首次调用解析。
     */
    @Lazy
    @Resource
    private LaunchWorkflowTool launchWorkflowTool;

    @Resource
    private WorkflowControlTool workflowControlTool;

    @Resource
    private WorkflowRunMapper workflowRunMapper;

    @Resource
    private CharacterMessageMapper characterMessageMapper;

    // ==================== SPI 实现 ====================

    @Override
    public boolean isEnabled() {
        //缺省关：灰度总开关，管理端 sys_config 开启后才注入对话工具
        String value = SysConfigService.getByKey(AdiConstant.SysConfigKey.WF_LAUNCH_ENABLED);
        return StringUtils.isNotBlank(value) && Boolean.parseBoolean(value.trim());
    }

    @Override
    public ToolBundle prepare(String sceneUuid) {
        List<Workflow> workflows = listEnabledBoundWorkflows(sceneUuid);
        if (workflows.isEmpty()) {
            return new ToolBundle("", Collections.emptyList());
        }
        List<ToolSpecification> specifications = new ArrayList<>();
        StringBuilder listText = new StringBuilder();
        for (Workflow workflow : workflows) {
            String inputDesc = buildInputDesc(workflow);
            specifications.add(launchWorkflowTool.buildSpecification(workflow, inputDesc));
            listText.append("- 「").append(StringUtils.defaultIfBlank(workflow.getTitle(), workflow.getUuid())).append("」");
            if (StringUtils.isNotBlank(workflow.getRemark())) {
                listText.append("：").append(workflow.getRemark().trim());
            }
            listText.append('\n');
        }
        //M2-T5：固定名控制工具（cancel_workflow / pause_workflow），用户想中止/暂停运行中的工作流时使用
        specifications.add(workflowControlTool.buildCancelSpecification());
        specifications.add(workflowControlTool.buildPauseSpecification());
        return new ToolBundle(buildManifest(listText.toString()), specifications);
    }

    @Override
    public String execute(String toolName, String argsJson, Long userId, String characterUuid,
                          String sceneUuid, String questionUuid) {
        if (StringUtils.isBlank(sceneUuid)) {
            return "ERROR: no scene context, workflow launch is only available inside a scene";
        }
        //M2-T5：固定名控制工具优先路由（cancel/pause 不绑定具体工作流，归属校验在 service 层）
        if (WorkflowControlTool.CANCEL_TOOL_NAME.equals(toolName)) {
            return workflowControlTool.invokeCancel(argsJson, userId);
        }
        if (WorkflowControlTool.PAUSE_TOOL_NAME.equals(toolName)) {
            return workflowControlTool.invokePause(argsJson, userId);
        }
        //工具名必须在场景绑定集内匹配——未绑定的工作流不可对话触发（天然防越权）
        for (Workflow workflow : listEnabledBoundWorkflows(sceneUuid)) {
            if (launchWorkflowTool.toolName(workflow).equals(toolName)) {
                return launchWorkflowTool.invoke(argsJson, workflow.getUuid(), userId,
                        characterUuid, sceneUuid, questionUuid);
            }
        }
        log.warn("workflow tool not bound to scene, toolName:{}, sceneUuid:{}", toolName, sceneUuid);
        return "ERROR: workflow not available in current scene, tool=" + toolName;
    }

    @Override
    public void backfillMessageLink(String characterUuid, String questionUuid) {
        //兜底回填是锦上添花：吞掉一切异常，绝不影响对话主流程
        try {
            if (StringUtils.isBlank(characterUuid) || StringUtils.isBlank(questionUuid)) {
                return;
            }
            List<WorkflowRuntime> runtimes = workflowRunMapper.selectList(new LambdaQueryWrapper<WorkflowRuntime>()
                    .eq(WorkflowRuntime::getConversationUuid, characterUuid)
                    .eq(WorkflowRuntime::getMessageUuid, questionUuid)
                    .eq(WorkflowRuntime::getIsDeleted, false));
            if (runtimes.isEmpty()) {
                return;
            }
            Set<String> runtimeUuids = new LinkedHashSet<>();
            for (WorkflowRuntime runtime : runtimes) {
                if (StringUtils.isNotBlank(runtime.getUuid())) {
                    runtimeUuids.add(runtime.getUuid());
                }
            }
            List<CharacterMessage> messages = characterMessageMapper.selectList(new LambdaQueryWrapper<CharacterMessage>()
                    .eq(CharacterMessage::getUuid, questionUuid)
                    .eq(CharacterMessage::getIsDeleted, false));
            if (messages.isEmpty()) {
                return;
            }
            CharacterMessage message = messages.get(0);
            String existing = StringUtils.defaultString(message.getWfRuntimeUuid());
            StringBuilder merged = new StringBuilder(existing);
            for (String runtimeUuid : runtimeUuids) {
                if (merged.length() > 0) {
                    merged.append(',');
                }
                merged.append(runtimeUuid);
            }
            message.setWfRuntimeUuid(merged.toString());
            characterMessageMapper.updateById(message);
        } catch (Exception e) {
            log.warn("backfill message-workflow link failed, characterUuid:{}, questionUuid:{}",
                    characterUuid, questionUuid, e);
        }
    }

    // ==================== 供 LaunchWorkflowTool 调用 ====================

    /**
     * 启动工作流（conversationUuid = characterUuid，即发起会话）。
     * 同步建 runtime → 异步执行 → 返回 runtime uuid；失败抛 BaseException 由工具层转 "ERROR:"。
     */
    public String launch(User user, String workflowUuid, List<ObjectNode> userInputs,
                         String characterUuid, String questionUuid, String sceneUuid) {
        return workflowStarter.launchFromChat(user, workflowUuid, userInputs,
                characterUuid, questionUuid, sceneUuid);
    }

    /**
     * 启动结果 JSON（LLM 可读）：runtime_uuid + 提示语，指引 LLM 向用户简述"已启动"。
     */
    public String launchResultJson(String runtimeUuid) {
        ObjectNode result = com.moyz.adi.common.util.JsonUtil.createObjectNode();
        result.put("runtime_uuid", runtimeUuid);
        result.put("status", "launched");
        result.put("hint", "工作流已启动，正在异步执行。请向用户简述已启动及用途，不要等待执行结果；用户可随时在工作流卡片查看进度。");
        return result.toString();
    }

    /**
     * 读 START 节点入参定义（name → WfNodeIO），用于 LLM 入参类型映射与工具描述生成。
     * 工作流不存在/无 START 节点/无入参定义时返回空 Map。
     */
    public Map<String, WfNodeIO> getStartInputDefs(String workflowUuid) {
        Workflow workflow = workflowService.getByUuid(workflowUuid);
        if (null == workflow) {
            return Collections.emptyMap();
        }
        List<Long> startComponentIds = workflowComponentService.lambdaQuery()
                .eq(WorkflowComponent::getName, WfComponentNameEnum.START.getName())
                .eq(WorkflowComponent::getIsDeleted, false)
                .list()
                .stream().map(WorkflowComponent::getId).toList();
        if (startComponentIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                .eq(WorkflowNode::getWorkflowId, workflow.getId())
                .eq(WorkflowNode::getIsDeleted, false)
                .list();
        Map<String, WfNodeIO> defs = new HashMap<>();
        for (WorkflowNode node : nodes) {
            if (!startComponentIds.contains(node.getWorkflowComponentId())) {
                continue;
            }
            WfNodeInputConfig inputConfig = node.getInputConfig();
            if (null == inputConfig || null == inputConfig.getUserInputs()) {
                continue;
            }
            for (WfNodeIO io : inputConfig.getUserInputs()) {
                if (StringUtils.isNotBlank(io.getName())) {
                    defs.put(io.getName(), io);
                }
            }
            //取第一个带入参定义的 START 节点即可
            if (!defs.isEmpty()) {
                break;
            }
        }
        return defs;
    }

    // ==================== 内部方法 ====================

    /**
     * 场景绑定的全部启用工作流（按 sort 顺序）。绑定行存在但工作流被删除/停用时跳过。
     */
    private List<Workflow> listEnabledBoundWorkflows(String sceneUuid) {
        if (StringUtils.isBlank(sceneUuid)) {
            return Collections.emptyList();
        }
        List<Workflow> result = new ArrayList<>();
        for (SceneWorkflow bind : sceneService.listWorkflows(sceneUuid)) {
            if (StringUtils.isBlank(bind.getWorkflowUuid())) {
                continue;
            }
            Workflow workflow = workflowService.getByUuid(bind.getWorkflowUuid());
            if (null == workflow || Boolean.FALSE.equals(workflow.getIsEnable())) {
                continue;
            }
            result.add(workflow);
        }
        return result;
    }

    /**
     * START 入参定义 → 工具描述里的入参说明（name(type, title, required)）。
     */
    private String buildInputDesc(Workflow workflow) {
        Map<String, WfNodeIO> defs = getStartInputDefs(workflow.getUuid());
        if (defs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (WfNodeIO def : defs.values()) {
            if (sb.length() > 0) {
                sb.append('；');
            }
            sb.append(def.getName())
                    .append('(')
                    .append(StringUtils.defaultIfBlank(def.getTitle(), def.getName()));
            if (Boolean.TRUE.equals(def.getRequired())) {
                sb.append(", 必填");
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /**
     * 注入 system prompt 的业务工作流使用规则（方案 §5.1 Prompt 约束模板）+ 可用工作流清单。
     */
    private String buildManifest(String workflowList) {
        return """
                ## 业务工作流使用规则
                1. 用户请求与下列某个业务工作流的用途匹配时，优先调用对应工具启动工作流，而不是自己手工模拟业务步骤。
                2. 工具是异步的：调用后立即返回 runtime_uuid 即视为成功，请向用户简述"已启动"与用途，不要编造或等待执行结果。
                3. 工具的必填入参缺失或含糊时，先向用户追问补齐，禁止编造参数值。
                4. 同一工作流被重复触发前，先确认用户意图（避免误启动重复流程）。
                5. 用户想取消/终止某个工作流时调用 cancel_workflow，想暂停时调用 pause_workflow；两者的 runtime_uuid 必须来自本对话中已出现的工作流，禁止编造。

                ## 当前场景可用的工作流
                """ + workflowList;
    }
}
