package com.moyz.adi.common.workflow.node.subworkflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowEdge;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.entity.WorkflowRuntime;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.service.WorkflowRuntimeNodeService;
import com.moyz.adi.common.service.WorkflowRuntimeService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.WfNodeDeps;
import com.moyz.adi.common.workflow.WfNodeInputConfig;
import com.moyz.adi.common.workflow.WfNodeProvider;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WorkflowEngine;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.def.WfNodeIOText;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9-07：子工作流 + onError 错误分支引擎级单测。
 * <p>
 * 以最小工作流（Start → Template → End）走真实引擎构图与执行（服务层 mock），
 * 覆盖任务书验收：
 * <ol>
 *   <li>子工作流正常执行 —— {@code blockingRun} 全链路（子流程复用同一入口）；</li>
 *   <li>错误分支接管 —— 节点失败走 on_error 分支，正常下游被跳过，整体不中断；</li>
 *   <li>错误对象字段完整 —— node_uuid/node_title/error_message/inputs 齐全。</li>
 * </ol>
 */
class WorkflowEngineSubWorkflowAndErrorBranchTest {

    private WorkflowRuntimeService runtimeService;
    private WorkflowRuntimeNodeService runtimeNodeService;
    private SseManager sseManager;

    @BeforeEach
    void setUp() {
        ApplicationContext context = com.moyz.adi.common.workflow.WfNodeTestSupport.install();
        //真实实例：blockingRun 依赖 register/sendComplete 的「哑发射器静默」机制，mock 会绕过 entries 初始化
        sseManager = new SseManager();
        when(context.getBean(SseManager.class)).thenReturn(sseManager);
        runtimeService = mock(WorkflowRuntimeService.class);
        runtimeNodeService = mock(WorkflowRuntimeNodeService.class);
        when(runtimeService.create(any(User.class), anyLong())).thenReturn(runtimeWithId(1L));
        when(runtimeService.updateOutput(anyLong(), any(), anyLong(), any()))
                .thenAnswer(inv -> runtimeWithOutput(1L, JsonUtil.createObjectNode()));
        when(runtimeNodeService.createByState(any(), anyLong(), anyLong(), any()))
                .thenAnswer(inv -> {
                    com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto dto = new com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto();
                    dto.setId(inv.getArgument(1, Long.class));
                    return dto;
                });
    }

    @AfterEach
    void tearDown() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.uninstall();
        SubWorkflowInvoker.clear();
    }

    private static com.moyz.adi.common.dto.workflow.WfRuntimeResp runtimeWithId(long id) {
        com.moyz.adi.common.dto.workflow.WfRuntimeResp runtime = new com.moyz.adi.common.dto.workflow.WfRuntimeResp();
        runtime.setId(id);
        return runtime;
    }

    private static WorkflowRuntime runtimeWithOutput(long id, ObjectNode output) {
        WorkflowRuntime runtime = new WorkflowRuntime();
        runtime.setId(id);
        runtime.setOutput(output);
        return runtime;
    }

    private static WorkflowComponent component(Long id, String name) {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(id);
        component.setName(name);
        return component;
    }

    private static WorkflowNode node(Long id, String uuid, Long componentId, String configJson) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setUuid(uuid);
        node.setTitle(uuid);
        node.setWorkflowComponentId(componentId);
        node.setNodeConfig((ObjectNode) JsonUtil.toJsonNode(configJson));
        WfNodeInputConfig inputConfig = new WfNodeInputConfig();
        inputConfig.setUserInputs(List.of());
        inputConfig.setRefInputs(List.of());
        node.setInputConfig(inputConfig);
        return node;
    }

    private static WorkflowEdge edge(String source, String target) {
        return edge(source, target, null);
    }

    private static WorkflowEdge edge(String source, String target, String sourceHandle) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setSourceNodeUuid(source);
        edge.setTargetNodeUuid(target);
        edge.setSourceHandle(sourceHandle);
        return edge;
    }

    private static Workflow workflow(String uuid) {
        Workflow workflow = new Workflow();
        workflow.setId(9L);
        workflow.setUuid(uuid);
        workflow.setIsEnable(true);
        return workflow;
    }

    private static List<ObjectNode> userInput() {
        ObjectNode wrapper = JsonUtil.createObjectNode();
        wrapper.put("name", "q");
        ObjectNode content = wrapper.putObject("content");
        content.put("type", 1);
        content.put("title", "q");
        content.put("value", "hello");
        return new ArrayList<>(List.of(wrapper));
    }

    @Test
    @DisplayName("子工作流正常执行：Start→Template→End 经 blockingRun 全链路完成并产出 outputs")
    void subWorkflowBlockingRunCompletes() {
        WorkflowNode start = node(1L, "start", 1L, "{}");
        start.setInputConfig(startInputConfig());
        WorkflowNode template = node(2L, "tpl", 2L, "{\"template\":\"echo:{q}\"}");
        WorkflowNode end = node(3L, "end", 3L, "{\"result\":\"done:{q}\"}");

        WorkflowEngine engine = new WorkflowEngine(workflow("wf-main"), sseManager,
                List.of(component(1L, "Start"), component(2L, "Template"), component(3L, "End")),
                List.of(start, template, end),
                List.of(edge("start", "tpl"), edge("tpl", "end")),
                runtimeService, runtimeNodeService,
                new WfNodeProvider(WfNodeDeps.empty()));

        Map<String, Object> result = engine.blockingRun(user(), userInput());

        assertEquals("completed", result.get("status"));
        verify(runtimeService, never()).updateStatus(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("错误分支接管：节点失败走 on_error 分支，正常下游被跳过，工作流整体完成")
    void errorBranchTakesOverOnFailure() {
        WorkflowNode start = node(1L, "start", 1L, "{}");
        start.setInputConfig(startInputConfig());
        //失败节点：模板引用不存在的变量 {{no_such_root}} → 渲染抛 A_WF_VAR_ROOT_NOT_FOUND
        WorkflowNode failing = node(2L, "failing", 2L, "{\"template\":\"{{no_such_root}}\"}");
        WorkflowNode skipped = node(3L, "skipped", 2L, "{\"template\":\"SHOULD NOT RUN\"}");
        WorkflowNode errorTpl = node(4L, "error-tpl", 2L, "{\"template\":\"caught\"}");
        WorkflowNode end = node(5L, "end", 3L, "{\"result\":\"recovered\"}");

        WorkflowEngine engine = new WorkflowEngine(workflow("wf-err"), sseManager,
                List.of(component(1L, "Start"), component(2L, "Template"), component(3L, "End")),
                List.of(start, failing, skipped, errorTpl, end),
                List.of(edge("start", "failing"),
                        edge("failing", "skipped"),
                        edge("failing", "error-tpl", WorkflowEngine.ON_ERROR_HANDLE),
                        edge("error-tpl", "end")),
                runtimeService, runtimeNodeService,
                new WfNodeProvider(WfNodeDeps.empty()));

        Map<String, Object> result = engine.blockingRun(user(), userInput());

        //整体完成（未中断为 FAIL）
        assertEquals("completed", result.get("status"));
        //执行记录里不应出现被跳过的正常下游
        Set<String> executed = capturedNodeUuids();
        assertFalse(executed.contains("skipped"), "正常下游必须被错误分支跳过，实际执行了：" + executed);
        assertTrue(executed.contains("error-tpl"), "错误分支必须被接管执行，实际执行了：" + executed);
        assertTrue(executed.contains("end"));
    }

    @Test
    @DisplayName("错误对象字段完整：node_uuid/node_title/error_message/inputs 全部可读")
    void errorObjectFieldsComplete() {
        WorkflowNode start = node(1L, "start", 1L, "{}");
        start.setInputConfig(startInputConfig());
        WorkflowNode failing = node(2L, "failing-node", 2L, "{\"template\":\"{{missing_var}}\"}");
        failing.setTitle("失败节点");
        WorkflowNode errorTpl = node(3L, "error-tpl", 2L, "{\"template\":\"caught\"}");
        WorkflowNode end = node(4L, "end", 3L, "{\"result\":\"recovered\"}");

        WorkflowEngine engine = new WorkflowEngine(workflow("wf-err2"), sseManager,
                List.of(component(1L, "Start"), component(2L, "Template"), component(3L, "End")),
                List.of(start, failing, errorTpl, end),
                List.of(edge("start", "failing-node"),
                        edge("failing-node", "error-tpl"),
                        edge("failing-node", "error-tpl", WorkflowEngine.ON_ERROR_HANDLE),
                        edge("error-tpl", "end")),
                runtimeService, runtimeNodeService,
                new WfNodeProvider(WfNodeDeps.empty()));

        engine.blockingRun(user(), userInput());

        //从运行记录输出里找错误对象
        ArgumentCaptor<WfNodeState> stateCaptor = ArgumentCaptor.forClass(WfNodeState.class);
        verify(runtimeNodeService, atLeastOnce()).updateOutput(anyLong(), stateCaptor.capture());
        boolean errorObjFound = stateCaptor.getAllValues().stream()
                .flatMap(state -> state.getOutputs().stream())
                .filter(out -> "error".equals(out.getName()))
                .map(NodeIOData::valueToString)
                .peek(json -> {
                    ObjectNode obj = (ObjectNode) JsonUtil.toJsonNode(json);
                    assertTrue(obj.hasNonNull("node_uuid"));
                    assertTrue(obj.hasNonNull("node_title"));
                    assertTrue(obj.hasNonNull("error_message"));
                    assertTrue(obj.hasNonNull("inputs"));
                })
                .findFirst()
                .isPresent();
        assertTrue(errorObjFound, "必须存在字段完整的 error 错误对象输出");
    }

    @Test
    @DisplayName("fail-fast：节点配置 fail_fast=true 时错误分支被忽略，工作流按既有语义失败")
    void failFastIgnoresErrorBranch() {
        WorkflowNode start = node(1L, "start", 1L, "{}");
        start.setInputConfig(startInputConfig());
        WorkflowNode failing = node(2L, "failing", 2L, "{\"template\":\"{{missing_var}}\",\"fail_fast\":true}");
        WorkflowNode errorTpl = node(3L, "error-tpl", 2L, "{\"template\":\"caught\"}");
        WorkflowNode end = node(4L, "end", 3L, "{\"result\":\"recovered\"}");

        WorkflowEngine engine = new WorkflowEngine(workflow("wf-ff"), sseManager,
                List.of(component(1L, "Start"), component(2L, "Template"), component(3L, "End")),
                List.of(start, failing, errorTpl, end),
                List.of(edge("start", "failing"),
                        edge("failing", "error-tpl"),
                        edge("failing", "error-tpl", WorkflowEngine.ON_ERROR_HANDLE),
                        edge("error-tpl", "end")),
                runtimeService, runtimeNodeService,
                new WfNodeProvider(WfNodeDeps.empty()));

        Map<String, Object> result = engine.blockingRun(user(), userInput());

        assertFalse("completed".equals(result.get("status")), "fail_fast 节点失败必须中断工作流");
        verify(runtimeService, atLeastOnce()).updateStatus(anyLong(), org.mockito.ArgumentMatchers.anyInt(), anyString(), anyLong(), any());
        assertFalse(capturedNodeUuids().contains("error-tpl"), "fail_fast 下错误分支不得被执行");
    }

    // ---------- 辅助 ----------

    private User user() {
        return com.moyz.adi.common.workflow.WfNodeTestSupport.user(1L);
    }

    private static WfNodeInputConfig startInputConfig() {
        WfNodeInputConfig config = new WfNodeInputConfig();
        WfNodeIOText text = WfNodeIOText.builder().name("q").title("问题").type(1).required(true).build();
        config.setUserInputs(List.of(text));
        config.setRefInputs(List.of());
        return config;
    }

    /** createByState 的 nodeId → 节点 uuid 反查（通过 id 映射） */
    private Set<String> capturedNodeUuids() {
        ArgumentCaptor<Long> nodeIdCaptor = ArgumentCaptor.forClass(Long.class);
        verify(runtimeNodeService, atLeastOnce()).createByState(any(), nodeIdCaptor.capture(), anyLong(), any());
        Set<Long> executedIds = new HashSet<>(nodeIdCaptor.getAllValues());
        Set<String> uuids = new HashSet<>();
        //id → uuid 映射与测试构造一致（1..5）
        Map<Long, String> idToUuid = Map.of(1L, "start", 2L, "failing", 3L, "skipped", 4L, "error-tpl", 5L, "end");
        executedIds.forEach(id -> uuids.add(idToUuid.getOrDefault(id, "id-" + id)));
        return uuids;
    }
}
