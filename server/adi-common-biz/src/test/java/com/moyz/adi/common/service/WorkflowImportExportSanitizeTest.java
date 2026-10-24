package com.moyz.adi.common.service;

import com.moyz.adi.common.dto.workflow.WfEdgeReq;
import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WfNodeDto;
import com.moyz.adi.common.dto.workflow.WorkflowResp;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.WorkflowExportSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T9-06：导出脱敏 + 导入未知节点标记 + 名字冲突 单元测试。
 */
class WorkflowImportExportSanitizeTest {

    private WorkflowService workflowService;
    private WorkflowMapper workflowMapper;
    private WorkflowNodeService workflowNodeService;
    private WorkflowEdgeService workflowEdgeService;
    private WorkflowComponentService workflowComponentService;

    @BeforeEach
    void setUp() {
        MpTableInfoTestSupport.initTableInfo(com.moyz.adi.common.entity.Workflow.class);
        workflowService = new WorkflowService();
        workflowMapper = mock(WorkflowMapper.class);
        workflowNodeService = mock(WorkflowNodeService.class);
        workflowEdgeService = mock(WorkflowEdgeService.class);
        workflowComponentService = mock(WorkflowComponentService.class);

        ReflectionTestUtils.setField(workflowService, "baseMapper", workflowMapper);
        ReflectionTestUtils.setField(workflowService, "workflowNodeService", workflowNodeService);
        ReflectionTestUtils.setField(workflowService, "workflowEdgeService", workflowEdgeService);
        ReflectionTestUtils.setField(workflowService, "workflowComponentService", workflowComponentService);
        ReflectionTestUtils.setField(workflowService, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(workflowService, "redisTemplateUtil", mock(com.moyz.adi.common.util.RedisTemplateUtil.class));
        ReflectionTestUtils.setField(workflowService, "knowledgeBaseService", mock(KnowledgeBaseService.class));
        ReflectionTestUtils.setField(workflowService, "workflowVersionService", mock(WorkflowVersionService.class));

        com.moyz.adi.common.base.ThreadContext.setCurrentUser(new com.moyz.adi.common.entity.User() {{
            setId(42L);
        }});
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        com.moyz.adi.common.base.ThreadContext.unload();
    }

    private WorkflowComponent startComponent() {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(1L);
        component.setName("Start");
        return component;
    }

    private WorkflowComponent retrieverComponent() {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(7L);
        component.setName("KnowledgeRetrieval");
        return component;
    }

    private WfNodeDto node(String uuid, long componentId, String componentName) {
        WfNodeDto node = new WfNodeDto();
        node.setUuid(uuid);
        node.setTitle("n-" + uuid);
        node.setWorkflowComponentId(componentId);
        node.setComponentName(componentName);
        node.setInputConfig(JsonUtil.createObjectNode());
        node.setNodeConfig(JsonUtil.createObjectNode());
        node.setPositionX(0.0);
        node.setPositionY(0.0);
        return node;
    }

    // ---------- 导出脱敏 ----------

    @Test
    @DisplayName("导出脱敏：api_key/password/secret/token 键递归剥离，credentialUuid 置空")
    void exportStripsSensitiveFields() {
        WfNodeDto mailNode = node("n1", 1L, "Start");
        mailNode.setCredentialUuid("cred-uuid-1");
        var config = JsonUtil.createObjectNode();
        var sender = config.putObject("sender");
        sender.put("name", "bot");
        sender.put("password", "PLAINTEXT-PASSWORD");
        config.put("api_key", "sk-xxx");
        var nested = config.putObject("nested");
        nested.put("secret", "s");
        nested.put("keep", "v");
        mailNode.setNodeConfig(config);
        WfExportResp resp = new WfExportResp();
        resp.setTitle("t");
        resp.setNodes(List.of(mailNode));

        WorkflowExportSanitizer.sanitize(resp);

        assertNull(resp.getNodes().get(0).getCredentialUuid(), "凭据库引用必须剥离");
        assertFalse(resp.getNodes().get(0).getNodeConfig().has("api_key"));
        assertFalse(resp.getNodes().get(0).getNodeConfig().get("sender").has("password"), "MailSend 明文密码必须剥离");
        assertFalse(resp.getNodes().get(0).getNodeConfig().get("nested").has("secret"), "嵌套层敏感键也必须剥离");
        assertTrue(resp.getNodes().get(0).getNodeConfig().get("sender").has("name"));
        assertTrue(resp.getNodes().get(0).getNodeConfig().get("nested").has("keep"), "非敏感键保留");
    }

    @Test
    @DisplayName("导出脱敏：业务引用不误伤（knowledge_base_uuid 保留）")
    void exportKeepsBusinessReferences() {
        WfNodeDto retriever = node("n1", 7L, "KnowledgeRetrieval");
        retriever.setNodeConfig(JsonUtil.getObjectMapper().valueToTree(JsonUtil.createObjectNode()
                .put("knowledge_base_uuid", "kb-abc")));
        WfExportResp resp = new WfExportResp();
        resp.setNodes(List.of(retriever));

        WorkflowExportSanitizer.sanitize(resp);

        assertEquals("kb-abc", resp.getNodes().get(0).getNodeConfig().get("knowledge_base_uuid").asText(),
                "知识包引用是业务字段，必须原样导出（导入按 uuid 校验）");
    }

    @Test
    @DisplayName("export() 出口脱敏：listDtoByWfId 返回带明文密码的节点时，返回体已剥离")
    void exportEndpointSanitizes() {
        com.moyz.adi.common.entity.Workflow workflow = new com.moyz.adi.common.entity.Workflow();
        workflow.setId(100L);
        workflow.setUuid("w000000000000000000000000000001");
        workflow.setTitle("t");
        //PrivilegeUtil 依赖 query() + ThreadContext
        when(workflowMapper.selectOne(any())).thenReturn(workflow);
        when(workflowNodeService.listDtoByWfId(anyLong())).thenReturn(List.of(node("n1", 7L, "KnowledgeRetrieval")));
        when(workflowEdgeService.listDtoByWfId(anyLong())).thenReturn(List.of());
        when(workflowComponentService.getAllEnable()).thenReturn(List.of(retrieverComponent()));

        WfExportResp resp = workflowService.export("w000000000000000000000000000001");

        assertNull(resp.getNodes().get(0).getCredentialUuid());
    }

    // ---------- 导入：未知节点标记 ----------

    @Test
    @DisplayName("导入未知组件：节点保留并标记（_wf_unknown_component + title 前缀 + START 透传），不阻断导入")
    void importMarksUnknownComponentNode() {
        WfNodeDto unknown = node("n1", 999L, "FutureComponent");
        WfNodeDto normal = node("n2", 7L, "KnowledgeRetrieval");
        WfExportResp req = new WfExportResp();
        req.setTitle("客服流");
        req.setNodes(List.of(unknown, normal));
        when(workflowComponentService.getAllEnable()).thenReturn(List.of(retrieverComponent(), startComponent()));
        when(workflowComponentService.getStartComponent()).thenReturn(startComponent());
        when(workflowMapper.selectCount(any())).thenReturn(0L);
        when(workflowMapper.insert(any(com.moyz.adi.common.entity.Workflow.class))).thenAnswer(inv -> {
            inv.getArgument(0, com.moyz.adi.common.entity.Workflow.class).setId(1L);
            return 1;
        });
        when(workflowNodeService.listDtoByWfId(anyLong())).thenReturn(List.of());
        when(workflowEdgeService.listDtoByWfId(anyLong())).thenReturn(List.of());

        WorkflowResp resp = workflowService.importWorkflow(req);

        assertEquals("客服流", resp.getTitle());
        @SuppressWarnings("unchecked")
        var captor = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(workflowNodeService).createOrUpdateNodes(anyLong(), captor.capture());
        List<WfNodeDto> saved = captor.getValue();
        assertEquals(2, saved.size());
        WfNodeDto marked = saved.get(0);
        assertEquals("FutureComponent", marked.getNodeConfig().get("_wf_unknown_component").asText(),
                "未知组件必须写进 nodeConfig 标记");
        assertTrue(marked.getTitle().startsWith("[未知组件]"), "title 必须带未知组件前缀");
        assertEquals(1L, marked.getWorkflowComponentId(), "重映射为 START 组件（透传，引擎可加载）");
        //正常节点不受影响
        assertEquals(7L, saved.get(1).getWorkflowComponentId());
    }

    // ---------- 名字冲突 ----------

    @Test
    @DisplayName("模板导入同名冲突：自动加 -imported 后缀")
    void importDuplicateTitleGetsSuffix() {
        WfNodeDto normal = node("n1", 7L, "KnowledgeRetrieval");
        WfExportResp req = new WfExportResp();
        req.setTitle("客服流");
        req.setNodes(List.of(normal));
        when(workflowComponentService.getAllEnable()).thenReturn(List.of(retrieverComponent(), startComponent()));
        when(workflowComponentService.getStartComponent()).thenReturn(startComponent());
        //第一次查重命中同名（1），-imported 查重未命中（0）
        when(workflowMapper.selectCount(any())).thenReturn(1L, 0L);
        when(workflowMapper.insert(any(com.moyz.adi.common.entity.Workflow.class))).thenAnswer(inv -> {
            inv.getArgument(0, com.moyz.adi.common.entity.Workflow.class).setId(2L);
            return 1;
        });
        when(workflowNodeService.listDtoByWfId(anyLong())).thenReturn(List.of());
        when(workflowEdgeService.listDtoByWfId(anyLong())).thenReturn(List.of());

        WorkflowResp resp = workflowService.importWorkflow(req);

        assertEquals("客服流-imported", resp.getTitle());
    }

    @Test
    @DisplayName("导出→导入往返：脱敏后的 JSON round-trip 结构一致（节点/边数不变）")
    void sanitizedExportRoundTrips() {
        WfNodeDto mailNode = node("n1", 1L, "Start");
        mailNode.setNodeConfig(JsonUtil.getObjectMapper().valueToTree(JsonUtil.createObjectNode()
                .put("password", "x").put("keep", "v")));
        WfExportResp exported = new WfExportResp();
        exported.setTitle("t");
        exported.setNodes(List.of(mailNode));
        exported.setEdges(List.of(new WfEdgeReq()));
        WorkflowExportSanitizer.sanitize(exported);

        String json = JsonUtil.toJson(exported);
        WfExportResp imported = JsonUtil.fromJson(json, WfExportResp.class);

        assertEquals(1, imported.getNodes().size());
        assertEquals(1, imported.getEdges().size());
        assertFalse(imported.getNodes().get(0).getNodeConfig().has("password"));
        assertEquals("v", imported.getNodes().get(0).getNodeConfig().get("keep").asText());
    }
}
