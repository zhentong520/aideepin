package com.moyz.adi.common.service;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WfNodeDto;
import com.moyz.adi.common.dto.workflow.WorkflowResp;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_IMPORT_KB_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T3-14：工作流导入导出 kbUuid 校验单元测试（mock mapper/服务，不启 Spring）。
 */
class WorkflowImportExportTest {

    private WorkflowService workflowService;
    private WorkflowMapper workflowMapper;
    private WorkflowNodeService workflowNodeService;
    private WorkflowEdgeService workflowEdgeService;
    private WorkflowComponentService workflowComponentService;
    private KnowledgeBaseService knowledgeBaseService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService();
        workflowMapper = mock(WorkflowMapper.class);
        workflowNodeService = mock(WorkflowNodeService.class);
        workflowEdgeService = mock(WorkflowEdgeService.class);
        workflowComponentService = mock(WorkflowComponentService.class);
        knowledgeBaseService = mock(KnowledgeBaseService.class);
        userService = mock(UserService.class);

        ReflectionTestUtils.setField(workflowService, "baseMapper", workflowMapper);
        ReflectionTestUtils.setField(workflowService, "workflowNodeService", workflowNodeService);
        ReflectionTestUtils.setField(workflowService, "workflowEdgeService", workflowEdgeService);
        ReflectionTestUtils.setField(workflowService, "workflowComponentService", workflowComponentService);
        ReflectionTestUtils.setField(workflowService, "knowledgeBaseService", knowledgeBaseService);
        ReflectionTestUtils.setField(workflowService, "userService", userService);

        WorkflowComponent retriever = new WorkflowComponent();
        retriever.setId(7L);
        retriever.setName("KnowledgeRetrieval");
        when(workflowComponentService.getAllEnable()).thenReturn(List.of(retriever));

        //BaseException 带参文案经 MessageFormat 还原，mock MessageSource 返回带 {0} 的真实模板
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return "A_WF_IMPORT_KB_NOT_FOUND".equals(key)
                    ? "知识包不存在：{0}。导入已中止，请先导入或创建对应知识包"
                    : key;
        });
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);

        ThreadContext.setCurrentUser(user(42L));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        return user;
    }

    private static WfNodeDto retrieverNode(String uuid, String kbUuid) {
        WfNodeDto node = new WfNodeDto();
        node.setUuid(uuid);
        node.setTitle("知识检索-" + uuid);
        node.setWorkflowComponentId(7L);
        node.setComponentName("KnowledgeRetrieval");
        node.setInputConfig(JsonUtil.createObjectNode());
        node.setNodeConfig(JsonUtil.getObjectMapper().valueToTree(
                JsonUtil.createObjectNode().put("knowledge_base_uuid", kbUuid)));
        return node;
    }

    @Test
    @DisplayName("导入缺失 kbUuid：报错且错误体列出全部缺失 uuid（不静默丢节点）")
    void importWithMissingKbUuidsFailsAndListsAllMissing() {
        WfExportResp req = new WfExportResp();
        req.setTitle("客服流");
        req.setNodes(List.of(
                retrieverNode("n1", "kb-exists"),
                retrieverNode("n2", "kb-missing-1"),
                retrieverNode("n3", "kb-missing-2")
        ));
        when(knowledgeBaseService.filterExistingUuids(any())).thenReturn(List.of("kb-exists"));

        BaseException e = assertThrows(BaseException.class, () -> workflowService.importWorkflow(req));

        assertEquals(A_WF_IMPORT_KB_NOT_FOUND.getCode(), e.getCode());
        //错误体必须列出全部缺失 uuid，一个都不能少
        assertTrue(e.getInfo().contains("kb-missing-1"), "错误体应含 kb-missing-1，实际：" + e.getInfo());
        assertTrue(e.getInfo().contains("kb-missing-2"), "错误体应含 kb-missing-2，实际：" + e.getInfo());
        //校验先于任何写入：工作流行都不允许插入
        verify(workflowMapper, times(0)).insert(any(Workflow.class));
    }

    @Test
    @DisplayName("导入成功：节点引用的 kbUuid 原样保留（不被重写/丢弃）")
    void importSuccessKeepsKbReference() {
        WfExportResp req = new WfExportResp();
        req.setTitle("客服流");
        req.setRemark("r");
        req.setNodes(List.of(retrieverNode("n1", "kb-exists")));
        when(knowledgeBaseService.filterExistingUuids(any())).thenReturn(List.of("kb-exists"));
        when(workflowMapper.insert(any(Workflow.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Workflow.class).setId(1L);
            return 1;
        });
        when(workflowMapper.selectCount(any())).thenReturn(0L);
        when(workflowNodeService.listDtoByWfId(anyLong())).thenReturn(List.of());
        when(workflowEdgeService.listDtoByWfId(anyLong())).thenReturn(List.of());

        workflowService.importWorkflow(req);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WfNodeDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(workflowNodeService).createOrUpdateNodes(anyLong(), captor.capture());
        List<WfNodeDto> saved = captor.getValue();
        assertEquals(1, saved.size());
        assertEquals("n1", saved.get(0).getUuid());
        assertEquals("kb-exists", saved.get(0).getNodeConfig().get("knowledge_base_uuid").asText(),
                "导入后节点的知识包引用必须与导出时完全一致");
    }

    @Test
    @DisplayName("round-trip：导出 JSON → 反序列化 → 导入，节点 uuid 与 kbUuid 引用逐项一致")
    void exportImportRoundTripKeepsReference() {
        WfNodeDto source = retrieverNode("n1", "kb-abc");
        WfExportResp exported = new WfExportResp();
        exported.setTitle("客服流");
        exported.setNodes(List.of(source));

        //模拟「导出 → 传输 → 导入」的 JSON round-trip
        String json = JsonUtil.toJson(exported);
        WfExportResp imported = JsonUtil.fromJson(json, WfExportResp.class);

        when(knowledgeBaseService.filterExistingUuids(any())).thenReturn(List.of("kb-abc"));
        when(workflowMapper.insert(any(Workflow.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Workflow.class).setId(2L);
            return 1;
        });
        when(workflowMapper.selectCount(any())).thenReturn(0L);
        when(workflowNodeService.listDtoByWfId(anyLong())).thenReturn(List.of());
        when(workflowEdgeService.listDtoByWfId(anyLong())).thenReturn(List.of());

        WorkflowResp resp = workflowService.importWorkflow(imported);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WfNodeDto>> captor = ArgumentCaptor.forClass(List.class);
        verify(workflowNodeService).createOrUpdateNodes(anyLong(), captor.capture());
        WfNodeDto saved = captor.getValue().get(0);
        assertEquals("n1", saved.getUuid(), "round-trip 后节点 uuid 不得改变");
        assertEquals("kb-abc", saved.getNodeConfig().get("knowledge_base_uuid").asText(),
                "round-trip 后知识包引用不得改变");
        assertEquals("客服流", resp.getTitle());
    }

    @Test
    @DisplayName("同名工作流导入：新建副本，名字加 -imported 后缀")
    void duplicateTitleImportsAsSuffixedCopy() {
        WfExportResp req = new WfExportResp();
        req.setTitle("客服流");
        req.setNodes(List.of(retrieverNode("n1", "kb-exists")));
        when(knowledgeBaseService.filterExistingUuids(any())).thenReturn(List.of("kb-exists"));
        //第一次查重命中同名（返回 1），-imported 后缀查重未命中（返回 0）
        when(workflowMapper.selectCount(any())).thenReturn(1L, 0L);
        when(workflowMapper.insert(any(Workflow.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, Workflow.class).setId(3L);
            return 1;
        });
        when(workflowNodeService.listDtoByWfId(anyLong())).thenReturn(List.of());
        when(workflowEdgeService.listDtoByWfId(anyLong())).thenReturn(List.of());

        WorkflowResp resp = workflowService.importWorkflow(req);

        assertEquals("客服流-imported", resp.getTitle(), "同名导入必须新建副本并加后缀");
        ArgumentCaptor<Workflow> captor = ArgumentCaptor.forClass(Workflow.class);
        verify(workflowMapper).insert(captor.capture());
        assertEquals("客服流-imported", captor.getValue().getTitle());
        //副本是全新的 uuid，不与源工作流冲突
        assertTrue(!captor.getValue().getUuid().isBlank());
    }
}
