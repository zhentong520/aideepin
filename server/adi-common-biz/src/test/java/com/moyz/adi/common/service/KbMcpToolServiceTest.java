package com.moyz.adi.common.service;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.extapi.ExtKbRetrieveResp;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KbMcpToolService 测试（T1-10）。
 * <p>
 * 钉死三条 MCP 特有契约：
 * <ol>
 *   <li>key 绑定库与路径 kbUuid 不一致 → 明确报错（不是静默空结果）；</li>
 *   <li>底层检索 skipped（无权/不存在）→ 转成明确错误（与 T1-08 的静默跳过语义区分）；</li>
 *   <li>kb_search 复用 {@link ExtKnowledgeSearchService}（检索内核同源）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class KbMcpToolServiceTest {

    private static final String KB_UUID = "kb-1";
    private static final String OTHER_KB_UUID = "kb-other";

    @Mock
    private ExtKnowledgeSearchService extKnowledgeSearchService;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KbDocumentService kbDocumentService;

    private KbMcpToolService service;

    @BeforeEach
    void setUp() {
        service = new KbMcpToolService();
        ReflectionTestUtils.setField(service, "extKnowledgeSearchService", extKnowledgeSearchService);
        ReflectionTestUtils.setField(service, "knowledgeBaseService", knowledgeBaseService);
        ReflectionTestUtils.setField(service, "kbDocumentService", kbDocumentService);

        // 模拟 ExtApiAuthFilter 完成鉴权后的 ThreadContext：key 绑定 KB_UUID
        User user = user();
        ThreadContext.setCurrentUser(user);
        ThreadContext.setExtApiContext(true, KB_UUID, "knowledge");
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
    }

    @DisplayName("kb_search：key 绑定库与路径 kbUuid 一致 → 复用 ExtKnowledgeSearchService 检索")
    @Test
    void kbSearchReusesExtSearchService() {
        ExtKbRetrieveResp resp = ExtKbRetrieveResp.builder()
                .items(List.of(ExtKbRetrieveResp.Item.builder().content("hit").score(0.9).kbUuid(KB_UUID).build()))
                .skippedKbUuids(List.of())
                .build();
        when(extKnowledgeSearchService.retrieve(any(User.class), eq(List.of(KB_UUID)), eq("q"), eq(5)))
                .thenReturn(resp);

        List<ExtKbRetrieveResp.Item> items = service.kbSearch(ThreadContext.getCurrentUser(), KB_UUID, "q", 5);

        assertEquals(1, items.size());
        assertEquals("hit", items.get(0).getContent());
        // 内核复用证明：MCP 的检索只经由 ExtKnowledgeSearchService 一个入口
        verify(extKnowledgeSearchService).retrieve(any(User.class), eq(List.of(KB_UUID)), eq("q"), eq(5));
    }

    @DisplayName("kb_search：key 未绑定该库（path kbUuid != entityUuid）→ 明确报错，不进检索")
    @Test
    void kbSearchWithUnboundKbFailsFast() {
        BaseException ex = assertThrows(BaseException.class,
                () -> service.kbSearch(ThreadContext.getCurrentUser(), OTHER_KB_UUID, "q", null));
        assertTrue(ex.getMessage().contains(OTHER_KB_UUID), "错误信息必须指明库");
        verify(extKnowledgeSearchService, never()).retrieve(any(), anyList(), anyString(), any());
    }

    @DisplayName("kb_search：底层检索 skipped（无权/不存在）→ 明确报错（MCP 与 T1-08 skipped 语义区分）")
    @Test
    void kbSearchDeniedKbRaisesExplicitError() {
        when(extKnowledgeSearchService.retrieve(any(User.class), eq(List.of(KB_UUID)), eq("q"), any()))
                .thenReturn(ExtKbRetrieveResp.builder()
                        .items(List.of())
                        .skippedKbUuids(List.of(KB_UUID))
                        .build());

        BaseException ex = assertThrows(BaseException.class,
                () -> service.kbSearch(ThreadContext.getCurrentUser(), KB_UUID, "q", null));
        assertTrue(ex.getMessage().contains("not accessible"), "无权库必须返回明确错误：" + ex.getMessage());
    }

    @DisplayName("kb_get_document：返回文档原文（rawText 优先），文档必须属于该库")
    @Test
    void kbGetDocumentReturnsContent() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(KB_UUID);
        kb.setOwnerUuid("key-owner");
        kb.setIsPublic(false);
        when(knowledgeBaseService.getOrThrow(KB_UUID)).thenReturn(kb);
        KbDocument doc = new KbDocument();
        doc.setUuid("doc-1");
        doc.setKbUuid(KB_UUID);
        doc.setTitle("t");
        doc.setRawText("raw content");
        doc.setRemark("remark content");
        when(kbDocumentService.getEnable("doc-1")).thenReturn(doc);

        Map<String, Object> result = service.kbGetDocument(ThreadContext.getCurrentUser(), KB_UUID, "doc-1");

        assertEquals("doc-1", result.get("uuid"));
        assertEquals("raw content", result.get("content"), "原文取 rawText 优先");
    }

    @DisplayName("kb_get_document：文档属于别的库 → 明确报错（防借 docUuid 跨库探取）")
    @Test
    void kbGetDocumentCrossKbDocRejected() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(KB_UUID);
        kb.setOwnerUuid("key-owner");
        when(knowledgeBaseService.getOrThrow(KB_UUID)).thenReturn(kb);
        KbDocument doc = new KbDocument();
        doc.setUuid("doc-2");
        doc.setKbUuid(OTHER_KB_UUID);
        when(kbDocumentService.getEnable("doc-2")).thenReturn(doc);

        BaseException ex = assertThrows(BaseException.class,
                () -> service.kbGetDocument(ThreadContext.getCurrentUser(), KB_UUID, "doc-2"));
        assertNotNull(ex.getMessage());
    }

    @DisplayName("kb_get_document：文档不存在 → A_DATA_NOT_FOUND")
    @Test
    void kbGetDocumentMissingDoc() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(KB_UUID);
        kb.setOwnerUuid("key-owner");
        when(knowledgeBaseService.getOrThrow(KB_UUID)).thenReturn(kb);
        when(kbDocumentService.getEnable("doc-x")).thenReturn(null);

        assertThrows(BaseException.class,
                () -> service.kbGetDocument(ThreadContext.getCurrentUser(), KB_UUID, "doc-x"));
    }

    @DisplayName("kb_get_document：key 未绑定该库 → 明确报错")
    @Test
    void kbGetDocumentUnboundKb() {
        assertThrows(BaseException.class,
                () -> service.kbGetDocument(ThreadContext.getCurrentUser(), OTHER_KB_UUID, "doc-1"));
    }

    @DisplayName("无库归属权限的 owner（其他人的私有库）也不能借 getOrThrow 绕过读权限")
    @Test
    void kbGetDocumentPermissionDenied() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(KB_UUID);
        // ThreadContext 当前用户不是 owner（key 绑定的 kb 校验在 checkAccess 已过，
        // 这里模拟另一种入口：库存在但 subject 不可读）
        kb.setOwnerUuid("someone-else");
        kb.setIsPublic(false);
        when(knowledgeBaseService.getOrThrow(KB_UUID)).thenReturn(kb);

        BaseException ex = assertThrows(BaseException.class,
                () -> service.kbGetDocument(ThreadContext.getCurrentUser(), KB_UUID, "doc-1"));
        assertTrue(ex.getMessage().contains("not accessible"));
        verify(kbDocumentService, never()).getEnable(anyString());
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setUuid("key-owner");
        user.setIsAdmin(false);
        return user;
    }
}
