package com.moyz.adi.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.dto.workflow.WfRuntimeMetricsSummary;
import com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto;
import com.moyz.adi.common.dto.workflow.WfRuntimeResp;
import com.moyz.adi.common.entity.*;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.WorkflowRuntimeNodeService;
import com.moyz.adi.common.service.WorkflowRuntimeService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import com.moyz.adi.common.workflow.WfFeatureGate;
import com.moyz.adi.common.workflow.WfNodeIODataUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.def.WfNodeIO;
import com.moyz.adi.common.workflow.metrics.LLMMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bsc.async.AsyncGenerator;
import org.bsc.langgraph4j.*;
import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.bsc.langgraph4j.langchain4j.generators.StreamingChatGenerator;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.StateSnapshot;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import com.moyz.adi.common.helper.SseManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

import static com.moyz.adi.common.cosntant.AdiConstant.WorkflowConstant.*;
import static com.moyz.adi.common.enums.ErrorEnum.*;
import static com.moyz.adi.common.workflow.WfComponentNameEnum.HUMAN_FEEDBACK;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class WorkflowEngine {
    private CompiledGraph<WfNodeState> app;
    private final Workflow workflow;
    private final List<WorkflowComponent> components;
    private final List<WorkflowNode> wfNodes;
    private final List<WorkflowEdge> wfEdges;
    private final SseManager sseManager;
    private final WorkflowRuntimeService workflowRuntimeService;
    private final WorkflowRuntimeNodeService workflowRuntimeNodeService;

    private final ObjectStreamStateSerializer<WfNodeState> stateSerializer = new ObjectStreamStateSerializer<>(WfNodeState::new);
    private final Map<String, List<StateGraph<WfNodeState>>> stateGraphNodes = new HashMap<>();
    private final Map<String, List<StateGraph<WfNodeState>>> stateGraphEdges = new HashMap<>();
    private final Map<String, String> rootToSubGraph = new HashMap<>();
    private final Map<String, GraphCompileNode> nodeToParallelBranch = new HashMap<>();
    /**
     * T9-07：节点 uuid → onError 分支目标节点 uuid（构图阶段由 on_error 边登记）。
     * runNode 失败时按此路由错误分支，而不是中断整个工作流。
     */
    private final Map<String, String> nodeToErrorBranch = new HashMap<>();

    private String sseUuid;
    private User user;
    private WfState wfState;
    private WfRuntimeResp wfRuntimeResp;
    /**
     * T9-01：触发来源 manual/schedule/webhook/api，落运行记录 trigger_source 列。
     */
    private String triggerSource = "manual";
    /**
     * Wall-clock start of this run (epoch millis), captured once on entry. Used to compute total
     * runtime duration at terminal status — sum-of-node durations would inflate when parallel
     * branches execute concurrently.
     */
    private long runStartedMillis;

    public WorkflowEngine(
            Workflow workflow,
            SseManager sseManager,
            List<WorkflowComponent> components,
            List<WorkflowNode> nodes,
            List<WorkflowEdge> wfEdges,
            WorkflowRuntimeService workflowRuntimeService,
            WorkflowRuntimeNodeService workflowRuntimeNodeService) {
        this.workflow = workflow;
        this.sseManager = sseManager;
        this.components = components;
        this.wfNodes = nodes;
        this.wfEdges = wfEdges;
        this.workflowRuntimeService = workflowRuntimeService;
        this.workflowRuntimeNodeService = workflowRuntimeNodeService;
    }

    /**
     * T9-01：设置触发来源（manual/schedule/webhook/api），落运行记录 trigger_source 列。
     */
    public void setTriggerSource(String triggerSource) {
        if (null != triggerSource && !triggerSource.isBlank()) {
            this.triggerSource = triggerSource;
        }
    }

    public void run(User user, List<ObjectNode> userInputs, String sseUuid) {
        run(user, userInputs, sseUuid, null, null, false);
    }

    /**
     * T3-10：从指定节点起执行（单节点试跑）。
     * <ul>
     *   <li>startNodeUuid 非空时：跳过上游，直接执行该节点及其下游；</li>
     *   <li>upstreamContext：最近一次成功执行的节点输出（nodeUuid -> 输出参数），用于给
     *       起跑节点补齐上游变量（refInput / 模板引用）；调用方在无历史上下文时必须显式报错；</li>
     *   <li>isTest=true：运行记录打 is_test 标记，不进正式执行记录列表。</li>
     * </ul>
     */
    public void run(User user, List<ObjectNode> userInputs, String sseUuid, String startNodeUuid,
                    Map<String, List<NodeIOData>> upstreamContext, boolean isTest) {
        this.user = user;
        this.sseUuid = sseUuid;
        this.runStartedMillis = System.currentTimeMillis();
        log.info("WorkflowEngine run,userId:{},workflowUuid:{},userInputs:{},startNodeUuid:{},isTest:{}",
                user.getId(), workflow.getUuid(), userInputs, startNodeUuid, isTest);
        if (!this.workflow.getIsEnable()) {
            if (isTest) {
                throw new BaseException(ErrorEnum.A_WF_DISABLED);
            }
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage(ErrorEnum.A_WF_DISABLED.getInfo()));
            throw new BaseException(ErrorEnum.A_WF_DISABLED);
        }

        Long workflowId = this.workflow.getId();
        this.wfRuntimeResp = workflowRuntimeService.create(user, workflowId, isTest, triggerSource);
        //试跑同样发送 START 事件（携带 runtime uuid），前端需要它查询试跑明细/结果
        this.sseManager.startSse(user, sseUuid, JsonUtil.toJson(wfRuntimeResp));

        String runtimeUuid = this.wfRuntimeResp.getUuid();
        try {
            WorkflowNode startNode;
            if (StringUtils.isNotBlank(startNodeUuid)) {
                startNode = wfNodes.stream().filter(item -> item.getUuid().equals(startNodeUuid)).findFirst()
                        .orElseThrow(() -> new BaseException(ErrorEnum.A_WF_NODE_NOT_FOUND));
                //把起跑节点当作根节点构图：临时剪掉它的入边，避免 buildCompileNode 把它当作普通下游节点
                wfEdges.removeIf(edge -> edge.getTargetNodeUuid().equals(startNodeUuid));
            } else {
                Pair<WorkflowNode, Set<WorkflowNode>> startAndEnds = findStartAndEndNode();
                startNode = startAndEnds.getLeft();
            }
            List<NodeIOData> wfInputs;
            if (StringUtils.isNotBlank(startNodeUuid)) {
                //指定节点起跑：宽松组装输入（起跑节点通常不是 START 节点，不按 START 定义强校验）
                wfInputs = new ArrayList<>();
                if (null != userInputs) {
                    for (ObjectNode userInput : userInputs) {
                        try {
                            wfInputs.add(WfNodeIODataUtil.createNodeIOData(userInput));
                        } catch (Exception e) {
                            log.warn("Test run: skip invalid user input:{}", e.getMessage());
                        }
                    }
                }
            } else {
                wfInputs = getAndCheckUserInput(userInputs, startNode);
            }
            //工作流运行实例状态
            this.wfState = new WfState(user, wfInputs, runtimeUuid);
            //T3-10：回放最近一次成功执行的上游输出，让 refInput/模板引用可以命中历史变量
            seedUpstreamContext(upstreamContext);
            workflowRuntimeService.updateInput(this.wfRuntimeResp.getId(), wfState);
            CompileNode rootCompileNode = new CompileNode();
            rootCompileNode.setId(startNode.getUuid());

            //构建整棵树（检测节点重复访问防止无限循环）
            Map<String, Integer> nodeVisitCount = new HashMap<>();
            buildCompileNode(rootCompileNode, startNode, nodeVisitCount);

//Main state graph
            //主状态图
            StateGraph<WfNodeState> mainStateGraph = new StateGraph<>(stateSerializer);
            this.wfState.addEdge(START, startNode.getUuid());
            //构建包括所有节点的状态图
            buildStateGraph(null, mainStateGraph, rootCompileNode);

            MemorySaver saver = new MemorySaver();
            CompileConfig compileConfig = CompileConfig.builder()
                    .checkpointSaver(saver)
                    .interruptBefore(wfState.getInterruptNodes().toArray(String[]::new))
                    .build();
            app = mainStateGraph.compile(compileConfig);
            RunnableConfig invokeConfig = RunnableConfig.builder()
                    .build();
            exe(invokeConfig, false);
        } catch (Throwable e) {
            errorWhenExe(e instanceof Exception ? (Exception) e : new RuntimeException(e));
        }
    }

    /**
     * T3-10：把最近一次成功执行的节点输出种进 completedNodes（只构造实例、不执行），
     * 供起跑节点的 refInput 解析（wfState.getIOByNodeUuid）与 getLatestOutputs 使用。
     * 包私有以便单测。
     */
    void seedUpstreamContext(Map<String, List<NodeIOData>> upstreamContext) {
        if (null == upstreamContext || upstreamContext.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<NodeIOData>> entry : upstreamContext.entrySet()) {
            WorkflowNode seedNodeDef = wfNodes.stream().filter(item -> item.getUuid().equals(entry.getKey())).findFirst().orElse(null);
            WorkflowComponent seedComponent = null == seedNodeDef ? null : components.stream()
                    .filter(item -> item.getId().equals(seedNodeDef.getWorkflowComponentId())).findFirst().orElse(null);
            if (null == seedNodeDef || null == seedComponent) {
                log.warn("seedUpstreamContext: skip missing node/component, nodeUuid:{}", entry.getKey());
                continue;
            }
            WfNodeState seedState = new WfNodeState();
            seedState.getOutputs().addAll(entry.getValue());
            wfState.getCompletedNodes().add(WfNodeFactory.create(seedComponent, seedNodeDef, wfState, seedState));
        }
        log.info("seedUpstreamContext: seeded {} upstream nodes", upstreamContext.size());
    }

    private void exe(RunnableConfig invokeConfig, boolean resume) {
//Do not use langgraph4j state update methods, no need to pass input
        //不使用langgraph4j state的update相关方法，无需传入input
        AsyncGenerator<NodeOutput<WfNodeState>> outputs = app.stream(resume ? null : Map.of(), invokeConfig);
        streamingResult(wfState, outputs, sseUuid);

        StateSnapshot<WfNodeState> stateSnapshot = app.getState(invokeConfig);
        String nextNode = stateSnapshot.config().nextNode().orElse("");
        //还有下个节点，表示进入中断状态，等待用户输入后继续执行
        if (StringUtils.isNotBlank(nextNode) && !nextNode.equalsIgnoreCase(END)) {
            String intTip = WorkflowUtil.getHumanFeedbackTip(nextNode, wfNodes);
            SseManager.parseAndSendPartialMsg(sseUuid, "[NODE_WAIT_FEEDBACK_BY_" + nextNode + "]", intTip);
            InterruptedFlow.put(wfState.getUuid(), this);
            //更新状态
            wfState.setProcessStatus(WORKFLOW_PROCESS_STATUS_WAITING_INPUT);
            WorkflowRuntime interruptedRuntime = workflowRuntimeService.updateOutput(wfRuntimeResp.getId(), wfState, runStartedMillis, computeTokenSummary());
            pushRuntimeMetrics(interruptedRuntime);
        } else {
            WorkflowRuntime updatedRuntime = workflowRuntimeService.updateOutput(wfRuntimeResp.getId(), wfState, runStartedMillis, computeTokenSummary());
            // Push the terminal metrics snapshot before completing the stream
            pushRuntimeMetrics(updatedRuntime);
            // updatedRuntime is null only if the runtime row was concurrently deleted; fall back
            // to the in-memory wfState output so the client still gets a complete event.
            ObjectNode output = updatedRuntime != null ? updatedRuntime.getOutput() : null;
            sseManager.sendComplete(user.getId(), sseUuid, JsonUtil.toJson(output != null ? output : JsonUtil.createObjectNode()));
            InterruptedFlow.remove(wfState.getUuid());
        }
    }

    /**
     * 中断流程等待用户输入时，会进行暂停状态，用户输入后调用本方法执行流程剩余部分
     *
     * @param userInput 用户输入
     */
    public void resume(String userInput) {
        RunnableConfig invokeConfig = RunnableConfig.builder().build();
        try {
            app.updateState(invokeConfig, Map.of(HUMAN_FEEDBACK_KEY, userInput), null);
            exe(invokeConfig, true);
        } catch (Throwable e) {
            errorWhenExe(e instanceof Exception ? (Exception) e : new RuntimeException(e));
        } finally {
            //有可能多次接收人机交互，待整个流程完全执行后才能删除
            if (wfState.getProcessStatus() != WORKFLOW_PROCESS_STATUS_WAITING_INPUT) {
                InterruptedFlow.remove(wfState.getUuid());
            }
        }
    }

    /**
     * Blocking execution: build the workflow graph, run it synchronously,
     * and return the final output as a JSON response.
     */
    public Map<String, Object> blockingRun(User user, List<ObjectNode> userInputs) {
        this.user = user;
        this.runStartedMillis = System.currentTimeMillis();
        // Create a dummy sseUuid with a dummy emitter and immediately mark it completed,
        // so all SSE send operations in runNode silently skip (COMPLETED_SSE cache check)
        this.sseUuid = "blocking-" + com.moyz.adi.common.util.UuidUtil.createShort();
        SseEmitter dummyEmitter = new SseEmitter(0L);
        SpringUtil.getBean(com.moyz.adi.common.helper.SseManager.class).register(this.sseUuid, dummyEmitter);
        sseManager.sendComplete(user.getId(), this.sseUuid);
        log.info("WorkflowEngine blockingRun,userId:{},workflowUuid:{},userInputs:{}", user.getId(), workflow.getUuid(), userInputs);
        if (!this.workflow.getIsEnable()) {
            throw new BaseException(ErrorEnum.A_WF_DISABLED);
        }

        Long workflowId = this.workflow.getId();
        this.wfRuntimeResp = workflowRuntimeService.create(user, workflowId);

        String runtimeUuid = this.wfRuntimeResp.getUuid();
        try {
            Pair<WorkflowNode, Set<WorkflowNode>> startAndEnds = findStartAndEndNode();
            WorkflowNode startNode = startAndEnds.getLeft();
            List<NodeIOData> wfInputs = getAndCheckUserInput(userInputs, startNode);
            this.wfState = new WfState(user, wfInputs, runtimeUuid);
            if (!wfState.getInterruptNodes().isEmpty()) {
                throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
            }
            workflowRuntimeService.updateInput(this.wfRuntimeResp.getId(), wfState);

            CompileNode rootCompileNode = new CompileNode();
            rootCompileNode.setId(startNode.getUuid());

            Map<String, Integer> nodeVisitCount = new HashMap<>();
            buildCompileNode(rootCompileNode, startNode, nodeVisitCount);

            StateGraph<WfNodeState> mainStateGraph = new StateGraph<>(stateSerializer);
            this.wfState.addEdge(START, startNode.getUuid());
            buildStateGraph(null, mainStateGraph, rootCompileNode);

            MemorySaver saver = new MemorySaver();
            CompileConfig compileConfig = CompileConfig.builder()
                    .checkpointSaver(saver)
                    .build();
            app = mainStateGraph.compile(compileConfig);
            RunnableConfig invokeConfig = RunnableConfig.builder().build();

            // Synchronous invoke instead of streaming
            app.invoke(Map.of(), invokeConfig);

            //Consume any streaming generators to ensure mapResult callbacks fire (e.g. token recording)
            //消费流式生成器以确保 mapResult 回调被触发（如 token 记录）
            for (StreamingChatGenerator<AgentState> generator : wfState.getNodeToStreamingGenerator().values()) {
                for (Object ignored : generator) {
                    // drain the generator to trigger mapResult
                }
            }

            // Collect output from the last completed node (end node or final node)
            List<AbstractWfNode> completedNodes = wfState.getCompletedNodes();
            if (!completedNodes.isEmpty()) {
                wfState.setOutput(completedNodes.get(completedNodes.size() - 1).getState().getOutputs());
            }

            WorkflowRuntime updatedRuntime = workflowRuntimeService.updateOutput(wfRuntimeResp.getId(), wfState, runStartedMillis, computeTokenSummary());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("task_id", runtimeUuid);
            data.put("status", "completed");
            if (null != updatedRuntime) {
                data.put("outputs", updatedRuntime.getOutput());
            }
            return data;
        } catch (Throwable e) {
            log.error("blockingRun execution exception, workflowUuid:{}", workflow.getUuid(), e);
            String errorMsg = e.getMessage();
            workflowRuntimeService.updateStatus(wfRuntimeResp.getId(), WORKFLOW_PROCESS_STATUS_FAIL, errorMsg, runStartedMillis, computeTokenSummary());

            Map<String, Object> errorResult = new LinkedHashMap<>();
            errorResult.put("message", errorMsg);
            return errorResult;
        }
    }

    private void errorWhenExe(Exception e) {
        log.error("error", e);
        String errorMsg = e.getMessage();
        if (errorMsg.contains("parallel node doesn't support conditional branch")) {
            errorMsg = "Parallel nodes cannot contain conditional branches";
        }
        // Persist terminal snapshot (incl. already-consumed tokens) as best-effort; the SSE error
        // MUST always reach the client even if persistence fails (DB unavailable, row gone, etc.).
        try {
            WorkflowRuntime failedRuntime = workflowRuntimeService.updateStatus(wfRuntimeResp.getId(), WORKFLOW_PROCESS_STATUS_FAIL, errorMsg, runStartedMillis, computeTokenSummary());
            pushRuntimeMetrics(failedRuntime);
        } catch (Throwable persistenceEx) {
            log.error("Failed to persist runtime failure status, sending SSE error anyway", persistenceEx);
        }
        sseManager.sendErrorAndComplete(user.getId(), sseUuid, errorMsg);
    }

    /**
     * Push the runtime's terminal metrics snapshot to the client via SSE, so the run list can
     * render stats for the just-finished run without a page refresh. No-op when no metrics have
     * been persisted yet.
     */
    /**
     * Aggregate input/output token totals from completed nodes' in-memory metrics, avoiding a DB
     * round-trip. Only LLM-typed nodes (llm, agent) contribute; other node types are skipped.
     */
    private WfRuntimeMetricsSummary computeTokenSummary() {
        long inputTokens = 0;
        long outputTokens = 0;
        long duration = 0;
        //wfState 可能在构图/输入校验阶段抛错时还未创建（如指定节点不存在），此时无已消费 token 可汇总
        if (null == wfState) {
            return new WfRuntimeMetricsSummary(0, 0, 0);
        }
        for (AbstractWfNode node : wfState.getCompletedNodes()) {
            NodeExecutionMetrics metrics = node.getState().getMetrics();
            if (metrics != null) {
                duration += metrics.getDurationMs();
                if (metrics instanceof LLMMetrics llm) {
                    if (llm.getInputTokens() != null) {
                        inputTokens += llm.getInputTokens();
                    }
                    if (llm.getOutputTokens() != null) {
                        outputTokens += llm.getOutputTokens();
                    }
                }
            }
        }
        return new WfRuntimeMetricsSummary(inputTokens, outputTokens, duration);
    }

    /**
     * Push the runtime's terminal metrics snapshot via SSE. The DB columns are NOT NULL DEFAULT 0
     * so a non-null runtime always has values; null safety is a guard against the caller passing a
     * runtime that was returned null by updateOutput/updateStatus (row concurrently deleted).
     */
    private void pushRuntimeMetrics(WorkflowRuntime runtime) {
        if (runtime == null) {
            return;
        }
        SseManager.parseAndSendPartialMsg(sseUuid, AdiConstant.SSEEventName.RUNTIME_METRICS,
                JsonUtil.toJson(Map.of("inputTokens", runtime.getInputTokens(),
                        "outputTokens", runtime.getOutputTokens(),
                        "duration", runtime.getDuration())));
    }

    private Map<String, Object> runNode(WorkflowNode wfNode, WfNodeState nodeState) {
        Map<String, Object> resultMap = new HashMap<>();
        //T9-07：错误分支接管需要节点实例/运行记录引用，声明提升到 try 外（工厂失败时为 null → 不接管，直接失败）
        AbstractWfNode abstractWfNode = null;
        WfRuntimeNodeDto runtimeNodeDto = null;
        try {
            WorkflowComponent wfComponent = components.stream().filter(item -> item.getId().equals(wfNode.getWorkflowComponentId())).findFirst().orElseThrow();
            abstractWfNode = WfNodeFactory.create(wfComponent, wfNode, wfState, nodeState);
            //节点实例
            runtimeNodeDto = workflowRuntimeNodeService.createByState(user, wfNode.getId(), wfRuntimeResp.getId(), nodeState);
            //lambda 捕获要求实际最终变量：catch 路径复用的可变引用在 try 内定稿后以 final 别名交给回调
            final WfRuntimeNodeDto runtimeNodeDtoRef = runtimeNodeDto;
            wfState.getRuntimeNodes().add(runtimeNodeDtoRef);

            SseManager.parseAndSendPartialMsg(sseUuid, "[NODE_RUN_" + wfNode.getUuid() + "]", JsonUtil.toJson(runtimeNodeDtoRef));

            NodeProcessResult processResult = abstractWfNode.process((is) -> {
                workflowRuntimeNodeService.updateInput(runtimeNodeDtoRef.getId(), nodeState);
                for (NodeIOData input : nodeState.getInputs()) {
                    SseManager.parseAndSendPartialMsg(sseUuid, "[NODE_INPUT_" + wfNode.getUuid() + "]", JsonUtil.toJson(input));
                }
            }, (is) -> {
                workflowRuntimeNodeService.updateOutput(runtimeNodeDtoRef.getId(), nodeState);

                //并行节点内部的节点执行结束后，需要主动向客户端发送输出结果
                String nodeUuid = wfNode.getUuid();
                List<NodeIOData> nodeOutputs = nodeState.getOutputs();
                for (NodeIOData output : nodeOutputs) {
                    log.info("callback node:{},output:{}", nodeUuid, output.getContent());
                    SseManager.parseAndSendPartialMsg(sseUuid, "[NODE_OUTPUT_" + nodeUuid + "]", JsonUtil.toJson(output));
                }
                //推送可观测指标（流式节点由 streamingResult 统一推送，因为 token 数据在流完成后才可用）
                //Push observability metrics (streaming nodes are handled by streamingResult since token data is only available after stream completes)
                if (nodeState.getMetrics() != null && nodeState.getMetrics().getDurationMs() > 0
                        && !wfState.getNodeToStreamingGenerator().containsKey(nodeUuid)) {
                    SseManager.parseAndSendPartialMsg(sseUuid, AdiConstant.SSEEventName.NODE_METRICS_PREFIX + nodeUuid + "]", JsonUtil.toJson(nodeState.getMetrics()));
                }
            });
            if (StringUtils.isNotBlank(processResult.getNextNodeUuid())) {
                resultMap.put("next", processResult.getNextNodeUuid());
            } else if (nodeToErrorBranch.containsKey(wfNode.getUuid())) {
                //T9-07：配置了 on_error 边的节点被编译为条件路由（普通下游 + 错误分支），
                //langgraph 的条件边读取 state.next——普通节点不产出 next，这里补默认路由（正常下游）
                resultMap.put("next", getNormalDownstreamUuid(wfNode.getUuid()));
            }
        } catch (Exception e) {
            //T9-07：onError 错误分支接管——节点失败时若配置了 on_error 边且未被 fail-fast/开关禁用，
            //构造错误对象（节点/错误信息/输入）作为该节点输出，路由到错误分支而非中断整个工作流
            String errorTarget = nodeToErrorBranch.get(wfNode.getUuid());
            boolean failFast = null != wfNode.getNodeConfig() && wfNode.getNodeConfig().path("fail_fast").asBoolean(false);
            if (null != errorTarget && WfFeatureGate.errorBranchEnabled() && !failFast) {
                log.warn("Node {} failed, taking onError branch -> {}", wfNode.getUuid(), errorTarget, e);
                resultMap.putAll(takeOnErrorBranch(wfNode, abstractWfNode, nodeState, runtimeNodeDto, e));
                return resultMap;
            }
            log.error("Node run error", e);
            throw new BaseException(ErrorEnum.B_WF_RUN_ERROR);
        }
        resultMap.put("name", wfNode.getTitle());
//langgraph4j state data is not stored, only metadata is stored
        //langgraph4j state中的data不做数据存储，只存储元数据
        StreamingChatGenerator<AgentState> generator = wfState.getNodeToStreamingGenerator().get(wfNode.getUuid());
        if (null != generator) {
            resultMap.put("_streaming_messages", generator);
            return resultMap;
        }
        return resultMap;
    }

    /**
     * T9-07：onError 分支接管。失败节点不抛出中断，而是：
     * <ol>
     *   <li>在失败节点 state 上写错误输出：{@code error}（JSON 对象：node_uuid/node_title/
     *       error_message/inputs）与 {@code error_msg}（原始错误文本，沿用既有默认错误参数名）；</li>
     *   <li>把失败节点补进 completedNodes（带失败状态），错误分支节点的 refInput /
     *       getLatestOutputs 才能解析到错误对象；</li>
     *   <li>恢复 wfState 流程状态为执行中（AbstractWfNode.process 失败时已置 FAIL）；</li>
     *   <li>持久化失败节点输出并推送 SSE（运行明细能看到失败节点的错误对象）；</li>
     *   <li>路由到错误分支目标节点（langgraph 条件边读取 state.next）。</li>
     * </ol>
     */
    private Map<String, Object> takeOnErrorBranch(WorkflowNode wfNode, AbstractWfNode failedNode,
                                                  WfNodeState nodeState, WfRuntimeNodeDto runtimeNodeDto, Exception cause) {
        String errorMessage = rootMessage(cause);
        //错误对象：字段完整（节点标识/标题/错误信息/原始输入），下游可直接引用
        ObjectNode errorObj = JsonUtil.createObjectNode();
        errorObj.put("node_uuid", wfNode.getUuid());
        errorObj.put("node_title", wfNode.getTitle());
        errorObj.put("error_message", errorMessage);
        errorObj.put("inputs", JsonUtil.toJson(nodeState.getInputs()));
        List<NodeIOData> errorOutputs = List.of(
                NodeIOData.createByText("error", "错误对象", JsonUtil.toJson(errorObj)),
                NodeIOData.createByText(DEFAULT_ERROR_OUTPUT_PARAM_NAME, "错误信息", errorMessage));
        nodeState.getOutputs().clear();
        nodeState.getOutputs().addAll(errorOutputs);
        if (null != failedNode) {
            failedNode.getState().setOutputs(new ArrayList<>(errorOutputs));
            wfState.getCompletedNodes().add(failedNode);
        }
        wfState.setProcessStatus(WORKFLOW_PROCESS_STATUS_DOING);
        if (null != runtimeNodeDto) {
            try {
                workflowRuntimeNodeService.updateOutput(runtimeNodeDto.getId(), nodeState);
            } catch (Exception persistEx) {
                log.warn("onError branch: persist failed node output error:{}", persistEx.getMessage());
            }
            SseManager.parseAndSendPartialMsg(sseUuid, "[NODE_OUTPUT_" + wfNode.getUuid() + "]", JsonUtil.toJson(errorObj));
        }
        Map<String, Object> result = new HashMap<>();
        result.put("name", wfNode.getTitle());
        result.put("next", nodeToErrorBranch.get(wfNode.getUuid()));
        return result;
    }

    /** 取配置了 on_error 边的节点的唯一普通下游（构图阶段已校验恰好 1 条） */
    private String getNormalDownstreamUuid(String nodeUuid) {
        return wfEdges.stream()
                .filter(edge -> edge.getSourceNodeUuid().equals(nodeUuid) && !ON_ERROR_HANDLE.equals(edge.getSourceHandle()))
                .map(WorkflowEdge::getTargetNodeUuid)
                .findFirst()
                .orElse("");
    }

    /** 穿透包装异常取根因消息（AbstractWfNode.process 抛 RuntimeException(e)，message 为 toString） */
    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (null != cur.getCause() && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return StringUtils.defaultIfBlank(cur.getMessage(), String.valueOf(cur));
    }

    /**
     * 流式输出结果
     *
     * @param outputs    输出
     * @param sseUuid SSE 请求标识 / SSE request identifier
     */
    private void streamingResult(WfState wfState, AsyncGenerator<NodeOutput<WfNodeState>> outputs, String sseUuid) {
        for (NodeOutput<WfNodeState> out : outputs) {
            if (out instanceof StreamingOutput<WfNodeState> streamingOutput) {
                String node = streamingOutput.node();
                String chunk = streamingOutput.chunk();
                log.info("node:{},chunk:{}", node, streamingOutput.chunk());
                SseManager.parseAndSendPartialMsg(sseUuid, "[NODE_CHUNK_" + node + "]", chunk);
            } else {
                AbstractWfNode abstractWfNode = wfState.getCompletedNodes().stream().filter(item -> item.getNode().getUuid().endsWith(out.node())).findFirst().orElse(null);
                if (null != abstractWfNode) {
                    WfRuntimeNodeDto runtimeNodeDto = wfState.getRuntimeNodeByNodeUuid(out.node());
                    if (null != runtimeNodeDto) {
                        workflowRuntimeNodeService.updateOutput(runtimeNodeDto.getId(), abstractWfNode.getState());
                        wfState.setOutput(abstractWfNode.getState().getOutputs());
                        //流式节点完成时推送可观测指标（token 数据此时才可用）
                        //Push observability metrics when streaming node completes (token data available only now)
                        if (abstractWfNode.getState().getMetrics() != null && abstractWfNode.getState().getMetrics().getDurationMs() > 0) {
                            SseManager.parseAndSendPartialMsg(sseUuid, AdiConstant.SSEEventName.NODE_METRICS_PREFIX + out.node() + "]", JsonUtil.toJson(abstractWfNode.getState().getMetrics()));
                        }
                    } else {
                        log.warn("Can not find runtime node, node uuid:{}", out.node());
                    }
                } else {
                    log.warn("Can not find node state,node uuid:{}", out.node());
                }
            }
        }
    }

    /**
     * 校验用户输入并组装成工作流的输入
     *
     * @param userInputs 用户输入
     * @param startNode  开始节点定义
     * @return 正确的用户输入列表
     */
    private List<NodeIOData> getAndCheckUserInput(List<ObjectNode> userInputs, WorkflowNode startNode) {
        List<WfNodeIO> defList = startNode.getInputConfig().getUserInputs();
        List<NodeIOData> wfInputs = new ArrayList<>();
        for (WfNodeIO paramDefinition : defList) {
            String paramNameFromDef = paramDefinition.getName();
            boolean requiredParamMissing = paramDefinition.getRequired();
            for (ObjectNode userInput : userInputs) {
                NodeIOData nodeIOData = WfNodeIODataUtil.createNodeIOData(userInput);
                if (!paramNameFromDef.equalsIgnoreCase(nodeIOData.getName())) {
                    continue;
                }
                Integer dataType = nodeIOData.getContent().getType();
                if (null == dataType) {
                    throw new BaseException(A_WF_INPUT_INVALID);
                }
                requiredParamMissing = false;
                boolean valid = paramDefinition.checkValue(nodeIOData);
                if (!valid) {
                    log.error("Invalid user input, workflowId:{}", startNode.getWorkflowId());
                    throw new BaseException(ErrorEnum.A_WF_INPUT_INVALID);
                }
                wfInputs.add(nodeIOData);
            }
            if (requiredParamMissing) {
                log.error("Required parameter in flow definition not provided, name:{}", paramNameFromDef);
                throw new BaseException(A_WF_INPUT_MISSING);
            }
        }
        return wfInputs;
    }

    /**
     * 查找开始及结束节点 <br/>
     * 开始节点只能有一个，结束节点可能多个
     *
     * @return 开始节点及结束节点列表
     */
    public Pair<WorkflowNode, Set<WorkflowNode>> findStartAndEndNode() {
        WorkflowNode startNode = null;
        Set<WorkflowNode> endNodes = new HashSet<>();
        for (WorkflowNode node : wfNodes) {
            Optional<WorkflowComponent> wfComponent = components.stream().filter(item -> item.getId().equals(node.getWorkflowComponentId())).findFirst();
            if (wfComponent.isPresent() && WfComponentNameEnum.START.getName().equals(wfComponent.get().getName())) {
                if (null != startNode) {
                    throw new BaseException(ErrorEnum.A_WF_MULTIPLE_START_NODE);
                }
                startNode = node;
            } else if (wfComponent.isPresent() && WfComponentNameEnum.END.getName().equals(wfComponent.get().getName())) {
                endNodes.add(node);
            }
        }
        if (null == startNode) {
            log.error("No start node found, workflowId:{}", wfNodes.get(0).getWorkflowId());
            throw new BaseException(ErrorEnum.A_WF_START_NODE_NOT_FOUND);
        }
        //Find all end nodes
        wfNodes.forEach(item -> {
            String nodeUuid = item.getUuid();
            boolean source = false;
            boolean target = false;
            for (WorkflowEdge edgeDef : wfEdges) {
                if (edgeDef.getSourceNodeUuid().equals(nodeUuid)) {
                    source = true;
                } else if (edgeDef.getTargetNodeUuid().equals(nodeUuid)) {
                    target = true;
                }
            }
            if (!source && target) {
                endNodes.add(item);
            }
        });
        log.info("start node:{}", startNode);
        log.info("end nodes:{}", endNodes);
        if (endNodes.isEmpty()) {
            log.error("No end node found, workflowId:{}", startNode.getWorkflowId());
            throw new BaseException(A_WF_END_NODE_NOT_FOUND);
        }
        return Pair.of(startNode, endNodes);
    }

    private static final int MAX_NODE_VISITS = 10;

    /** T9-07：onError 错误分支边的 sourceHandle 约定值 */
    public static final String ON_ERROR_HANDLE = "on_error";

    private void buildCompileNode(
            CompileNode parentNode,
            WorkflowNode node,
            Map<String, Integer> nodeVisitCount) {
        int visits = nodeVisitCount.merge(node.getUuid(), 1, Integer::sum);
        if (visits > MAX_NODE_VISITS) {
            log.error("Node {} visited more than {} times, possible infinite loop in workflow graph", node.getUuid(), MAX_NODE_VISITS);
            throw new BaseException(ErrorEnum.B_WF_RUN_ERROR);
        }
        //T9-07：登记 onError 分支。约束：配置了 on_error 边的节点必须有且只有一条普通出边
        //（错误分支与并行扇出不兼容——条件边会破坏并行扇出语义），不满足时显式报错而非静默忽略
        List<String> errorTargets = wfEdges.stream()
                .filter(edge -> edge.getSourceNodeUuid().equals(node.getUuid()) && ON_ERROR_HANDLE.equals(edge.getSourceHandle()))
                .map(WorkflowEdge::getTargetNodeUuid)
                .toList();
        if (!errorTargets.isEmpty()) {
            long normalOutCount = wfEdges.stream()
                    .filter(edge -> edge.getSourceNodeUuid().equals(node.getUuid()) && !ON_ERROR_HANDLE.equals(edge.getSourceHandle()))
                    .count();
            if (normalOutCount != 1) {
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                        "node '" + node.getTitle() + "' has an on_error branch but " + normalOutCount
                                + " normal outgoing edges; exactly 1 is required (error branch is incompatible with parallel fan-out)");
            }
            nodeToErrorBranch.put(node.getUuid(), errorTargets.get(0));
            if (errorTargets.size() > 1) {
                log.warn("node '{}' has {} on_error edges, only the first target {} is used",
                        node.getTitle(), errorTargets.size(), errorTargets.get(0));
            }
        }
        log.info("buildByNode, parentNode:{}, node:{},title:{}", parentNode.getId(), node.getUuid(), node.getTitle());
        CompileNode newNode;
        List<String> upstreamNodeUuids = getUpstreamNodeUuids(node.getUuid());
        if (upstreamNodeUuids.isEmpty()) {
            log.error("Node {} has no upstream node", node.getUuid());
            newNode = parentNode;
        } else if (upstreamNodeUuids.size() == 1) {
            String upstreamUuid = upstreamNodeUuids.get(0);
            boolean pointToParallel = pointToParallelBranch(upstreamUuid);
            if (pointToParallel) {
                String rootId = node.getUuid();
                GraphCompileNode graphCompileNode = getOrCreateGraphCompileNode(rootId);
                appendToNextNodes(parentNode, graphCompileNode);
                newNode = graphCompileNode;
            } else if (parentNode instanceof GraphCompileNode graphCompileNode) {
                newNode = CompileNode.builder().id(node.getUuid()).conditional(false).nextNodes(new ArrayList<>()).build();
                graphCompileNode.appendToLeaf(newNode);
            } else {
                newNode = CompileNode.builder().id(node.getUuid()).conditional(false).nextNodes(new ArrayList<>()).build();
                appendToNextNodes(parentNode, newNode);
            }
        } else {
            newNode = CompileNode.builder().id(node.getUuid()).conditional(false).nextNodes(new ArrayList<>()).build();
            GraphCompileNode parallelBranch = nodeToParallelBranch.get(parentNode.getId());
            appendToNextNodes(Objects.requireNonNullElse(parallelBranch, parentNode), newNode);
        }

        if (null == newNode) {
            log.error("Node {} does not exist", node.getUuid());
            return;
        }
        List<String> downstreamUuids = getDownstreamNodeUuids(node.getUuid());
        for (String downstream : downstreamUuids) {
            Optional<WorkflowNode> n = wfNodes.stream().filter(item -> item.getUuid().equals(downstream)).findFirst();
            n.ifPresent(workflowNode -> buildCompileNode(newNode, workflowNode, nodeVisitCount));
        }
    }

    /**
     * 构建完整的stategraph
     *
     * @param upstreamCompileNode 上游节点
     * @param stateGraph          当前状态图
     * @param compileNode         当前节点
     * @throws GraphStateException 状态图异常
     */
    private void buildStateGraph(CompileNode upstreamCompileNode, StateGraph<WfNodeState> stateGraph, CompileNode compileNode) throws GraphStateException {
        log.info("buildStateGraph,upstreamCompileNode:{},node:{}", upstreamCompileNode, compileNode.getId());
        String stateGraphNodeUuid = compileNode.getId();
        if (null == upstreamCompileNode) {
            addNodeToStateGraph(stateGraph, stateGraphNodeUuid);
            addEdgeToStateGraph(stateGraph, START, compileNode.getId());
        } else {
            if (compileNode instanceof GraphCompileNode graphCompileNode) {
                String stateGraphId = graphCompileNode.getId();
                CompileNode root = graphCompileNode.getRoot();
                String rootId = root.getId();
                String existSubGraphId = rootToSubGraph.get(rootId);

                if (StringUtils.isBlank(existSubGraphId)) {
                    StateGraph<WfNodeState> subgraph = new StateGraph<>(stateSerializer);
                    addNodeToStateGraph(subgraph, rootId);
                    addEdgeToStateGraph(subgraph, START, rootId);
                    for (CompileNode child : root.getNextNodes()) {
                        buildStateGraph(root, subgraph, child);
                    }
                    addEdgeToStateGraph(subgraph, graphCompileNode.getTail().getId(), END);
                    stateGraph.addNode(stateGraphId, subgraph.compile());
                    rootToSubGraph.put(rootId, stateGraphId);

                    stateGraphNodeUuid = stateGraphId;
                } else {
                    stateGraphNodeUuid = existSubGraphId;
                }
            } else {
                addNodeToStateGraph(stateGraph, stateGraphNodeUuid);
            }

//ConditionalEdge creation is handled separately
            //ConditionalEdge 的创建另外处理
            if (Boolean.FALSE.equals(upstreamCompileNode.getConditional())) {
                addEdgeToStateGraph(stateGraph, upstreamCompileNode.getId(), stateGraphNodeUuid);
            }
        }
        List<CompileNode> nextNodes = compileNode.getNextNodes();
        if (nextNodes.size() > 1) {
            boolean conditional = nextNodes.stream().noneMatch(item -> item instanceof GraphCompileNode);
            compileNode.setConditional(conditional);
            for (CompileNode nextNode : nextNodes) {
                buildStateGraph(compileNode, stateGraph, nextNode);
            }
            //节点是"条件分支"或"分类"的情况下不支持并行执行，所以直接使用条件ConditionalEdge
            if (conditional) {
                List<String> targets = nextNodes.stream().map(CompileNode::getId).toList();
                Map<String, String> mappings = new HashMap<>();
                for (String target : targets) {
                    mappings.put(target, target);
                }
                stateGraph.addConditionalEdges(
                        stateGraphNodeUuid,
                        edge_async(state -> state.data().get("next").toString()),
                        mappings
                );
            }
        } else if (nextNodes.size() == 1) {
            for (CompileNode nextNode : nextNodes) {
                buildStateGraph(compileNode, stateGraph, nextNode);
            }
        } else {
            addEdgeToStateGraph(stateGraph, stateGraphNodeUuid, END);
        }
    }

    private GraphCompileNode getOrCreateGraphCompileNode(String rootId) {
        GraphCompileNode exist = nodeToParallelBranch.get(rootId);
        if (null == exist) {
            GraphCompileNode graphCompileNode = new GraphCompileNode();
            graphCompileNode.setId("parallel_" + rootId);
            graphCompileNode.setRoot(CompileNode.builder().id(rootId).conditional(false).nextNodes(new ArrayList<>()).build());
            nodeToParallelBranch.put(rootId, graphCompileNode);
            exist = graphCompileNode;
        }
        return exist;

    }

    private List<String> getUpstreamNodeUuids(String nodeUuid) {
        return this.wfEdges.stream()
                .filter(edge -> edge.getTargetNodeUuid().equals(nodeUuid))
                .map(WorkflowEdge::getSourceNodeUuid)
                .toList();
    }

    private List<String> getDownstreamNodeUuids(String nodeUuid) {
        return this.wfEdges.stream()
                .filter(edge -> edge.getSourceNodeUuid().equals(nodeUuid))
                .map(WorkflowEdge::getTargetNodeUuid)
                .toList();
    }

//Determine if node belongs to subgraph
    //判断节点是否属于子图
    private boolean pointToParallelBranch(String nodeUuid) {
        int edgeCount = 0;
        for (WorkflowEdge edge : this.wfEdges) {
            if (edge.getSourceNodeUuid().equals(nodeUuid) && StringUtils.isBlank(edge.getSourceHandle())) {
                edgeCount = edgeCount + 1;
            }
        }
        return edgeCount > 1;
    }

    /**
     * 添加节点到状态图
     *
     * @param stateGraph
     * @param stateGraphNodeUuid
     * @throws GraphStateException
     */
    private void addNodeToStateGraph(StateGraph<WfNodeState> stateGraph, String stateGraphNodeUuid) throws GraphStateException {
        List<StateGraph<WfNodeState>> stateGraphList = stateGraphNodes.computeIfAbsent(stateGraphNodeUuid, k -> new ArrayList<>());
        boolean exist = stateGraphList.stream().anyMatch(item -> item == stateGraph);
        if (exist) {
            log.info("state graph node exist,stateGraphNodeUuid:{}", stateGraphNodeUuid);
            return;
        }
        log.info("addNodeToStateGraph,node uuid:{}", stateGraphNodeUuid);
        WorkflowNode wfNode = getNodeByUuid(stateGraphNodeUuid);
        stateGraph.addNode(stateGraphNodeUuid, node_async((state) -> runNode(wfNode, state)));
        stateGraphList.add(stateGraph);

        //记录人机交互节点
        WorkflowComponent wfComponent = components.stream().filter(item -> item.getId().equals(wfNode.getWorkflowComponentId())).findFirst().orElseThrow();
        if (HUMAN_FEEDBACK.getName().equals(wfComponent.getName())) {
            this.wfState.addInterruptNode(stateGraphNodeUuid);
        }
    }

    private void addEdgeToStateGraph(StateGraph<WfNodeState> stateGraph, String source, String target) throws GraphStateException {
        String key = source + "_" + target;
        List<StateGraph<WfNodeState>> stateGraphList = stateGraphEdges.computeIfAbsent(key, k -> new ArrayList<>());
        boolean exist = stateGraphList.stream().anyMatch(item -> item == stateGraph);
        if (exist) {
            log.info("state graph edge exist,source:{},target:{}", source, target);
            return;
        }
        log.info("addEdgeToStateGraph,source:{},target:{}", source, target);
        stateGraph.addEdge(source, target);
        stateGraphList.add(stateGraph);
    }

    private WorkflowNode getNodeByUuid(String nodeUuid) {
        return wfNodes.stream()
                .filter(item -> item.getUuid().equals(nodeUuid))
                .findFirst()
                .orElseThrow(() -> new BaseException(ErrorEnum.A_WF_NODE_NOT_FOUND));
    }

    private void appendToNextNodes(CompileNode compileNode, CompileNode newNode) {
        boolean exist = compileNode.getNextNodes().stream().anyMatch(item -> item.getId().equals(newNode.getId()));
        if (!exist) {
            compileNode.getNextNodes().add(newNode);
        }

    }

    public CompiledGraph<WfNodeState> getApp() {
        return app;
    }
}
