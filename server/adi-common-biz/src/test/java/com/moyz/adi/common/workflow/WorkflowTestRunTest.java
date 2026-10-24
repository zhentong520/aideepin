package com.moyz.adi.common.workflow;

import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowEdge;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.entity.WorkflowRuntime;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowRunMapper;
import com.moyz.adi.common.service.WorkflowNodeService;
import com.moyz.adi.common.service.WorkflowRuntimeNodeService;
import com.moyz.adi.common.service.WorkflowRuntimeService;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_UPSTREAM_CONTEXT_MISSING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T3-10：单节点试跑后端单元测试。
 * 覆盖：指定节点起跑的上游上下文门禁、历史上下文种子注入 refInput 解析、
 * 试跑记录 is_test 标记（不污染正式记录的持久化基础）。
 */
class WorkflowTestRunTest {

    @BeforeEach
    void setUp() {
        //BaseException 带参文案经 MessageFormat 还原，mock MessageSource 返回带 {0} 的真实模板
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return "A_WF_UPSTREAM_CONTEXT_MISSING".equals(key)
                    ? "节点「{0}」的上游变量不可用：请先完整运行一次工作流"
                    : key;
        });
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    // ---------- 上游上下文门禁（WorkflowStarter.requireUpstreamContext） ----------

    @Test
    @DisplayName("起跑节点无上游：无需历史上下文，直接放行（空上下文）")
    void noUpstreamPassesWithoutHistory() {
        Map<String, List<NodeIOData>> result = WorkflowStarter.requireUpstreamContext(
                false, nodeWith("n1", "起始"), Map.of());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("起跑节点有上游且历史上下文存在：原样透传")
    void upstreamWithHistoryPassesThrough() {
        Map<String, List<NodeIOData>> loaded = Map.of("up", List.of(NodeIOData.createByText("output", "o", "v")));

        Map<String, List<NodeIOData>> result = WorkflowStarter.requireUpstreamContext(
                true, nodeWith("n1", "下游节点"), loaded);

        assertSame(loaded, result);
    }

    @Test
    @DisplayName("起跑节点有上游但无历史成功上下文：明确报错 A_WF_UPSTREAM_CONTEXT_MISSING，不静默跑")
    void upstreamWithoutHistoryThrowsExplicitly() {
        BaseException e = assertThrows(BaseException.class,
                () -> WorkflowStarter.requireUpstreamContext(true, nodeWith("n1", "下游节点"), Map.of()));

        assertEquals(A_WF_UPSTREAM_CONTEXT_MISSING.getCode(), e.getCode());
        assertTrue(e.getInfo().contains("下游节点"), "错误信息应指明是哪个节点缺上游变量，实际：" + e.getInfo());
    }

    // ---------- 历史上下文种子注入（WorkflowEngine.seedUpstreamContext） ----------

    @Test
    @DisplayName("种子注入：历史输出可被起跑节点的 refInput 解析命中（getIOByNodeUuid）")
    void seededContextResolvableByRefInput() throws Exception {
        WorkflowComponent templateComponent = new WorkflowComponent();
        templateComponent.setId(1L);
        templateComponent.setName("Template");
        WorkflowNode seedNode = new WorkflowNode();
        seedNode.setUuid("http-node");
        seedNode.setTitle("HTTP");
        seedNode.setWorkflowComponentId(1L);

        WorkflowEngine engine = new WorkflowEngine(new Workflow(), null,
                List.of(templateComponent), List.of(seedNode), List.of(), null, null);
        WfState wfState = new WfState(user(1L), List.of(), "rt-uuid");
        ReflectionTestUtils.setField(engine, "wfState", wfState);

        engine.seedUpstreamContext(Map.of(
                "http-node", List.of(NodeIOData.createByText("output", "响应内容", "{\"data\":{\"id\":9}}"))
        ));

        //起跑节点的 refInput（node_uuid=http-node, node_param_name=output）必须能命中种子输出
        List<NodeIOData> resolved = wfState.getIOByNodeUuid("http-node");
        assertEquals(1, resolved.size());
        assertEquals("output", resolved.get(0).getName());
        assertEquals("{\"data\":{\"id\":9}}", resolved.get(0).valueToString());
        //getLatestOutputs（initInput 用）也必须命中
        assertEquals("output", wfState.getLatestOutputs().get(0).getName());
    }

    @Test
    @DisplayName("种子注入：未知节点/组件的上下文条目被跳过，不炸构图")
    void seededContextSkipsUnknownNodes() throws Exception {
        WorkflowComponent templateComponent = new WorkflowComponent();
        templateComponent.setId(1L);
        templateComponent.setName("Template");
        WorkflowNode seedNode = new WorkflowNode();
        seedNode.setUuid("known-node");
        seedNode.setWorkflowComponentId(1L);

        WorkflowEngine engine = new WorkflowEngine(new Workflow(), null,
                List.of(templateComponent), List.of(seedNode), List.of(), null, null);
        WfState wfState = new WfState(user(1L), List.of(), "rt-uuid");
        ReflectionTestUtils.setField(engine, "wfState", wfState);

        Map<String, List<NodeIOData>> context = new HashMap<>();
        context.put("known-node", List.of(NodeIOData.createByText("output", "o", "v")));
        context.put("ghost-node", List.of(NodeIOData.createByText("output", "o", "v2")));

        engine.seedUpstreamContext(context);

        assertEquals(1, wfState.getCompletedNodes().size());
        assertTrue(wfState.getIOByNodeUuid("ghost-node").isEmpty());
    }

    // ---------- 试跑记录 is_test 标记（不污染正式记录） ----------

    @Test
    @DisplayName("试跑创建运行记录：is_test=true 落库；正式运行默认 is_test=false")
    void testRunFlagPersisted() {
        WorkflowRunMapper mapper = mock(WorkflowRunMapper.class);
        WorkflowRuntimeService service = new WorkflowRuntimeService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.insert(any(WorkflowRuntime.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, WorkflowRuntime.class).setId(99L);
            return 1;
        });
        when(mapper.selectById(99L)).thenAnswer(invocation -> {
            WorkflowRuntime runtime = new WorkflowRuntime();
            runtime.setId(99L);
            runtime.setIsTest(true);
            return runtime;
        });

        service.create(user(1L), 5L, true);

        ArgumentCaptor<WorkflowRuntime> captor = ArgumentCaptor.forClass(WorkflowRuntime.class);
        verify(mapper).insert(captor.capture());
        assertEquals(Boolean.TRUE, captor.getValue().getIsTest(), "试跑记录必须带 is_test=true 标记");

        //默认 create（正式路径）必须 is_test=false
        when(mapper.selectById(99L)).thenAnswer(invocation -> {
            WorkflowRuntime runtime = new WorkflowRuntime();
            runtime.setId(99L);
            runtime.setIsTest(false);
            return runtime;
        });
        service.create(user(1L), 5L);
        ArgumentCaptor<WorkflowRuntime> captor2 = ArgumentCaptor.forClass(WorkflowRuntime.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor2.capture());
        List<WorkflowRuntime> all = captor2.getAllValues();
        assertEquals(Boolean.FALSE, all.get(1).getIsTest(), "正式运行记录不得误标 is_test");
    }

    // ---------- 工具 ----------

    @Test
    @DisplayName("历史上下文解析：运行时输出 jsonb 条目还原为 NodeIOData（含类型分派）")
    void runtimeOutputEntryParsed() {
        com.fasterxml.jackson.databind.node.ObjectNode content = com.moyz.adi.common.util.JsonUtil.createObjectNode();
        content.put("type", 1);
        content.put("title", "响应内容");
        content.put("value", "hello");

        NodeIOData data = WfNodeIODataUtil.createNodeIODataFromRuntimeEntry("output", content);

        assertEquals("output", data.getName());
        assertEquals("hello", data.valueToString());
        //缺 value 的脏条目必须返回 null（跳过），不抛异常
        com.fasterxml.jackson.databind.node.ObjectNode dirty = com.moyz.adi.common.util.JsonUtil.createObjectNode();
        dirty.put("type", 1);
        assertTrue(null == WfNodeIODataUtil.createNodeIODataFromRuntimeEntry("x", dirty));
    }

    @Test
    @DisplayName("loadLastSuccessContext：无历史成功记录时返回空 Map（触发显式报错而非静默跑）")
    void emptyHistoryReturnsEmptyMap() {
        WorkflowRunMapper mapper = mock(WorkflowRunMapper.class);
        WorkflowRuntimeService service = new WorkflowRuntimeService();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        ReflectionTestUtils.setField(service, "workflowRuntimeNodeService", mock(WorkflowRuntimeNodeService.class));
        ReflectionTestUtils.setField(service, "workflowNodeService", mock(WorkflowNodeService.class));
        when(mapper.selectOne(any())).thenReturn(null);

        Map<String, List<NodeIOData>> result = service.loadLastSuccessContext(1L, 2L);

        assertTrue(result.isEmpty());
    }

    private static WorkflowNode nodeWith(String uuid, String title) {
        WorkflowNode node = new WorkflowNode();
        node.setUuid(uuid);
        node.setTitle(title);
        return node;
    }
}
