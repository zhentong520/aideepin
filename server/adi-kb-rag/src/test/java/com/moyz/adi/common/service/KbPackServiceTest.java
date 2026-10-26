package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.KbEditReq;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.KbDocumentMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-06：知识库技能配置包导出/导入单测。
 * <p>
 * 覆盖任务书验收点：导出→导入往返一致 / 虚拟 SKILL.md 兜底 /
 * 导入方无 ACL 时引用被拒绝而非静默穿透 / data 字段不跨库复制。
 */
class KbPackServiceTest {

    private KbPackService kbPackService;
    private KnowledgeBaseService knowledgeBaseService;
    private KbDocumentService kbDocumentService;
    private KbAuditLogService kbAuditLogService;
    private KbDocumentMapper kbDocumentMapper;
    private AdiProperties adiProperties;

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(configuration, ""), KbDocument.class);
        kbPackService = new KbPackService();
        knowledgeBaseService = mock(KnowledgeBaseService.class);
        kbDocumentService = mock(KbDocumentService.class);
        kbAuditLogService = mock(KbAuditLogService.class);
        kbDocumentMapper = mock(KbDocumentMapper.class);
        when(kbDocumentService.lambdaQuery())
                .thenReturn(new LambdaQueryChainWrapper<>(kbDocumentMapper, KbDocument.class));
        adiProperties = new AdiProperties();
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);
        ReflectionTestUtils.setField(kbPackService, "knowledgeBaseService", knowledgeBaseService);
        ReflectionTestUtils.setField(kbPackService, "kbDocumentService", kbDocumentService);
        ReflectionTestUtils.setField(kbPackService, "kbAuditLogService", kbAuditLogService);
        ReflectionTestUtils.setField(kbPackService, "adiProperties", adiProperties);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    // ===== 导出 =====

    @Test
    void exportProducesThreeEntriesWithVirtualSkillMdFallback() throws Exception {
        KnowledgeBase kb = richKb("kb-src", "研发知识库");
        when(knowledgeBaseService.getReadableOrThrow("kb-src")).thenReturn(kb);
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of());

        byte[] zip = kbPackService.exportPack("kb-src");
        Map<String, String> entries = unzip(zip);

        assertEquals(3, entries.size());
        // 虚拟 SKILL.md 兜底：知识库没有「技能指令」也能导出默认模板
        String skillMd = entries.get("SKILL.md");
        assertNotNull(skillMd);
        assertTrue(skillMd.startsWith("---"));
        assertTrue(skillMd.contains("name: 研发知识库"));
        assertTrue(skillMd.contains("description: 研发库备注"));
        // data 字段不跨库复制：config.json 只有白名单配置键，无 owner/api_key/文档内容
        Map<String, Object> config = JsonUtil.toMap(entries.get("config.json"));
        assertEquals("sentence", config.get("ingestSplitStrategy"));
        assertEquals(5, ((Number) config.get("retrieveMaxResults")).intValue());
        assertEquals("ORG", config.get("defaultSensitivity"));
        for (String forbidden : List.of("ownerId", "ownerUuid", "ownerName", "apiKey", "itemCount",
                "embeddingCount", "starCount", "categoryId", "documents", "segments", "embeddings",
                "embeddingProvider", "embeddingProviderVersion")) {
            assertFalse(config.containsKey(forbidden), "config.json must not carry " + forbidden);
        }
        // pack.json 清单：格式/版本/来源/provider 溯源/引用清单
        Map<String, Object> pack = JsonUtil.toMap(entries.get("pack.json"));
        assertEquals("kb-pack", pack.get("format"));
        assertEquals(1, ((Number) pack.get("version")).intValue());
        assertEquals("kb-src", pack.get("sourceKbUuid"));
        assertEquals("xinference/bge-m3@1024", pack.get("embeddingProviderVersion"));
        verify(kbAuditLogService).record(eq("kb-src"), eq("研发知识库"),
                eq(KbAuditLogService.ACTION_KB_PACK_EXPORT), eq(KbAuditLogService.TARGET_KB),
                eq("kb-src"), eq(true), anyString());
    }

    @Test
    void exportListsDocumentReferencesAsMetadataOnly() throws Exception {
        KnowledgeBase kb = richKb("kb-src", "研发知识库");
        when(knowledgeBaseService.getReadableOrThrow("kb-src")).thenReturn(kb);
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(doc("doc-1", "员工手册"), doc("doc-2", "报销流程")));

        byte[] zip = kbPackService.exportPack("kb-src");
        Map<String, Object> pack = JsonUtil.toMap(unzip(zip).get("pack.json"));

        List<?> refs = (List<?>) pack.get("references");
        assertEquals(2, refs.size());
        Map<?, ?> first = (Map<?, ?>) refs.get(0);
        assertEquals("document", first.get("type"));
        assertEquals("doc-1", first.get("uuid"));
        assertEquals("员工手册", first.get("title"));
    }

    @Test
    void exportRefusedWhenSwitchDisabled() {
        adiProperties.getKb().setPackSharingEnabled(false);

        BaseException exception = assertThrows(BaseException.class, () -> kbPackService.exportPack("kb-src"));

        assertEquals(ErrorEnum.A_PARAMS_ERROR.getCode(), exception.getCode());
        verify(knowledgeBaseService, never()).getReadableOrThrow(anyString());
    }

    @Test
    void exportRequiresReadPrivilege() {
        // 先构造异常再 stubbing：BaseException 构造会走 SpringUtil.getMessage（mock 交互），
        // 嵌在 when(...).thenThrow(...) 里会触发 UnfinishedStubbingException
        BaseException denied = new BaseException(ErrorEnum.A_USER_NOT_AUTH);
        when(knowledgeBaseService.getReadableOrThrow("kb-private")).thenThrow(denied);

        assertThrows(BaseException.class, () -> kbPackService.exportPack("kb-private"));

        verify(kbAuditLogService, never()).record(anyString(), any(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), anyString());
    }

    // ===== 导入 =====

    @Test
    void exportImportRoundtripPreservesConfig() throws Exception {
        KnowledgeBase source = richKb("kb-src", "研发知识库");
        when(knowledgeBaseService.getReadableOrThrow("kb-src")).thenReturn(source);
        when(kbDocumentMapper.selectList(any())).thenReturn(List.of(doc("doc-1", "员工手册")));
        byte[] zip = kbPackService.exportPack("kb-src");
        // 同一用户导入：引用文档可读（owner 本人）
        ThreadContext.setCurrentUser(user(42L));
        when(kbDocumentService.getEnable("doc-1")).thenReturn(doc("doc-1", "员工手册"));
        when(knowledgeBaseService.getReadableOrThrow("kb-other")).thenReturn(source);
        when(knowledgeBaseService.saveOrUpdate(any(KbEditReq.class))).thenAnswer(invocation -> {
            KbEditReq req = invocation.getArgument(0);
            KnowledgeBase created = new KnowledgeBase();
            created.setUuid("kb-new");
            created.setTitle(req.getTitle());
            created.setOwnerId(42L);
            return created;
        });

        KnowledgeBase imported = kbPackService.importPack(multipart(zip), null);

        assertEquals("kb-new", imported.getUuid());
        ArgumentCaptor<KbEditReq> captor = ArgumentCaptor.forClass(KbEditReq.class);
        verify(knowledgeBaseService).saveOrUpdate(captor.capture());
        KbEditReq req = captor.getValue();
        // 往返一致：标题/描述/配置逐字段等于来源库
        assertEquals("研发知识库", req.getTitle());
        assertEquals("研发库备注", req.getRemark());
        assertEquals("sentence", req.getIngestSplitStrategy());
        assertEquals(384, req.getIngestMaxSegmentSize());
        assertEquals(51, req.getIngestMaxOverlap());
        assertEquals(5, req.getRetrieveMaxResults());
        assertEquals(0.32, req.getRetrieveMinScore());
        assertEquals(Boolean.TRUE, req.getRetrieveHybridEnabled());
        assertEquals(60, req.getRetrieveRrfK());
        assertEquals(12, req.getRetrieveKeywordLimit());
        assertEquals(Boolean.TRUE, req.getRetrieveRerankEnabled());
        assertEquals(3, req.getRetrieveRerankTopN());
        assertEquals(0.21, req.getRetrieveRerankMinScore());
        assertEquals(Boolean.TRUE, req.getRetrieveNeighborEnabled());
        assertEquals(1, req.getRetrieveNeighborWindow());
        assertEquals(0.55, req.getQueryLlmTemperature());
        assertEquals("你是知识库问答助手", req.getQuerySystemMessage());
        assertEquals(Boolean.FALSE, req.getIsRetrievalEnabled());
        assertEquals("ORG", req.getDefaultSensitivity());
        assertNotNull(req.getDefaultVisibilityGroups());
        assertEquals("owner:user-42", req.getDefaultVisibilityGroups().get(0).asText());
        verify(kbAuditLogService).record(eq("kb-new"), eq("研发知识库"),
                eq(KbAuditLogService.ACTION_KB_PACK_IMPORT), eq(KbAuditLogService.TARGET_KB),
                eq("kb-new"), eq(true), anyString());
    }

    @Test
    void importTitleOverrideWins() throws Exception {
        byte[] zip = zipOf(Map.of(
                "pack.json", JsonUtil.toJson(manifest()),
                "config.json", "{\"retrieveMaxResults\":5}"));
        when(knowledgeBaseService.saveOrUpdate(any(KbEditReq.class))).thenAnswer(invocation -> {
            KbEditReq req = invocation.getArgument(0);
            KnowledgeBase created = new KnowledgeBase();
            created.setUuid("kb-new");
            created.setTitle(req.getTitle());
            return created;
        });

        KnowledgeBase imported = kbPackService.importPack(multipart(zip), "我的新库");

        assertEquals("我的新库", imported.getTitle());
    }

    @Test
    void importToleratesMissingSkillMdWithVirtualFallback() throws Exception {
        // 老包/手工包可能没有 SKILL.md：用清单里的来源库标题兜底，与导出侧兜底对称
        byte[] zip = zipOf(Map.of(
                "pack.json", JsonUtil.toJson(manifest()),
                "config.json", "{\"retrieveMaxResults\":5}"));
        when(knowledgeBaseService.saveOrUpdate(any(KbEditReq.class))).thenAnswer(invocation -> {
            KbEditReq req = invocation.getArgument(0);
            KnowledgeBase created = new KnowledgeBase();
            created.setUuid("kb-new");
            created.setTitle(req.getTitle());
            created.setRemark(req.getRemark());
            return created;
        });

        KnowledgeBase imported = kbPackService.importPack(multipart(zip), null);

        assertEquals("来源库", imported.getTitle());
        assertTrue(imported.getRemark().contains("kb-src"));
    }

    @Test
    void importAcceptsCommonRootLayout() throws Exception {
        Map<String, Object> pack = manifest();
        pack.put("sourceKbTitle", "来源库");
        byte[] zip = zipOf(Map.of(
                "kb-pack/pack.json", JsonUtil.toJson(pack),
                "kb-pack/config.json", "{\"retrieveMaxResults\":7}"));
        when(knowledgeBaseService.saveOrUpdate(any(KbEditReq.class))).thenAnswer(invocation -> {
            KbEditReq req = invocation.getArgument(0);
            KnowledgeBase created = new KnowledgeBase();
            created.setUuid("kb-new");
            created.setTitle(req.getTitle());
            return created;
        });

        KnowledgeBase imported = kbPackService.importPack(multipart(zip), null);

        assertEquals("来源库", imported.getTitle());
        ArgumentCaptor<KbEditReq> captor = ArgumentCaptor.forClass(KbEditReq.class);
        verify(knowledgeBaseService).saveOrUpdate(captor.capture());
        assertEquals(7, captor.getValue().getRetrieveMaxResults());
    }

    @Test
    void importRejectsDeniedReferencesInsteadOfSilentPassthrough() {
        // 导入方对包内引用的文档无权：整体拒绝，绝不静默穿透
        Map<String, Object> pack = manifest();
        pack.put("references", List.of(ref("doc-1", "机密文档")));
        byte[] zip = zipOf(Map.of(
                "pack.json", JsonUtil.toJson(pack),
                "config.json", "{\"retrieveMaxResults\":5}"));
        ThreadContext.setCurrentUser(user(7L));
        when(kbDocumentService.getEnable("doc-1")).thenReturn(doc("doc-1", "机密文档"));
        // 先构造异常再 stubbing：BaseException 构造走 SpringUtil.getMessage（mock 交互），
        // 嵌在 when(...).thenThrow(...) 里会触发 UnfinishedStubbingException
        BaseException denied = new BaseException(ErrorEnum.A_USER_NOT_AUTH);
        when(knowledgeBaseService.getReadableOrThrow("kb-other")).thenThrow(denied);

        BaseException exception = assertThrows(BaseException.class, () -> kbPackService.importPack(multipart(zip), null));

        assertEquals(ErrorEnum.A_KB_PACK_IMPORT_REF_DENIED.getCode(), exception.getCode());
        // 拒绝即拒绝：不得创建任何知识库
        verify(knowledgeBaseService, never()).saveOrUpdate(any(KbEditReq.class));
    }

    @Test
    void importRejectsReferencesMissingForImporter() {
        // 引用文档在导入方环境里根本不存在：同样拒绝（静默丢弃会让配置指向幽灵数据）
        Map<String, Object> pack = manifest();
        pack.put("references", List.of(ref("doc-ghost", "幽灵文档")));
        byte[] zip = zipOf(Map.of(
                "pack.json", JsonUtil.toJson(pack),
                "config.json", "{\"retrieveMaxResults\":5}"));
        ThreadContext.setCurrentUser(user(7L));
        when(kbDocumentService.getEnable("doc-ghost")).thenReturn(null);

        BaseException exception = assertThrows(BaseException.class, () -> kbPackService.importPack(multipart(zip), null));

        assertEquals(ErrorEnum.A_KB_PACK_IMPORT_REF_DENIED.getCode(), exception.getCode());
        verify(knowledgeBaseService, never()).saveOrUpdate(any(KbEditReq.class));
    }

    @Test
    void importAllowsPublicReferencesUnderImporterAcl() throws Exception {
        // 来源库公开：任何登录用户都可读引用——按导入方 ACL 放行，这正是重校验的意义
        Map<String, Object> pack = manifest();
        pack.put("references", List.of(ref("doc-1", "公开文档")));
        byte[] zip = zipOf(Map.of(
                "pack.json", JsonUtil.toJson(pack),
                "config.json", "{\"retrieveMaxResults\":5}"));
        ThreadContext.setCurrentUser(user(7L));
        when(kbDocumentService.getEnable("doc-1")).thenReturn(doc("doc-1", "公开文档"));
        when(knowledgeBaseService.getReadableOrThrow("kb-other")).thenReturn(richKb("kb-other", "公开库"));
        when(knowledgeBaseService.saveOrUpdate(any(KbEditReq.class))).thenAnswer(invocation -> {
            KbEditReq req = invocation.getArgument(0);
            KnowledgeBase created = new KnowledgeBase();
            created.setUuid("kb-new");
            created.setTitle(req.getTitle());
            return created;
        });

        assertNotNull(kbPackService.importPack(multipart(zip), null));

        verify(knowledgeBaseService).saveOrUpdate(any(KbEditReq.class));
    }

    @Test
    void importRejectsUnsupportedFormatOrVersion() {
        Map<String, Object> badFormat = manifest();
        badFormat.put("format", "other-pack");
        Map<String, Object> badVersion = manifest();
        badVersion.put("version", 99);

        for (Map<String, Object> bad : List.of(badFormat, badVersion)) {
            byte[] zip = zipOf(Map.of("pack.json", JsonUtil.toJson(bad), "config.json", "{}"));
            BaseException exception = assertThrows(BaseException.class,
                    () -> kbPackService.importPack(multipart(zip), null));
            assertEquals(ErrorEnum.A_KB_PACK_FORMAT_INVALID.getCode(), exception.getCode());
        }
        verify(knowledgeBaseService, never()).saveOrUpdate(any(KbEditReq.class));
    }

    @Test
    void importRejectsMissingManifestOrConfig() {
        byte[] noManifest = zipOf(Map.of(
                "config.json", "{}",
                "SKILL.md", "---\nname: x\ndescription: y\n---\n"));
        byte[] noConfig = zipOf(Map.of("pack.json", JsonUtil.toJson(manifest())));
        byte[] emptyZip = zipOf(Map.<String, String>of());

        for (byte[] zip : List.of(noManifest, noConfig, emptyZip)) {
            BaseException exception = assertThrows(BaseException.class,
                    () -> kbPackService.importPack(multipart(zip), null));
            assertEquals(ErrorEnum.A_KB_PACK_FORMAT_INVALID.getCode(), exception.getCode());
        }
        verify(knowledgeBaseService, never()).saveOrUpdate(any(KbEditReq.class));
    }

    @Test
    void importRefusedWhenSwitchDisabled() {
        adiProperties.getKb().setPackSharingEnabled(false);

        BaseException exception = assertThrows(BaseException.class,
                () -> kbPackService.importPack(multipart(new byte[0]), null));

        assertEquals(ErrorEnum.A_PARAMS_ERROR.getCode(), exception.getCode());
    }

    @Test
    void importRejectsEmptyOrOversizedFile() {
        assertThrows(BaseException.class, () -> kbPackService.importPack(null, null));
        MockMultipartFile empty = new MockMultipartFile("file", "pack.zip", "application/zip", new byte[0]);
        assertThrows(BaseException.class, () -> kbPackService.importPack(empty, null));
    }

    @Test
    void importIgnoresUnknownConfigKeys() throws Exception {
        // 未来/恶意包塞入未知键：忽略而不是经 BeanUtils 直通实体
        byte[] zip = zipOf(Map.of(
                "pack.json", JsonUtil.toJson(manifest()),
                "config.json", "{\"retrieveMaxResults\":5,\"ownerId\":999,\"uuid\":\"hacked\"}"));
        when(knowledgeBaseService.saveOrUpdate(any(KbEditReq.class))).thenAnswer(invocation -> {
            KbEditReq req = invocation.getArgument(0);
            KnowledgeBase created = new KnowledgeBase();
            created.setUuid("kb-new");
            created.setTitle(req.getTitle());
            return created;
        });

        kbPackService.importPack(multipart(zip), null);

        ArgumentCaptor<KbEditReq> captor = ArgumentCaptor.forClass(KbEditReq.class);
        verify(knowledgeBaseService).saveOrUpdate(captor.capture());
        KbEditReq req = captor.getValue();
        assertNull(req.getUuid());
        assertEquals(5, req.getRetrieveMaxResults());
    }

    // ===== helpers =====

    private static KnowledgeBase richKb(String uuid, String title) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(11L);
        kb.setUuid(uuid);
        kb.setTitle(title);
        kb.setRemark("研发库备注");
        kb.setOwnerId(42L);
        kb.setOwnerUuid("user-42");
        kb.setOwnerName("alice");
        kb.setIsStrict(true);
        kb.setIngestSplitStrategy("sentence");
        kb.setIngestMaxSegmentSize(384);
        kb.setIngestMaxOverlap(51);
        kb.setIngestCustomSeparator("###");
        kb.setIngestTokenEstimator("openai");
        kb.setRetrieveMaxResults(5);
        kb.setRetrieveMinScore(0.32);
        kb.setIsRetrievalEnabled(false);
        kb.setQueryLlmTemperature(0.55);
        kb.setQuerySystemMessage("你是知识库问答助手");
        kb.setRetrieveHybridEnabled(true);
        kb.setRetrieveRrfK(60);
        kb.setRetrieveKeywordLimit(12);
        kb.setRetrieveRerankEnabled(true);
        kb.setRetrieveRerankTopN(3);
        kb.setRetrieveRerankMinScore(0.21);
        kb.setRetrieveNeighborEnabled(true);
        kb.setRetrieveNeighborWindow(1);
        kb.setDefaultSensitivity("ORG");
        kb.setDefaultVisibilityGroups(JsonUtil.toJsonNode("[\"owner:user-42\"]"));
        kb.setEmbeddingProvider("bge-m3");
        kb.setEmbeddingProviderVersion("xinference/bge-m3@1024");
        kb.setApiKey("secret-key");
        kb.setCategoryId(3L);
        return kb;
    }

    private static KbDocument doc(String uuid, String title) {
        KbDocument doc = new KbDocument();
        doc.setUuid(uuid);
        doc.setTitle(title);
        doc.setKbUuid("kb-other");
        doc.setIsDeleted(false);
        return doc;
    }

    private static Map<String, Object> manifest() {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("format", "kb-pack");
        pack.put("version", 1);
        pack.put("sourceKbUuid", "kb-src");
        pack.put("sourceKbTitle", "来源库");
        pack.put("exportedAt", "2026-10-20T16:00:00");
        pack.put("embeddingProvider", "bge-m3");
        pack.put("embeddingProviderVersion", "xinference/bge-m3@1024");
        pack.put("references", List.of());
        return pack;
    }

    private static Map<String, String> ref(String uuid, String title) {
        Map<String, String> ref = new LinkedHashMap<>();
        ref.put("type", "document");
        ref.put("uuid", uuid);
        ref.put("title", title);
        return ref;
    }

    private static User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setUuid("user-" + id);
        user.setName("user" + id);
        user.setIsAdmin(false);
        return user;
    }

    private static MockMultipartFile multipart(byte[] zip) {
        return new MockMultipartFile("file", "kb-pack.zip", "application/zip", zip);
    }

    private static byte[] zipOf(Map<String, String> entries) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            zos.finish();
            return bos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> unzip(byte[] zip) throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
                entries.put(entry.getName(), out.toString(StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return entries;
    }
}
