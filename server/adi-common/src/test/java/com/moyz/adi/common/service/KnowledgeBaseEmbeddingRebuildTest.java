package com.moyz.adi.common.service;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.dto.KbEmbeddingRebuildResp;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import com.moyz.adi.common.service.embedding.EmbeddingProviderInfo;
import com.moyz.adi.common.service.embedding.EmbeddingProviderService;
import com.moyz.adi.common.util.SpringUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-05：按知识库重建 embedding 索引单测（provider 版本记录 + 回滚链路 + 开关 + 权限）。
 */
class KnowledgeBaseEmbeddingRebuildTest {

    private KnowledgeBaseService knowledgeBaseService;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private KbDocumentService kbDocumentService;
    private KbAuditLogService kbAuditLogService;
    private EmbeddingProviderService embeddingProviderService;
    private StringRedisTemplate stringRedisTemplate;
    private AdiProperties adiProperties;

    @BeforeEach
    void setUp() {
        knowledgeBaseService = new KnowledgeBaseService();
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        kbDocumentService = mock(KbDocumentService.class);
        kbAuditLogService = mock(KbAuditLogService.class);
        embeddingProviderService = mock(EmbeddingProviderService.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        SetOperations<String, String> setOperations = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        adiProperties = new AdiProperties();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(knowledgeBaseService, "baseMapper", knowledgeBaseMapper);
        ReflectionTestUtils.setField(knowledgeBaseService, "kbDocumentService", kbDocumentService);
        ReflectionTestUtils.setField(knowledgeBaseService, "kbAuditLogService", kbAuditLogService);
        ReflectionTestUtils.setField(knowledgeBaseService, "embeddingProviderService", embeddingProviderService);
        ReflectionTestUtils.setField(knowledgeBaseService, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(knowledgeBaseService, "adiProperties", adiProperties);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    @Test
    void rebuildRecordsProviderVersionAndEnqueuesAllDocs() {
        KnowledgeBase kb = kb("kb-1", "研发知识库");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(kbDocumentService.rebuildEmbeddingByKb("kb-1")).thenReturn(3);
        when(embeddingProviderService.currentProvider())
                .thenReturn(new EmbeddingProviderInfo("bge-small-zh-v1.5", "bge-small-zh-v1.5@512", 512, true, "", ""));
        ThreadContext.setCurrentUser(user(42L, true));

        KbEmbeddingRebuildResp resp = knowledgeBaseService.rebuildEmbedding("kb-1");

        assertEquals(3, resp.getDocCount());
        assertEquals("bge-small-zh-v1.5@512", resp.getCurrentVersion());
        assertEquals("", resp.getPreviousVersion());
        // 版本必须落库（后续启动自检与审计都以此为准绳）
        ArgumentCaptor<KnowledgeBase> captor = ArgumentCaptor.forClass(KnowledgeBase.class);
        verify(knowledgeBaseMapper).updateById(captor.capture());
        assertEquals("bge-small-zh-v1.5@512", captor.getValue().getEmbeddingProviderVersion());
        assertEquals("bge-small-zh-v1.5", captor.getValue().getEmbeddingProvider());
        // 审计必须记录新旧 provider 版本（回滚溯源的唯一凭据）
        verify(kbAuditLogService).record(eq("kb-1"), eq("研发知识库"), eq(KbAuditLogService.ACTION_KB_REINDEX),
                eq(KbAuditLogService.TARGET_KB), eq("kb-1"), eq(true),
                contains("provider: -> bge-small-zh-v1.5@512,docs:3"));
    }

    @Test
    void rebuildResponseCarriesRollbackEvidence() {
        // 库里已有旧版本记录（上次重建产物），本次切到 bge-m3：previousVersion 即回滚判据
        KnowledgeBase kb = kb("kb-2", "合同库");
        kb.setEmbeddingProvider("bge-small-zh-v1.5");
        kb.setEmbeddingProviderVersion("bge-small-zh-v1.5@512");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(kbDocumentService.rebuildEmbeddingByKb("kb-2")).thenReturn(1);
        when(embeddingProviderService.currentProvider())
                .thenReturn(new EmbeddingProviderInfo("bge-m3", "xinference/bge-m3@1024", 1024, false, "xinference", "bge-m3"));
        ThreadContext.setCurrentUser(user(42L, true));

        KbEmbeddingRebuildResp resp = knowledgeBaseService.rebuildEmbedding("kb-2");

        assertEquals("bge-small-zh-v1.5@512", resp.getPreviousVersion());
        assertEquals("xinference/bge-m3@1024", resp.getCurrentVersion());
        verify(kbAuditLogService).record(eq("kb-2"), eq("合同库"), eq(KbAuditLogService.ACTION_KB_REINDEX),
                eq(KbAuditLogService.TARGET_KB), eq("kb-2"), eq(true),
                contains("provider:bge-small-zh-v1.5@512 -> xinference/bge-m3@1024"));
    }

    @Test
    void rebuildRefusedWhenSwitchDisabled() {
        adiProperties.getKb().setEmbeddingRebuildEnabled(false);
        KnowledgeBase kb = kb("kb-3", "合同库");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        ThreadContext.setCurrentUser(user(42L, true));

        BaseException exception = assertThrows(BaseException.class,
                () -> knowledgeBaseService.rebuildEmbedding("kb-3"));

        assertEquals(ErrorEnum.A_KB_EMBEDDING_REBUILD_DISABLED.getCode(), exception.getCode());
        // 开关关闭时不得触发任何重建副作用
        verify(kbDocumentService, never()).rebuildEmbeddingByKb(anyString());
        verify(knowledgeBaseMapper, never()).updateById(any(com.moyz.adi.common.entity.KnowledgeBase.class));
    }

    @Test
    void rebuildRejectsBlankUuid() {
        ThreadContext.setCurrentUser(user(42L, true));

        BaseException exception = assertThrows(BaseException.class,
                () -> knowledgeBaseService.rebuildEmbedding("  "));

        assertEquals(ErrorEnum.A_PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void rebuildRejectsNonOwnerNonAdmin() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb("kb-4", "私有库"));
        when(knowledgeBaseMapper.exists(any())).thenReturn(false);
        ThreadContext.setCurrentUser(user(7L, false));

        BaseException exception = assertThrows(BaseException.class,
                () -> knowledgeBaseService.rebuildEmbedding("kb-4"));

        assertEquals(ErrorEnum.A_USER_NOT_AUTH.getCode(), exception.getCode());
        verify(kbDocumentService, never()).rebuildEmbeddingByKb(anyString());
    }

    @Test
    void rebuildAllowsOwner() {
        KnowledgeBase kb = kb("kb-5", "自有库");
        kb.setOwnerId(42L);
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(knowledgeBaseMapper.exists(any())).thenReturn(true);
        when(kbDocumentService.rebuildEmbeddingByKb("kb-5")).thenReturn(2);
        when(embeddingProviderService.currentProvider())
                .thenReturn(new EmbeddingProviderInfo("all-minilm-l6-v2", "all-minilm-l6-v2@384", 384, true, "", ""));
        ThreadContext.setCurrentUser(user(42L, false));

        KbEmbeddingRebuildResp resp = knowledgeBaseService.rebuildEmbedding("kb-5");

        assertEquals(2, resp.getDocCount());
        assertEquals("all-minilm-l6-v2@384", resp.getCurrentVersion());
    }

    @Test
    void rebuildRejectsMissingKnowledgeBase() {
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(null);
        ThreadContext.setCurrentUser(user(42L, true));

        BaseException exception = assertThrows(BaseException.class,
                () -> knowledgeBaseService.rebuildEmbedding("not-exist"));

        assertEquals(ErrorEnum.A_DATA_NOT_FOUND.getCode(), exception.getCode());
    }

    @Test
    void rebuildOnQaOnlyKnowledgeBaseStillRecordsVersion() {
        // 只有 QA 文档（非 QA 文档 0 条）时也要入队并记录版本：问答对向量同样过期
        KnowledgeBase kb = kb("kb-6", "问答库");
        when(knowledgeBaseMapper.selectOne(any())).thenReturn(kb);
        when(kbDocumentService.rebuildEmbeddingByKb("kb-6")).thenReturn(1);
        when(embeddingProviderService.currentProvider())
                .thenReturn(new EmbeddingProviderInfo(AdiConstant.EmbeddingModel.BGE_M3,
                        "xinference/bge-m3@1024", 1024, false, "xinference", "bge-m3"));
        ThreadContext.setCurrentUser(user(42L, true));

        KbEmbeddingRebuildResp resp = knowledgeBaseService.rebuildEmbedding("kb-6");

        assertEquals(1, resp.getDocCount());
        assertEquals("bge-m3", resp.getCurrentProvider());
        assertTrue(EmbeddingProviderService.isBgeM3(resp.getCurrentProvider()));
    }

    private static KnowledgeBase kb(String uuid, String title) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(11L);
        kb.setUuid(uuid);
        kb.setTitle(title);
        kb.setOwnerId(42L);
        kb.setIsDeleted(false);
        return kb;
    }

    private static User user(Long id, boolean isAdmin) {
        User user = new User();
        user.setId(id);
        user.setIsAdmin(isAdmin);
        return user;
    }
}
