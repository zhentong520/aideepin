package com.moyz.adi.chat.controller;

import com.moyz.adi.common.dto.RefEmbeddingDto;
import com.moyz.adi.common.dto.RefGraphDto;
import com.moyz.adi.common.entity.CharacterMessage;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.CharacterMessageRefEmbeddingService;
import com.moyz.adi.common.service.CharacterMessageRefGraphService;
import com.moyz.adi.common.service.CharacterMessageRefMemoryEmbeddingService;
import com.moyz.adi.common.service.CharacterMessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code /character/message/*-ref} 三个只读端点的前置鉴权回归测试。
 * <p>
 * 2026-09-30 之前这三个端点只按消息 uuid 查引用表，不校验消息归属：任何登录用户拿到
 * 他人消息 uuid 就能读到该消息命中的知识库/记忆片段与图谱内容（实测已复现）。
 * 修复方式是在进引用表之前先调 {@code CharacterMessageService.getOwnedOrThrow}；
 * 本测试钉死「鉴权失败时不触碰引用表」这一顺序，防止将来被顺手挪走。
 * </p>
 * <p>
 * Regression tests for the pre-auth guard on the three read-only {@code *-ref} endpoints.
 * Before 2026-09-30 they resolved references by message uuid without checking ownership, letting
 * any logged-in user read another user's referenced knowledge/memory chunks and graph. The fix
 * calls {@code getOwnedOrThrow} first; these tests pin that ordering ("guard rejected ⇒ reference
 * stores are never queried") so it cannot be silently removed.
 * </p>
 */
class CharacterMessageControllerAuthorizationTest {

    private CharacterMessageController controller;
    private CharacterMessageService characterMessageService;
    private CharacterMessageRefEmbeddingService refEmbeddingService;
    private CharacterMessageRefMemoryEmbeddingService refMemoryEmbeddingService;
    private CharacterMessageRefGraphService refGraphService;

    @BeforeEach
    void setUp() {
        controller = new CharacterMessageController();
        characterMessageService = mock(CharacterMessageService.class);
        refEmbeddingService = mock(CharacterMessageRefEmbeddingService.class);
        refMemoryEmbeddingService = mock(CharacterMessageRefMemoryEmbeddingService.class);
        refGraphService = mock(CharacterMessageRefGraphService.class);

        ReflectionTestUtils.setField(controller, "characterMessageService", characterMessageService);
        ReflectionTestUtils.setField(controller, "characterMessageRefEmbeddingService", refEmbeddingService);
        ReflectionTestUtils.setField(controller, "characterMessageRefMemoryEmbeddingService", refMemoryEmbeddingService);
        ReflectionTestUtils.setField(controller, "characterMessageRefGraphService", refGraphService);
    }

    @Test
    void knowledgeRefChecksOwnershipBeforeQueryingReferences() {
        when(characterMessageService.getOwnedOrThrow(anyString()))
                .thenThrow(new BaseException(ErrorEnum.A_USER_NOT_AUTH));

        BaseException exception = assertThrows(BaseException.class, () -> controller.embeddingRef("foreign-msg"));

        assertEquals(ErrorEnum.A_USER_NOT_AUTH.getCode(), exception.getCode());
        verify(refEmbeddingService, never()).listRefEmbeddings(anyString());
    }

    @Test
    void memoryRefChecksOwnershipBeforeQueryingReferences() {
        when(characterMessageService.getOwnedOrThrow(anyString()))
                .thenThrow(new BaseException(ErrorEnum.A_USER_NOT_AUTH));

        BaseException exception = assertThrows(BaseException.class, () -> controller.memoryEmbeddingRef("foreign-msg"));

        assertEquals(ErrorEnum.A_USER_NOT_AUTH.getCode(), exception.getCode());
        verify(refMemoryEmbeddingService, never()).listRefEmbeddings(anyString());
    }

    @Test
    void graphRefChecksOwnershipBeforeQueryingReferences() {
        when(characterMessageService.getOwnedOrThrow(anyString()))
                .thenThrow(new BaseException(ErrorEnum.A_USER_NOT_AUTH));

        BaseException exception = assertThrows(BaseException.class, () -> controller.graphRef("foreign-msg"));

        assertEquals(ErrorEnum.A_USER_NOT_AUTH.getCode(), exception.getCode());
        verify(refGraphService, never()).getByMsgUuid(anyString());
    }

    @Test
    void ownerPathStillQueriesReferences() {
        CharacterMessage own = new CharacterMessage();
        own.setUuid("own-msg");
        when(characterMessageService.getOwnedOrThrow("own-msg")).thenReturn(own);

        List<RefEmbeddingDto> kbRefs = List.of(new RefEmbeddingDto());
        when(refEmbeddingService.listRefEmbeddings("own-msg")).thenReturn(kbRefs);
        when(refMemoryEmbeddingService.listRefEmbeddings("own-msg")).thenReturn(kbRefs);
        RefGraphDto graph = RefGraphDto.builder().build();
        when(refGraphService.getByMsgUuid("own-msg")).thenReturn(graph);

        assertSame(kbRefs, controller.embeddingRef("own-msg"));
        assertSame(kbRefs, controller.memoryEmbeddingRef("own-msg"));
        assertSame(graph, controller.graphRef("own-msg"));
    }
}
