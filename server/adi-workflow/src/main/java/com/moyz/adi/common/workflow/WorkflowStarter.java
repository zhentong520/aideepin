package com.moyz.adi.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.workflow.WorkflowTestRunReq;
import com.moyz.adi.common.entity.*;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.service.*;
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
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static com.moyz.adi.common.constant.SseConstant.SSE_TIMEOUT;
import static com.moyz.adi.common.enums.ErrorEnum.*;

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

    @Resource
    private WorkflowRuntimeNodeService workflowRuntimeNodeService;

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
                    workflowRuntimeNodeService);
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
                workflowRuntimeNodeService);
        workflowEngine.setTriggerSource(triggerSource);
        return workflowEngine.blockingRun(user, userInputs);
    }

    @Async("workflowExecutor")
    public void resumeFlow(String runtimeUuid, String userInput) {
        WorkflowEngine workflowEngine = InterruptedFlow.get(runtimeUuid);
        if (null == workflowEngine) {
            log.error("Workflow resume execution failed, runtime:{}", runtimeUuid);
            throw new BaseException(A_WF_RESUME_FAIL);
        }
        try {
            workflowEngine.resume(userInput);
        } catch (Throwable e) {
            log.error("resumeFlow execution exception, runtimeUuid:{}", runtimeUuid, e);
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
                    workflowRuntimeNodeService);
            workflowEngine.run(user, userInputs, sseUuid, startNodeUuid, upstreamContext, true);
        } catch (Throwable e) {
            log.error("asyncTestRun execution exception, workflowUuid:{}", workflow.getUuid(), e);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, "Workflow test run error:" + e.getMessage());
        }
    }

}
