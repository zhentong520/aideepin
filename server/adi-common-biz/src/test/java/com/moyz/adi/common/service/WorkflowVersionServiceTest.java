package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.workflow.WfEdgeReq;
import com.moyz.adi.common.dto.workflow.WfNodeDto;
import com.moyz.adi.common.dto.workflow.WfVersionDto;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowVersion;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowEdgeMapper;
import com.moyz.adi.common.mapper.WorkflowMapper;
import com.moyz.adi.common.mapper.WorkflowNodeMapper;
import com.moyz.adi.common.mapper.WorkflowVersionMapper;
import com.moyz.adi.common.util.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VERSION_DISABLED;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VERSION_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9-05：工作流版本快照单元测试（mock mapper/服务，不启 Spring）。
 * 覆盖：快照生成（版本号/节点边数/完整定义 JSON）、回滚生成新版本且历史不被覆盖、
 * 并发保存冲突重试、diff 概要、保留策略、开关关闭。
 */
class WorkflowVersionServiceTest {

    private WorkflowVersionService service;
    private WorkflowVersionMapper versionMapper;
    private WorkflowService workflowService;
    private WorkflowNodeService workflowNodeService;
    private WorkflowEdgeService workflowEdgeService;
    private WorkflowComponentService workflowComponentService;
    private WorkflowMapper workflowMapper;
    private AdiProperties adiProperties;

    @BeforeEach
    void setUp() {
        MpTableInfoTestSupport.initTableInfo(
                com.moyz.adi.common.entity.WorkflowVersion.class,
                com.moyz.adi.common.entity.WorkflowNode.class,
                com.moyz.adi.common.entity.WorkflowEdge.class,
                com.moyz.adi.common.entity.Workflow.class);
        service = new WorkflowVersionService();
        versionMapper = mock(WorkflowVersionMapper.class);
        workflowService = mock(WorkflowService.class);
        workflowNodeService = mock(WorkflowNodeService.class);
        workflowEdgeService = mock(WorkflowEdgeService.class);
        workflowComponentService = mock(WorkflowComponentService.class);
        workflowMapper = mock(WorkflowMapper.class);
        adiProperties = new AdiProperties();

        ReflectionTestUtils.setField(service, "baseMapper", versionMapper);
        ReflectionTestUtils.setField(service, "workflowService", workflowService);
        ReflectionTestUtils.setField(service, "workflowNodeService", workflowNodeService);
        ReflectionTestUtils.setField(service, "workflowEdgeService", workflowEdgeService);
        ReflectionTestUtils.setField(service, "workflowComponentService", workflowComponentService);
        ReflectionTestUtils.setField(service, "userService", mock(UserService.class));
        ReflectionTestUtils.setField(service, "adiProperties", adiProperties);
        //snapshot() 经 @Lazy self 代理调用 insertSnapshot，纯 mock 单测里注入自身
        ReflectionTestUtils.setField(service, "self", service);
    }

    @AfterEach
    void tearDown() {
        com.moyz.adi.common.base.ThreadContext.unload();
    }

    private Workflow workflow() {
        Workflow workflow = new Workflow();
        workflow.setId(100L);
        workflow.setUuid("w000000000000000000000000000001");
        workflow.setTitle("客服流");
        workflow.setRemark("r");
        return workflow;
    }

    private WfNodeDto node(String uuid, long componentId) {
        WfNodeDto node = new WfNodeDto();
        node.setUuid(uuid);
        node.setTitle("n-" + uuid);
        node.setWorkflowComponentId(componentId);
        node.setInputConfig(JsonUtil.createObjectNode());
        node.setNodeConfig(JsonUtil.createObjectNode());
        node.setPositionX(0.0);
        node.setPositionY(0.0);
        return node;
    }

    private WorkflowComponent retrieverComponent() {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(7L);
        component.setName("KnowledgeRetrieval");
        return component;
    }

    private WorkflowVersion versionRow(int versionNo, ObjectNode definition, int nodes, int edges) {
        WorkflowVersion version = new WorkflowVersion();
        version.setId((long) versionNo);
        version.setWorkflowId(100L);
        version.setVersionNo(versionNo);
        version.setNodeCount(nodes);
        version.setEdgeCount(edges);
        version.setDefinition(definition);
        return version;
    }

    // ---------- 快照生成 ----------

    @Test
    @DisplayName("快照生成：version_no = 当前最大+1，definition 含完整 nodes/edges，修改人/节点/边数落列")
    void snapshotInsertsWithMaxPlusOne() {
        when(versionMapper.selectOne(any())).thenReturn(versionRow(3, JsonUtil.createObjectNode(), 1, 1));
        when(workflowService.getById(100L)).thenReturn(workflow());
        when(workflowNodeService.listDtoByWfId(100L)).thenReturn(List.of(node("n1", 1L), node("n2", 1L)));
        when(workflowEdgeService.listDtoByWfId(100L)).thenReturn(List.of(new WfEdgeReq()));
        when(workflowComponentService.getAllEnable()).thenReturn(List.of());

        WorkflowVersion created = service.snapshot(100L, 42L, "");

        assertEquals(4, created.getVersionNo(), "版本号必须 = 当前最大 + 1");
        assertEquals(2, created.getNodeCount());
        assertEquals(1, created.getEdgeCount());
        assertEquals(42L, created.getModifierUserId());
        ObjectNode definition = created.getDefinition();
        assertEquals(2, definition.get("nodes").size(), "definition 必须含全部节点");
        assertEquals(1, definition.get("edges").size(), "definition 必须含全部边");
        assertEquals("客服流", definition.get("title").asText());
        verify(versionMapper).insert(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("并发保存：唯一键冲突重试递增版本号（v4 撞号 → v5），历史行不被覆盖")
    void concurrentSaveRetriesWithIncrementedVersionNo() {
        when(versionMapper.selectOne(any())).thenReturn(versionRow(3, JsonUtil.createObjectNode(), 1, 0));
        when(workflowService.getById(100L)).thenReturn(workflow());
        when(workflowNodeService.listDtoByWfId(100L)).thenReturn(List.of(node("n1", 1L)));
        when(workflowEdgeService.listDtoByWfId(100L)).thenReturn(List.of());
        when(workflowComponentService.getAllEnable()).thenReturn(List.of());
        //第一次 insert 撞唯一键（并发保存同号），第二次成功
        when(versionMapper.insert(any(WorkflowVersion.class)))
                .thenThrow(new DuplicateKeyException("uk_workflow_version_no"))
                .thenReturn(1);

        WorkflowVersion created = service.insertSnapshot(100L, JsonUtil.createObjectNode(), 1, 0, 42L, "");

        assertEquals(5, created.getVersionNo(), "撞号后必须 +1 重试，绝不覆盖 version_no=4 的历史行");
        ArgumentCaptor<WorkflowVersion> captor = ArgumentCaptor.forClass(WorkflowVersion.class);
        verify(versionMapper, atLeastOnce()).insert(captor.capture());
        List<WorkflowVersion> inserted = captor.getAllValues();
        assertEquals(4, inserted.get(0).getVersionNo());
        assertEquals(5, inserted.get(1).getVersionNo());
        //历史不被覆盖：只有 insert，没有任何对旧版本的 update
        verify(versionMapper, never()).updateById(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("保留策略：retention-limit=1 时超出上限的最旧版本被软删")
    void retentionDeletesOldestBeyondLimit() {
        adiProperties.getWf().setVersionRetentionLimit(1);
        WorkflowVersion v3 = versionRow(3, JsonUtil.createObjectNode(), 1, 0);
        WorkflowVersion v2 = versionRow(2, JsonUtil.createObjectNode(), 1, 0);
        when(versionMapper.selectOne(any())).thenReturn(null);
        when(versionMapper.selectList(any())).thenReturn(List.of(v3, v2));

        service.applyRetention(100L);

        //软删走 lambdaUpdateChain → mapper.update(null, wrapper)：2 个版本 + limit=1
        //只允许恰好 1 次软删调用（超出上限的最旧 v2），v3 保留——若多删必有第 2 次调用
        verify(versionMapper, org.mockito.Mockito.times(1))
                .update(org.mockito.Mockito.isNull(), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        verify(versionMapper, never()).updateById(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("开关关闭（adi.wf.version-enabled=false）：保存不生成快照")
    void snapshotSkippedWhenDisabled() {
        adiProperties.getWf().setVersionEnabled(false);
        assertNull(service.snapshot(100L, 42L, ""));
        verify(versionMapper, never()).insert(any(WorkflowVersion.class));
    }

    // ---------- 回滚 ----------

    @Test
    @DisplayName("回滚：软删当前节点/边 → 按快照重建 → 生成新版本（v2→回滚 → 新版本 v3），历史行不被覆盖")
    void rollbackReplaysAndCreatesNewVersion() {
        com.moyz.adi.common.entity.User modifier = new com.moyz.adi.common.entity.User();
        modifier.setId(42L);
        com.moyz.adi.common.base.ThreadContext.setCurrentUser(modifier);
        //definition：v2 时代的 1 节点 0 边（WfExportResp 结构，组件按名重映射）
        WfNodeDto snapshotNode = node("n1", 7L);
        snapshotNode.setComponentName("KnowledgeRetrieval");
        com.moyz.adi.common.dto.workflow.WfExportResp definitionDto = new com.moyz.adi.common.dto.workflow.WfExportResp();
        definitionDto.setTitle("客服流");
        definitionDto.setRemark("r");
        definitionDto.setNodes(List.of(snapshotNode));
        definitionDto.setEdges(List.of());
        ObjectNode def = (ObjectNode) JsonUtil.classToJsonNode(definitionDto);
        WorkflowVersion target = versionRow(2, def, 1, 0);
        when(versionMapper.selectOne(any())).thenReturn(target);
        when(workflowComponentService.getAllEnable()).thenReturn(List.of(retrieverComponent()));
        WorkflowNodeMapper nodeMapper = mock(WorkflowNodeMapper.class);
        WorkflowEdgeMapper edgeMapper = mock(WorkflowEdgeMapper.class);
        when(workflowNodeService.getBaseMapper()).thenReturn(nodeMapper);
        when(workflowEdgeService.getBaseMapper()).thenReturn(edgeMapper);
        when(workflowService.getBaseMapper()).thenReturn(workflowMapper);
        //回滚后的快照链路
        when(workflowService.getById(100L)).thenReturn(workflow());
        when(workflowNodeService.listDtoByWfId(100L)).thenReturn(List.of(snapshotNode));
        when(workflowEdgeService.listDtoByWfId(100L)).thenReturn(List.of());
        when(versionMapper.insert(any(WorkflowVersion.class))).thenReturn(1);

        WfVersionDto dto = service.doRollback(workflow(), 2);

        //1) 当前节点/边被软删
        verify(nodeMapper).update(any(), any());
        verify(edgeMapper).update(any(), any());
        //2) 按快照重建（内容来自 v2 definition）
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WfNodeDto>> nodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(workflowNodeService).createOrUpdateNodes(org.mockito.ArgumentMatchers.eq(100L), nodesCaptor.capture());
        assertEquals("n1", nodesCaptor.getValue().get(0).getUuid());
        //3) 回滚本身生成新版本（version_no = 快照当前最大 2 + 1 = 3），不覆盖历史
        assertEquals(3, dto.getVersionNo());
        assertEquals("rollback from v2", dto.getRemark());
        assertTrue(dto.getRollback());
        verify(versionMapper, never()).updateById(any(WorkflowVersion.class));
    }

    @Test
    @DisplayName("回滚目标版本不存在 → A_WF_VERSION_NOT_FOUND")
    void rollbackVersionNotFound() {
        when(versionMapper.selectOne(any())).thenReturn(null);
        BaseException e = assertThrows(BaseException.class, () -> service.doRollback(workflow(), 99));
        assertEquals(A_WF_VERSION_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("开关关闭：rollback 拒绝（A_WF_VERSION_DISABLED），不做任何查询")
    void rollbackRefusedWhenDisabled() {
        adiProperties.getWf().setVersionEnabled(false);
        BaseException e = assertThrows(BaseException.class, () -> service.rollback("w000000000000000000000000000001", 2));
        assertEquals(A_WF_VERSION_DISABLED.getCode(), e.getCode());
        verify(versionMapper, never()).selectOne(any());
    }

    // ---------- diff 概要 ----------

    @Test
    @DisplayName("diff 概要：nodeDiff/edgeDiff = 本版本 - 上一版本")
    void fillDiffComputesDeltaAgainstPreviousVersion() {
        when(versionMapper.selectOne(any())).thenReturn(versionRow(4, JsonUtil.createObjectNode(), 3, 2));
        WfVersionDto dto = new WfVersionDto();
        dto.setVersionNo(5);
        dto.setNodeCount(2);
        dto.setEdgeCount(2);

        service.fillDiff(100L, List.of(dto));

        assertEquals(-1, dto.getNodeDiff());
        assertEquals(0, dto.getEdgeDiff());
        assertTrue(!dto.getRollback());
    }
}
