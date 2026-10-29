package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.entity.ChatPage;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * ChatPageService 纯逻辑单测（二开 P1）：spy 掉 ServiceImpl 的持久化方法，
 * 只验证保存/更新的分支语义（默认值兜底、部分更新、启停、软删）。
 */
class ChatPageServiceTest {

    private ChatPageService service;

    @BeforeEach
    void setUp() {
        service = spy(new ChatPageService());
    }

    private static ChatPage page(String uuid, String name) {
        ChatPage p = new ChatPage();
        p.setUuid(uuid);
        p.setName(name);
        return p;
    }

    // ===== saveOrUpdatePage：新建 =====

    @Test
    void insert_fillsDefaultsAndGeneratesUuid() {
        AtomicReference<ChatPage> saved = new AtomicReference<>();
        doAnswer(inv -> {
            saved.set(inv.getArgument(0));
            return true;
        }).when(service).save(any(ChatPage.class));

        ChatPage input = page("", "导购页");
        ChatPage out = service.saveOrUpdatePage(input);

        assertNotNull(out.getUuid());
        assertFalse(out.getUuid().isBlank());
        assertEquals("", out.getIcon());
        assertEquals("", out.getSceneUuid());
        assertEquals("", out.getAgentUuid());
        assertEquals(0, out.getSort());
        assertTrue(out.getIsEnable());
        assertNotNull(out.getPageSchema());
        // schema 兜底：渲染器拿到空 blocks 不 NPE
        assertTrue(out.getPageSchema().has("blocks"));
        assertEquals(0, out.getPageSchema().get("blocks").size());
        assertEquals(out, saved.get());
    }

    @Test
    void insert_keepsProvidedOptionalFields() {
        doReturn(true).when(service).save(any(ChatPage.class));
        JsonNode schema = JsonUtil.toJsonNode("{\"blocks\":[{\"type\":\"welcome\"}]}");

        ChatPage input = page("", "导购页");
        input.setIcon("Cart");
        input.setSceneUuid("scene-1");
        input.setAgentUuid("agent-1");
        input.setPageSchema(schema);
        input.setSort(5);
        input.setIsEnable(false);

        ChatPage out = service.saveOrUpdatePage(input);
        assertEquals("Cart", out.getIcon());
        assertEquals("scene-1", out.getSceneUuid());
        assertEquals("agent-1", out.getAgentUuid());
        assertEquals(schema, out.getPageSchema());
        assertEquals(5, out.getSort());
        assertFalse(out.getIsEnable());
    }

    @Test
    void insert_blankName_throwsParamsError() {
        assertThrows(BaseException.class, () -> service.saveOrUpdatePage(page("", "")));
        verify(service, never()).save(any(ChatPage.class));
    }

    // ===== saveOrUpdatePage：更新（部分更新语义：null 保持库中值） =====

    @Test
    void update_nullFieldsKeepStoredValues() {
        ChatPage stored = page("u1", "旧名");
        stored.setIcon("OldIcon");
        stored.setPageSchema(JsonUtil.toJsonNode("{\"blocks\":[{\"type\":\"welcome\"}]}"));
        stored.setSceneUuid("scene-old");
        stored.setAgentUuid("agent-old");
        stored.setSort(3);
        stored.setIsEnable(true);
        doReturn(stored).when(service).getByUuid("u1");
        AtomicReference<ChatPage> updated = new AtomicReference<>();
        doAnswer(inv -> {
            updated.set(inv.getArgument(0));
            return true;
        }).when(service).updateById(any(ChatPage.class));

        ChatPage input = page("u1", "新名");
        // 全部可空字段都不传 → 保持库中值
        ChatPage out = service.saveOrUpdatePage(input);

        assertEquals("新名", out.getName());
        assertEquals("OldIcon", out.getIcon());
        assertEquals("scene-old", out.getSceneUuid());
        assertEquals("agent-old", out.getAgentUuid());
        assertEquals(3, out.getSort());
        assertTrue(out.getIsEnable());
        // pageSchema 为 null 保持库中值（jsonb 铁律：updateById 走 typeHandler）
        assertNotNull(out.getPageSchema());
        assertEquals(1, out.getPageSchema().get("blocks").size());
        assertEquals(out, updated.get());
    }

    @Test
    void update_nonNullFieldsOverwrite() {
        ChatPage stored = page("u1", "旧名");
        stored.setSceneUuid("scene-old");
        stored.setSort(3);
        doReturn(stored).when(service).getByUuid("u1");
        doReturn(true).when(service).updateById(any(ChatPage.class));

        ChatPage input = page("u1", "新名");
        input.setSceneUuid("scene-new");
        input.setSort(9);
        input.setIsEnable(false);

        ChatPage out = service.saveOrUpdatePage(input);
        assertEquals("scene-new", out.getSceneUuid());
        assertEquals(9, out.getSort());
        assertFalse(out.getIsEnable());
    }

    @Test
    void update_missingUuid_throwsDataNotFound() {
        // stub 底层 getOne 返回 null，让真实 getByUuid 走到「查无 → 抛 A_DATA_NOT_FOUND」分支
        doReturn(null).when(service).getOne(any());
        ChatPage input = page("ghost", "任意名");
        assertThrows(BaseException.class, () -> service.saveOrUpdatePage(input));
    }

    // ===== toggle / softDelete =====

    @Test
    void toggle_setsIsEnable() {
        ChatPage stored = page("u1", "页");
        stored.setIsEnable(true);
        doReturn(stored).when(service).getByUuid("u1");
        doReturn(true).when(service).updateById(any(ChatPage.class));

        assertTrue(service.toggle("u1", false));
        assertFalse(stored.getIsEnable());
    }

    @Test
    void softDelete_marksDeleted() {
        ChatPage stored = page("u1", "页");
        stored.setIsDeleted(false);
        doReturn(stored).when(service).getByUuid("u1");
        doReturn(true).when(service).updateById(any(ChatPage.class));

        service.softDelete("u1");
        assertTrue(stored.getIsDeleted());
    }

    @Test
    void toggle_missingUuid_throwsDataNotFound() {
        doReturn(null).when(service).getOne(any());
        assertThrows(BaseException.class, () -> service.toggle("ghost", true));
    }

    // ===== emptySchema 兜底 =====

    @Test
    void emptySchemaFallback_isBlockArray() {
        doReturn(true).when(service).save(any(ChatPage.class));
        ChatPage out = service.saveOrUpdatePage(page("", "无 schema 页"));
        assertNotNull(out.getPageSchema());
        assertTrue(out.getPageSchema().has("blocks"));
    }
}
