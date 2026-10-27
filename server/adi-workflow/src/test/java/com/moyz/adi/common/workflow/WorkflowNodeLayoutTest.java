package com.moyz.adi.common.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import com.moyz.adi.common.dto.workflow.WfNodeDto;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9-08：Container 分组布局元数据单测。
 * 覆盖任务书两条验收：分组元数据存取 / 不改变执行顺序。
 */
class WorkflowNodeLayoutTest {

    private WorkflowRuntimeService runtimeService;
    private WorkflowRuntimeNodeService runtimeNodeService;
    private SseManager sseManager;

    @BeforeEach
    void setUp() {
        ApplicationContext context = WfNodeTestSupport.install();
        //真实实例：blockingRun 依赖 register/sendComplete 的「哑发射器静默」机制，mock 会绕过 entries 初始化
        sseManager = new SseManager();
        when(context.getBean(SseManager.class)).thenReturn(sseManager);
        runtimeService = mock(WorkflowRuntimeService.class);
        runtimeNodeService = mock(WorkflowRuntimeNodeService.class);
        when(runtimeService.create(any(User.class), anyLong())).thenAnswer(inv -> {
            com.moyz.adi.common.dto.workflow.WfRuntimeResp resp = new com.moyz.adi.common.dto.workflow.WfRuntimeResp();
            resp.setId(1L);
            return resp;
        });
        when(runtimeService.updateOutput(anyLong(), any(), anyLong(), any())).thenAnswer(inv -> {
            WorkflowRuntime runtime = new WorkflowRuntime();
            runtime.setId(1L);
            runtime.setOutput(JsonUtil.createObjectNode());
            return runtime;
        });
        when(runtimeNodeService.createByState(any(), anyLong(), anyLong(), any())).thenAnswer(inv -> {
            com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto dto = new com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto();
            dto.setId(inv.getArgument(1, Long.class));
            return dto;
        });
    }

    @AfterEach
    void tearDown() {
        WfNodeTestSupport.uninstall();
    }

    // ---------- 分组元数据存取 ----------

    @Test
    @DisplayName("layout jsonb 存取：JsonNodeTypeHandler 写读往返一致（Container 元数据不丢）")
    void layoutTypeHandlerRoundTrip() throws Exception {
        JsonNodeTypeHandler handler = new JsonNodeTypeHandler();
        ObjectNode layout = JsonUtil.createObjectNode();
        layout.put("container", "grp-1");
        layout.put("folded", true);
        layout.putArray("tags").add("a").add("b");

        //写侧：layout 序列化为 jsonb PGobject
        java.sql.PreparedStatement ps = mock(java.sql.PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, layout, org.apache.ibatis.type.JdbcType.JAVA_OBJECT);
        ArgumentCaptor<Object> pgCaptor = ArgumentCaptor.forClass(Object.class);
        verify(ps, org.mockito.Mockito.times(1)).setObject(org.mockito.ArgumentMatchers.eq(1), pgCaptor.capture());
        Object written = pgCaptor.getValue();
        assertTrue(written instanceof org.postgresql.util.PGobject, "layout 必须以 jsonb PGobject 写入");
        String json = ((org.postgresql.util.PGobject) written).getValue();
        assertTrue(json.contains("grp-1") && json.contains("folded"), json);

        //读侧：jsonb 文本还原为 ObjectNode（autoResultMap 回读路径）
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(rs.getString("layout")).thenReturn(json);
        ObjectNode readBack = (ObjectNode) handler.getNullableResult(rs, "layout");
        assertEquals("grp-1", readBack.get("container").asText());
        assertTrue(readBack.get("folded").asBoolean());
        assertEquals(2, readBack.get("tags").size());
    }

    @Test
    @DisplayName("layout 存取：DTO↔实体字段映射（BeanUtils copy + listDto 回读），layout 为 null 时安全")
    void layoutDtoEntityMapping() {
        WfNodeDto dto = new WfNodeDto();
        dto.setUuid("0123456789abcdef0123456789abcdef");
        dto.setTitle("n");
        dto.setWorkflowComponentId(1L);
        dto.setInputConfig(JsonUtil.createObjectNode());
        dto.setNodeConfig(JsonUtil.createObjectNode());
        dto.setPositionX(1.0);
        dto.setPositionY(2.0);
        ObjectNode layout = JsonUtil.createObjectNode();
        layout.put("container", "grp-1");
        dto.setLayout(layout);

        WorkflowNode entity = new WorkflowNode();
        org.springframework.beans.BeanUtils.copyProperties(dto, entity, "inputConfig");
        assertEquals("grp-1", entity.getLayout().get("container").asText(), "DTO layout 必须能复制到实体（保存路径）");

        //回读路径：实体 → DTO 同名字段复制（listDtoByWfId 走 MPPageUtil 同语义）
        WfNodeDto back = new WfNodeDto();
        org.springframework.beans.BeanUtils.copyProperties(entity, back);
        assertEquals("grp-1", back.getLayout().get("container").asText(), "实体 layout 必须能读回 DTO（铁律 3）");

        //null layout：实体字段为 null 时（insert 跳过该列，走 DB DEFAULT '{}'）
        WorkflowNode empty = new WorkflowNode();
        WfNodeDto emptyBack = new WfNodeDto();
        org.springframework.beans.BeanUtils.copyProperties(empty, emptyBack);
        assertEquals(null, emptyBack.getLayout());
    }

    // ---------- 不改变执行顺序 ----------

    @Test
    @DisplayName("分组元数据不影响执行语义：带 layout 的节点按原有拓扑顺序执行（Start→Template→End）")
    void layoutDoesNotChangeExecutionOrder() {
        List<Long> executedOrder = new ArrayList<>();
        when(runtimeNodeService.createByState(any(), anyLong(), anyLong(), any())).thenAnswer(inv -> {
            executedOrder.add(inv.getArgument(1, Long.class));
            com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto dto = new com.moyz.adi.common.dto.workflow.WfRuntimeNodeDto();
            dto.setId(inv.getArgument(1, Long.class));
            return dto;
        });

        WorkflowComponent startComp = component(1L, "Start");
        WorkflowComponent tplComp = component(2L, "Template");
        WorkflowComponent endComp = component(3L, "End");
        WorkflowNode start = node(1L, "start", 1L, "{}", null);
        WorkflowNode tpl = node(2L, "tpl", 2L, "{\"template\":\"echo\"}", containerLayout("grp-1"));
        WorkflowNode end = node(3L, "end", 3L, "{\"result\":\"done\"}", containerLayout("grp-1"));

        Workflow workflow = new Workflow();
        workflow.setId(9L);
        workflow.setUuid("wf-layout");
        workflow.setIsEnable(true);
        WorkflowEngine engine = new WorkflowEngine(workflow, sseManager,
                List.of(startComp, tplComp, endComp), List.of(start, tpl, end),
                List.of(edge("start", "tpl"), edge("tpl", "end")),
                runtimeService, runtimeNodeService,
                new WfNodeProvider(WfNodeDeps.empty()));

        ObjectNode userInput = JsonUtil.createObjectNode();
        userInput.put("name", "q");
        ObjectNode content = userInput.putObject("content");
        content.put("type", 1);
        content.put("value", "hi");
        Map<String, Object> result = engine.blockingRun(WfNodeTestSupport.user(1L), new ArrayList<>(List.of(userInput)));

        assertEquals("completed", result.get("status"));
        assertEquals(List.of(1L, 2L, 3L), executedOrder, "layout 元数据不得改变节点执行顺序");
    }

    // ---------- 辅助 ----------

    private static WorkflowComponent component(Long id, String name) {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(id);
        component.setName(name);
        return component;
    }

    private static WorkflowNode node(Long id, String uuid, Long componentId, String configJson, ObjectNode layout) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setUuid(uuid);
        node.setTitle(uuid);
        node.setWorkflowComponentId(componentId);
        node.setNodeConfig((ObjectNode) JsonUtil.toJsonNode(configJson));
        node.setLayout(layout);
        WfNodeInputConfig inputConfig = new WfNodeInputConfig();
        inputConfig.setUserInputs(List.of());
        inputConfig.setRefInputs(List.of());
        node.setInputConfig(inputConfig);
        return node;
    }

    private static ObjectNode containerLayout(String containerUuid) {
        ObjectNode layout = JsonUtil.createObjectNode();
        layout.put("container", containerUuid);
        layout.put("folded", false);
        return layout;
    }

    private static WorkflowEdge edge(String source, String target) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setSourceNodeUuid(source);
        edge.setTargetNodeUuid(target);
        return edge;
    }
}
