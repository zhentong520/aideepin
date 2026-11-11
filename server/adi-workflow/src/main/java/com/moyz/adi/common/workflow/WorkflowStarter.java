package com.moyz.adi.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.workflow.WorkflowTestRunReq;
import com.moyz.adi.common.dto.workflow.WfRuntimeResp;
import com.moyz.adi.common.entity.*;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.service.*;
import com.moyz.adi.common.entity.WfRuntimeCheckpoint;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static com.moyz.adi.common.constant.SseConstant.SSE_TIMEOUT;
import static com.moyz.adi.common.enums.ErrorEnum.*;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_PAUSED;
import static com.moyz.adi.common.workflow.WfConstant.WORKFLOW_PROCESS_STATUS_WAITING_INPUT;

@Slf4j
@Component
public class WorkflowStarter {

    @Lazy
    @Resource
    private WorkflowStarter self;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowNodeService workflowNodeService;

    @Resource
    private WorkflowEdgeService workflowEdgeService;

    @Resource
    private WorkflowComponentService workflowComponentService;

    @Resource
    private WorkflowRuntimeService workflowRuntimeService;

    /** M2-T4：L2 重建恢复按 checkpoint 找停驻节点。 */
    @Resource
    private WfRuntimeCheckpointService checkpointService;

    @Resource
    private WorkflowRuntimeNodeService workflowRuntimeNodeService;

    /** @Lazy 破构造器环：WfNodeProvider 构造器注入本类，若此处立即解析会成环（真实 boot 才暴露）。 */
    @Lazy
    @Resource
    private WfNodeProvider nodeProvider;

    @Resource
    private SseManager sseManager;

    @Resource
    private AdiProperties adiProperties;


    public SseEmitter streaming(User user, String workflowUuid, List<ObjectNode> userInputs) {
        String sseUuid = com.moyz.adi.common.util.UuidUtil.createShort();
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT);
        if (!sseManager.checkOrComplete(user, sseUuid, sseEmitter)) {
            return sseEmitter;
        }
        // 工作流的 START 事件由异步线程在 WorkflowEngine.run 中发送（需携带 wfRuntimeResp 数据），
        // 所以这里不能像聊天那样直接调 startSse；只注册 emitter，供异步线程通过 sseUuid 取回使用。
        // <p>
        // The workflow START event is sent from the async thread inside WorkflowEngine.run (it
        // carries the wfRuntimeResp payload), so unlike chat we cannot call startSse here; we only
        // register the emitter so the async thread can look it up by sseUuid.
        sseManager.register(sseUuid, sseEmitter, user.getId());
        Workflow workflow = workflowService.getByUuid(workflowUuid);
        if (null == workflow) {
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage(A_WF_NOT_FOUND.getInfo()));
            return sseEmitter;
        } else if (Boolean.FALSE.equals(workflow.getIsEnable())) {
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage(A_WF_DISABLED.getInfo()));
            return sseEmitter;
        }
        try {
            self.asyncRun(user, workflow, userInputs, sseUuid);
        } catch (RejectedExecutionException e) {
            //The workflow pool is saturated: tell the client instead of dropping the SSE silently
            log.warn("workflow rejected, too many running workflows, workflowUuid:{}", workflowUuid);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid,
                    SpringUtil.getMessage(A_WF_TOO_MANY_RUNNING.getInfo()));
        }
        return sseEmitter;
    }

    @Async("workflowExecutor")
    public void asyncRun(User user, Workflow workflow, List<ObjectNode> userInputs, String sseUuid) {
        log.info("WorkflowEngine run,userId:{},workflowUuid:{},userInputs:{}", user.getId(), workflow.getUuid(), userInputs);
        try {
            List<WorkflowComponent> components = workflowComponentService.getAllEnable();
            List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                    .eq(WorkflowNode::getWorkflowId, workflow.getId())
                    .eq(WorkflowNode::getIsDeleted, false)
                    .list();
            List<WorkflowEdge> edges = workflowEdgeService.lambdaQuery()
                    .eq(WorkflowEdge::getWorkflowId, workflow.getId())
                    .eq(WorkflowEdge::getIsDeleted, false)
                    .list();
            WorkflowEngine workflowEngine = new WorkflowEngine(workflow,
                    sseManager,
                    components,
                    nodes,
                    edges,
                    workflowRuntimeService,
                    checkpointService,
                    workflowRuntimeNodeService,
                    nodeProvider);
            workflowEngine.run(user, userInputs, sseUuid);
        } catch (Throwable e) {
            log.error("asyncRun execution exception, workflowUuid:{}", workflow.getUuid(), e);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, "Workflow execution error:" + e.getMessage());
        }
    }

    /**
     * Run a workflow in blocking mode, wait for completion and return the result.
     */
    public Map<String, Object> blocking(User user, String workflowUuid, List<ObjectNode> userInputs) {
        return blocking(user, workflowUuid, userInputs, "manual");
    }

    /**
     * T9-01：带触发来源的阻塞运行（定时/webhook/API 触发走本入口）。
     * triggerSource 落运行记录 trigger_source 列（manual/schedule/webhook/api）。
     */
    public Map<String, Object> blocking(User user, String workflowUuid, List<ObjectNode> userInputs, String triggerSource) {
        Workflow workflow = workflowService.getByUuid(workflowUuid);
        if (null == workflow) {
            throw new BaseException(A_WF_NOT_FOUND);
        } else if (Boolean.FALSE.equals(workflow.getIsEnable())) {
            throw new BaseException(A_WF_DISABLED);
        }

        List<WorkflowComponent> components = workflowComponentService.getAllEnable();
        List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                .eq(WorkflowNode::getWorkflowId, workflow.getId())
                .eq(WorkflowNode::getIsDeleted, false)
                .list();
        List<WorkflowEdge> edges = workflowEdgeService.lambdaQuery()
                .eq(WorkflowEdge::getWorkflowId, workflow.getId())
                .eq(WorkflowEdge::getIsDeleted, false)
                .list();

        WorkflowEngine workflowEngine = new WorkflowEngine(workflow,
                sseManager,
                components,
                nodes,
                edges,
                workflowRuntimeService,
                checkpointService,
                workflowRuntimeNodeService,
                nodeProvider);
        workflowEngine.setTriggerSource(triggerSource);
        return workflowEngine.blockingRun(user, userInputs);
    }

    @Async("workflowExecutor")
    public void resumeFlow(String runtimeUuid, String userInput, User user) {
        //M2-T2/T3：优先命中 WAITING_INPUT 注册表；未命中且为 PAUSED（暂停停驻）则走
        //resumeFromPause 续跑。两类停驻共用 /resume 端点（前端「继续」按钮统一入口）。
        WorkflowEngine workflowEngine = InterruptedFlow.get(runtimeUuid);
        if (null == workflowEngine) {
            workflowEngine = PausedFlow.get(runtimeUuid);
            if (null == workflowEngine) {
                //M2-T4（L2 重建）：进程重启后注册表为空——按 checkpoint 从停驻节点重建恢复
                resumeFromCheckpoint(runtimeUuid, userInput, user);
                return;
            }
            log.info("resumeFlow: PAUSED runtime found in PausedFlow, resuming without user input, runtime:{}", runtimeUuid);
            try {
                workflowEngine.resumeFromPause();
            } catch (Throwable e) {
                log.error("resumeFlow (from pause) execution exception, runtimeUuid:{}", runtimeUuid, e);
            }
            return;
        }
        try {
            workflowEngine.resume(userInput);
        } catch (Throwable e) {
            log.error("resumeFlow execution exception, runtimeUuid:{}", runtimeUuid, e);
        }
    }

    /**
     * 业务编排 Harness M2-T4（L2 重建恢复，2026-11-11）：进程重启后的停驻实例恢复。
     * 进程内注册表（InterruptedFlow/PausedFlow）丢失后，按 checkpoint 行重建引擎：
     * <ol>
     *   <li>校验 runtime 仍处停驻态（WAITING_INPUT/PAUSED）且有 checkpoint 行；</li>
     *   <li>按 workflow_uuid 重载节点/边/组件并 new WorkflowEngine（与 asyncRunFromChat 同款）；</li>
     *   <li>setExternalRuntime 复用原 runtime（不新建记录）+ skipInputUpdate 保留原输入；</li>
     *   <li>loadRuntimeContext 回放本次运行已完成节点的输出（T3-10 指定节点执行机制）；</li>
     *   <li>run(user, ..., startNodeUuid=pausedNodeUuid) 从断点节点起跑。</li>
     * </ol>
     * 无 checkpoint 的旧数据（047 之前）明确报恢复失败，不静默。
     */
    private void resumeFromCheckpoint(String runtimeUuid, String userInput, User user) {
        WorkflowRuntime runtime = workflowRuntimeService.getByUuid(runtimeUuid);
        if (null == runtime) {
            throw new BaseException(A_WF_RESUME_FAIL);
        }
        int status = null == runtime.getStatus() ? 0 : runtime.getStatus();
        if (status != WORKFLOW_PROCESS_STATUS_WAITING_INPUT && status != WORKFLOW_PROCESS_STATUS_PAUSED) {
            log.error("resumeFromCheckpoint: runtime not parked, runtime:{}, status:{}", runtimeUuid, status);
            throw new BaseException(A_WF_RESUME_FAIL);
        }
        WfRuntimeCheckpoint checkpoint = checkpointService.getByRuntimeUuid(runtimeUuid);
        if (null == checkpoint || StringUtils.isBlank(checkpoint.getPausedNodeUuid())) {
            log.error("resumeFromCheckpoint: no checkpoint row (pre-047 legacy or save failed), runtime:{}", runtimeUuid);
            throw new BaseException(A_WF_RESUME_FAIL);
        }
        log.info("resumeFromCheckpoint: rebuilding engine, runtime:{}, workflow:{}, pausedNode:{}",
                runtimeUuid, checkpoint.getWorkflowUuid(), checkpoint.getPausedNodeUuid());
        Workflow workflow = workflowService.getByUuid(checkpoint.getWorkflowUuid());
        if (null == workflow) {
            throw new BaseException(A_WF_NOT_FOUND);
        }
        //WAITING_INPUT 恢复必须带上用户反馈（HumanFeedbackNode 从 state 读 human_feedback）；
        //PAUSED 恢复无需反馈
        if (status == WORKFLOW_PROCESS_STATUS_WAITING_INPUT && StringUtils.isBlank(userInput)) {
            log.error("resumeFromCheckpoint: WAITING_INPUT resume requires user feedback, runtime:{}", runtimeUuid);
            throw new BaseException(A_WF_RESUME_FAIL);
        }
        try {
            List<WorkflowComponent> components = workflowComponentService.getAllEnable();
            List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                    .eq(WorkflowNode::getWorkflowId, workflow.getId())
                    .eq(WorkflowNode::getIsDeleted, false)
                    .list();
            List<WorkflowEdge> edges = workflowEdgeService.lambdaQuery()
                    .eq(WorkflowEdge::getWorkflowId, workflow.getId())
                    .eq(WorkflowEdge::getIsDeleted, false)
                    .list();
            WorkflowEngine workflowEngine = new WorkflowEngine(workflow,
                    sseManager,
                    components,
                    nodes,
                    edges,
                    workflowRuntimeService,
                    checkpointService,
                    workflowRuntimeNodeService,
                    nodeProvider);
            workflowEngine.setExternalRuntime(workflowRuntimeService.getRespByUuid(runtimeUuid));
            workflowEngine.setSkipInputUpdate(true);
            //M2-T4：WAITING_INPUT 反馈经图初始输入注入（重建后 langgraph checkpoint 已丢失）
            workflowEngine.setResumeFeedback(userInput);
            //回放本次运行已完成节点的输出（给断点节点的 refInput 补齐上游变量）
            Map<String, List<NodeIOData>> upstreamContext = workflowRuntimeService.loadRuntimeContext(runtime.getId());
            //重启恢复不走 SSE 订阅：dummy emitter 兜底（与 asyncRunFromChat 同款）
            String dummySseUuid = "recovery-" + com.moyz.adi.common.util.UuidUtil.createShort();
            SseEmitter dummyEmitter = new SseEmitter(SSE_TIMEOUT);
            sseManager.register(dummySseUuid, dummyEmitter);
            workflowEngine.run(user, List.of(), dummySseUuid, checkpoint.getPausedNodeUuid(), upstreamContext, false);
        } catch (Throwable e) {
            log.error("resumeFromCheckpoint execution exception, runtimeUuid:{}", runtimeUuid, e);
        }
    }

    /**
     * 业务编排 Harness M1（2026-10-30）：对话触发的工作流启动（launch_workflow 工具后端入口）。
     * <p>
     * 同步阶段（LLM 工具回调线程）：校验工作流 → 建 runtime（落 conversation_uuid/message_uuid/
     * scene_uuid 关联列）→ 立即返回 runtime_uuid（LLM 拿到后向用户简述"已启动"，不等执行完）。
     * 异步阶段（{@code workflowExecutor} 线程池）：引擎复用预建 runtime 执行全流程。
     * <p>
     * 注意：本方法可能运行在工具回调线程（ThreadContext 不可达），所有上下文必须随参数显式传入，
     * 内部不得依赖 ThreadContext 的服务（WorkflowStarter 自身只用 @Resource 注入，安全）。
     *
     * @param conversationUuid 发起会话 uuid（= characterUuid）
     * @param messageUuid      发起提问的 questionUuid（落 runtime.message_uuid）
     * @param sceneUuid        发起时所在场景 uuid（可空）
     * @return runtime uuid（立即返回，不等待执行完成）
     */
    public String launchFromChat(User user, String workflowUuid, List<ObjectNode> userInputs,
                                 String conversationUuid, String messageUuid, String sceneUuid) {
        Workflow workflow = workflowService.getByUuid(workflowUuid);
        if (null == workflow) {
            throw new BaseException(A_WF_NOT_FOUND);
        } else if (Boolean.FALSE.equals(workflow.getIsEnable())) {
            throw new BaseException(A_WF_DISABLED);
        }
        //同步建 runtime：关联列随 create 落库；trigger_source=chat 标记对话触发
        WfRuntimeResp runtime = workflowRuntimeService.create(user, workflow.getId(), false, "chat",
                conversationUuid, messageUuid, sceneUuid);
        try {
            self.asyncRunFromChat(user, workflow, userInputs, runtime);
        } catch (RejectedExecutionException e) {
            log.warn("workflow launch from chat rejected, too many running workflows, workflowUuid:{}", workflowUuid);
            throw new BaseException(A_WF_TOO_MANY_RUNNING);
        }
        return runtime.getUuid();
    }

    /**
     * 业务编排 Harness M1：异步执行对话触发的工作流（复用预建 runtime）。
     * SSE 通道缺省：对话触发不向前端推 SSE（前端经 WorkflowCard 轮询节点状态），
     * 仿 blockingRun(:304-309) 用 dummy emitter 兜底引擎内的全部 SSE 发送调用。
     */
    @Async("workflowExecutor")
    public void asyncRunFromChat(User user, Workflow workflow, List<ObjectNode> userInputs, WfRuntimeResp runtime) {
        log.info("WorkflowEngine run from chat,userId:{},workflowUuid:{},runtimeUuid:{}",
                user.getId(), workflow.getUuid(), runtime.getUuid());
        try {
            List<WorkflowComponent> components = workflowComponentService.getAllEnable();
            List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                    .eq(WorkflowNode::getWorkflowId, workflow.getId())
                    .eq(WorkflowNode::getIsDeleted, false)
                    .list();
            List<WorkflowEdge> edges = workflowEdgeService.lambdaQuery()
                    .eq(WorkflowEdge::getWorkflowId, workflow.getId())
                    .eq(WorkflowEdge::getIsDeleted, false)
                    .list();
            WorkflowEngine workflowEngine = new WorkflowEngine(workflow,
                    sseManager,
                    components,
                    nodes,
                    edges,
                    workflowRuntimeService,
                    checkpointService,
                    workflowRuntimeNodeService,
                    nodeProvider);
            workflowEngine.setExternalRuntime(runtime);
            //对话触发不推 SSE：dummy emitter 兜底引擎内部发送（与 blockingRun 同款）
            String dummySseUuid = "chat-" + com.moyz.adi.common.util.UuidUtil.createShort();
            SseEmitter dummyEmitter = new SseEmitter(SSE_TIMEOUT);
            sseManager.register(dummySseUuid, dummyEmitter);
            workflowEngine.run(user, userInputs, dummySseUuid);
        } catch (Throwable e) {
            log.error("asyncRunFromChat execution exception, workflowUuid:{}, runtimeUuid:{}",
                    workflow.getUuid(), runtime.getUuid(), e);
        }
    }

    /**
     * T3-10：单节点试跑——从指定节点起执行，跳过上游。
     * <p>
     * 同步阶段（本方法，ThreadContext 可用）完成三件事：开关校验、起始节点定位、
     * 加载最近一次成功执行的上下文（有上游但无历史 → 明确抛 A_WF_UPSTREAM_CONTEXT_MISSING，
     * 不静默起跑）。之后异步执行，运行记录打 is_test 标记（选型：is_test 列而非独立通道，
     * 试跑复用引擎全链路可观测性，正式记录查询按 is_test 过滤）。
     */
    public SseEmitter streamingTestRun(User user, String workflowUuid, WorkflowTestRunReq req) {
        if (!adiProperties.getWf().isSingleNodeTestEnabled()) {
            throw new BaseException(A_WF_TEST_RUN_DISABLED);
        }
        if (null == req || StringUtils.isBlank(req.getStartNodeUuid())) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        Workflow workflow = workflowService.getByUuid(workflowUuid);
        if (null == workflow) {
            throw new BaseException(A_WF_NOT_FOUND);
        } else if (Boolean.FALSE.equals(workflow.getIsEnable())) {
            throw new BaseException(A_WF_DISABLED);
        }
        List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                .eq(WorkflowNode::getWorkflowId, workflow.getId())
                .eq(WorkflowNode::getIsDeleted, false)
                .list();
        List<WorkflowEdge> edges = workflowEdgeService.lambdaQuery()
                .eq(WorkflowEdge::getWorkflowId, workflow.getId())
                .eq(WorkflowEdge::getIsDeleted, false)
                .list();
        WorkflowNode startNode = nodes.stream()
                .filter(item -> item.getUuid().equals(req.getStartNodeUuid()))
                .findFirst()
                .orElseThrow(() -> new BaseException(A_WF_NODE_NOT_FOUND));
        //起跑节点有上游时，必须有最近一次成功执行的上下文，否则明确报错（任务书要求，不静默跑）
        boolean hasUpstream = edges.stream().anyMatch(edge -> edge.getTargetNodeUuid().equals(startNode.getUuid()));
        Map<String, List<NodeIOData>> upstreamContext;
        if (hasUpstream) {
            upstreamContext = requireUpstreamContext(true, startNode,
                    workflowRuntimeService.loadLastSuccessContext(workflow.getId(), user.getId()));
        } else {
            upstreamContext = requireUpstreamContext(false, startNode, Map.of());
        }

        String sseUuid = com.moyz.adi.common.util.UuidUtil.createShort();
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT);
        if (!sseManager.checkOrComplete(user, sseUuid, sseEmitter)) {
            return sseEmitter;
        }
        sseManager.register(sseUuid, sseEmitter, user.getId());
        try {
            self.asyncTestRun(user, workflow, req.getInputs(), sseUuid, req.getStartNodeUuid(), upstreamContext);
        } catch (RejectedExecutionException e) {
            log.warn("workflow test run rejected, too many running workflows, workflowUuid:{}", workflowUuid);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid,
                    SpringUtil.getMessage(A_WF_TOO_MANY_RUNNING.getInfo()));
        }
        return sseEmitter;
    }

    /**
     * T3-10：上游上下文门禁（包私有以便单测）。起跑节点有上游但无历史成功上下文 →
     * 明确抛 A_WF_UPSTREAM_CONTEXT_MISSING（任务书要求：不静默跑）；无上游直接放行空上下文。
     */
    static Map<String, List<NodeIOData>> requireUpstreamContext(boolean hasUpstream, WorkflowNode startNode,
                                                                Map<String, List<NodeIOData>> loaded) {
        if (!hasUpstream) {
            return Map.of();
        }
        if (null == loaded || loaded.isEmpty()) {
            throw new BaseException(A_WF_UPSTREAM_CONTEXT_MISSING, startNode.getTitle());
        }
        return loaded;
    }

    @Async("workflowExecutor")
    public void asyncTestRun(User user, Workflow workflow, List<ObjectNode> userInputs, String sseUuid,
                             String startNodeUuid, Map<String, List<NodeIOData>> upstreamContext) {
        log.info("WorkflowEngine test run,userId:{},workflowUuid:{},startNodeUuid:{}",
                user.getId(), workflow.getUuid(), startNodeUuid);
        try {
            List<WorkflowComponent> components = workflowComponentService.getAllEnable();
            List<WorkflowNode> nodes = workflowNodeService.lambdaQuery()
                    .eq(WorkflowNode::getWorkflowId, workflow.getId())
                    .eq(WorkflowNode::getIsDeleted, false)
                    .list();
            List<WorkflowEdge> edges = workflowEdgeService.lambdaQuery()
                    .eq(WorkflowEdge::getWorkflowId, workflow.getId())
                    .eq(WorkflowEdge::getIsDeleted, false)
                    .list();
            WorkflowEngine workflowEngine = new WorkflowEngine(workflow,
                    sseManager,
                    components,
                    nodes,
                    edges,
                    workflowRuntimeService,
                    checkpointService,
                    workflowRuntimeNodeService,
                    nodeProvider);
            workflowEngine.run(user, userInputs, sseUuid, startNodeUuid, upstreamContext, true);
        } catch (Throwable e) {
            log.error("asyncTestRun execution exception, workflowUuid:{}", workflow.getUuid(), e);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, "Workflow test run error:" + e.getMessage());
        }
    }

}
