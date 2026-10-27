package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.mapper.KbDocumentMapper;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import com.moyz.adi.common.service.embedding.IKnowledgeEmbeddingService;
import com.moyz.adi.common.util.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.InOrder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1-15：知识库删除级联（DB 内一次事务 + 事务外尽力而为远程清理）编排测试。
 */
class KnowledgeBaseServiceSoftDeleteCascadeTest {

    private KnowledgeBaseService knowledgeBaseService;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private KbDocumentService kbDocumentService;
    private IndexTaskService indexTaskService;
    private DocumentSegmentService documentSegmentService;
    private KbShareService kbShareService;
    private KnowledgeBaseStarService knowledgeBaseStarRecordService;
    private KbAuditLogService kbAuditLogService;
    private IKnowledgeEmbeddingService iKnowledgeEmbeddingService;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService();
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        kbDocumentService = mock(KbDocumentService.class);
        indexTaskService = mock(IndexTaskService.class);
        documentSegmentService = mock(DocumentSegmentService.class);
        kbShareService = mock(KbShareService.class);
        knowledgeBaseStarRecordService = mock(KnowledgeBaseStarService.class);
        kbAuditLogService = mock(KbAuditLogService.class);
        iKnowledgeEmbeddingService = mock(IKnowledgeEmbeddingService.class);

        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);

        // 级联里的 lambdaUpdateChain.set(SFunction,...) 即时解析 lambda 列，需初始化列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeBase.class);
        TableInfoHelper.initTableInfo(assistant, KbDocument.class);

        ReflectionTestUtils.setField(knowledgeBaseService, "baseMapper", knowledgeBaseMapper);
        // 单测无 AOP 代理，直接以自身充当 self（@Transactional 不生效，仅验证编排）
        ReflectionTestUtils.setField(knowledgeBaseService, "self", knowledgeBaseService);
        ReflectionTestUtils.setField(knowledgeBaseService, "kbDocumentService", kbDocumentService);
        ReflectionTestUtils.setField(knowledgeBaseService, "indexTaskService", indexTaskService);
        ReflectionTestUtils.setField(knowledgeBaseService, "documentSegmentService", documentSegmentService);
        ReflectionTestUtils.setField(knowledgeBaseService, "kbShareService", kbShareService);
        ReflectionTestUtils.setField(knowledgeBaseService, "knowledgeBaseStarRecordService", knowledgeBaseStarRecordService);
        ReflectionTestUtils.setField(knowledgeBaseService, "kbAuditLogService", kbAuditLogService);
        ReflectionTestUtils.setField(knowledgeBaseService, "iKnowledgeEmbeddingService", iKnowledgeEmbeddingService);

        when(kbDocumentService.getBaseMapper()).thenReturn(mock(KbDocumentMapper.class));

        ThreadContext.setCurrentUser(user(true));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    @Test
    void softDeleteCascadesEverythingThenCleansRemoteStores() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb());
        when(knowledgeBaseMapper.update(any(), any())).thenReturn(1);

        assertTrue(knowledgeBaseService.softDelete("kb-uuid"));

        // DB 级联顺序：取消索引任务 → 软删段落 → 删分享 → 软删收藏（文档批量软删在中间，经 mapper 完成）
        InOrder cascadeOrder = inOrder(indexTaskService, documentSegmentService, kbShareService, knowledgeBaseStarRecordService);
        cascadeOrder.verify(indexTaskService).cancelByKb("kb-uuid");
        cascadeOrder.verify(documentSegmentService).deleteByKbUuid("kb-uuid");
        cascadeOrder.verify(kbShareService).deleteByKb("kb-uuid");
        cascadeOrder.verify(knowledgeBaseStarRecordService).softDeleteByKb("kb-uuid");

        // 事务外远程清理：向量一次性删 + 图谱足迹逐文档删
        verify(iKnowledgeEmbeddingService).deleteByKbUuid("kb-uuid");
        verify(kbDocumentService).removeGraphFootprintsByKb("kb-uuid");

        // 审计落一条 KB_DELETE
        verify(kbAuditLogService).record(eq("kb-uuid"), eq("My KB"), eq(KbAuditLogService.ACTION_KB_DELETE),
                eq(KbAuditLogService.TARGET_KB), eq("kb-uuid"), eq(true), eq("cascade=true"));
    }

    @Test
    void softDeleteReturnsFalseWhenKbRowUpdateMisses() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb());
        // KB 行更新影响行数为 0（已删 / 并发删），级联不应继续
        when(knowledgeBaseMapper.update(any(), any())).thenReturn(0);

        assertFalse(knowledgeBaseService.softDelete("kb-uuid"));

        verify(indexTaskService, never()).cancelByKb(anyString());
        verify(documentSegmentService, never()).deleteByKbUuid(anyString());
        verify(kbShareService, never()).deleteByKb(anyString());
        verify(knowledgeBaseStarRecordService, never()).softDeleteByKb(anyString());
        verify(iKnowledgeEmbeddingService, never()).deleteByKbUuid(anyString());
    }

    @Test
    void softDeleteSucceedsEvenWhenVectorCleanupFails() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb());
        when(knowledgeBaseMapper.update(any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("pgvector down")).when(iKnowledgeEmbeddingService).deleteByKbUuid("kb-uuid");

        assertTrue(knowledgeBaseService.softDelete("kb-uuid"));

        // 向量清理失败不阻断主流程，残留由每日对账 job 收敛；审计仍要落
        verify(kbAuditLogService).record(eq("kb-uuid"), anyString(), eq(KbAuditLogService.ACTION_KB_DELETE),
                eq(KbAuditLogService.TARGET_KB), eq("kb-uuid"), eq(true), anyString());
    }

    @Test
    void softDeleteSucceedsEvenWhenGraphFootprintCleanupFails() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb());
        when(knowledgeBaseMapper.update(any(), any())).thenReturn(1);
        doThrow(new IllegalStateException("graph db down")).when(kbDocumentService).removeGraphFootprintsByKb("kb-uuid");

        assertTrue(knowledgeBaseService.softDelete("kb-uuid"));

        verify(iKnowledgeEmbeddingService).deleteByKbUuid("kb-uuid");
        verify(kbAuditLogService).record(eq("kb-uuid"), anyString(), eq(KbAuditLogService.ACTION_KB_DELETE),
                eq(KbAuditLogService.TARGET_KB), eq("kb-uuid"), eq(true), anyString());
    }

    private KnowledgeBase kb() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid("kb-uuid");
        kb.setTitle("My KB");
        kb.setIsDeleted(false);
        return kb;
    }

    private User user(boolean admin) {
        User user = new User();
        user.setId(42L);
        user.setIsAdmin(admin);
        return user;
    }
}
