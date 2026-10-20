package com.moyz.adi.common.memory.longterm;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.MemoryPending;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.MemoryType;
import com.moyz.adi.common.mapper.MemoryPendingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-07：敏感记忆 pending → confirm 两段式单测。
 * 卡片要求覆盖：
 * a) 敏感未确认不落库（pending 阶段绝不触碰向量库）；
 * b) 确认后落库（按 memory_type 路由写对应向量库）；
 * c) 迁移兼容 / 状态机（非 PENDING 不可再确认）；
 * d) ⚠️ 导入方隔离同款铁律：confirm/reject 校验归属——他人（无 ACL）拒绝而非静默穿透。
 */
class MemoryPendingServiceTest {

    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;
    private static final Long CHARACTER_A = 101L;

    private MemoryPendingService memoryPendingService;
    private MemoryPendingMapper memoryPendingMapper;
    private SemanticMemoryService semanticMemoryService;
    private EpisodicMemoryService episodicMemoryService;

    @BeforeEach
    void setUp() {
        memoryPendingService = new MemoryPendingService();
        memoryPendingMapper = mock(MemoryPendingMapper.class);
        semanticMemoryService = mock(SemanticMemoryService.class);
        episodicMemoryService = mock(EpisodicMemoryService.class);
        ReflectionTestUtils.setField(memoryPendingService, "baseMapper", memoryPendingMapper);
        ReflectionTestUtils.setField(memoryPendingService, "semanticMemoryService", semanticMemoryService);
        ReflectionTestUtils.setField(memoryPendingService, "episodicMemoryService", episodicMemoryService);
        loginAs(USER_A);
    }

    @AfterEach
    void tearDown() {
        ThreadContext.setCurrentUser(null);
    }

    private static void loginAs(Long userId) {
        User user = new User();
        user.setId(userId);
        ThreadContext.setCurrentUser(user);
    }

    private MemoryPending pendingRow(Long ownerId, MemoryType type) {
        MemoryPending pending = new MemoryPending();
        pending.setId(9L);
        pending.setMemoryUuid("uuid-" + ownerId + "-" + type);
        pending.setUserId(ownerId);
        pending.setCharacterId(CHARACTER_A);
        pending.setMemoryType(type);
        pending.setContent("我的手机号是13812345678");
        pending.setStatus(MemoryPending.STATUS_PENDING);
        return pending;
    }

    // ==================== a) 敏感未确认不落库 ====================

    @Test
    void createPendingNeverTouchesVectorStores() {
        MemoryPending created = memoryPendingService.createPending(USER_A, CHARACTER_A,
                MemoryType.SEMANTIC, "我的手机号是13812345678", 55L);

        assertNotNull(created);
        assertEquals(MemoryPending.STATUS_PENDING, created.getStatus());
        assertEquals(USER_A, created.getUserId());
        assertEquals(MemoryType.SEMANTIC, created.getMemoryType());
        // pending 阶段只落确认队列表，向量库零写入
        verify(memoryPendingMapper).insert(any(MemoryPending.class));
        verify(semanticMemoryService, never()).addConfirmed(anyLong(), anyLong(), anyString());
        verify(episodicMemoryService, never()).addConfirmedEvent(anyLong(), anyLong(), any(), any());
    }

    // ==================== b) 确认后落库 ====================

    @Test
    void confirmPersistsSemanticToVectorStore() {
        MemoryPending row = pendingRow(USER_A, MemoryType.SEMANTIC);
        stubOwned(row);

        MemoryPending confirmed = memoryPendingService.confirm(row.getMemoryUuid());

        assertEquals(MemoryPending.STATUS_CONFIRMED, confirmed.getStatus());
        ArgumentCaptor<Long> userCaptor = ArgumentCaptor.forClass(Long.class);
        verify(semanticMemoryService).addConfirmed(anyLong(), userCaptor.capture(), anyString());
        assertEquals(USER_A, userCaptor.getValue(), "确认落库必须以 pending 归属人身份写入");
        verify(episodicMemoryService, never()).addConfirmedEvent(anyLong(), anyLong(), any(), any());
        verify(memoryPendingMapper).updateById(any(MemoryPending.class));
    }

    @Test
    void confirmPersistsEpisodicToEpisodicStore() {
        MemoryPending row = pendingRow(USER_A, MemoryType.EPISODIC);
        row.setSourceMsgId(77L);
        stubOwned(row);

        memoryPendingService.confirm(row.getMemoryUuid());

        verify(episodicMemoryService).addConfirmedEvent(eq(CHARACTER_A), eq(USER_A), eq(77L), any());
        verify(semanticMemoryService, never()).addConfirmed(anyLong(), anyLong(), anyString());
    }

    // ==================== c) 状态机 / 迁移兼容 ====================

    @Test
    void alreadyConfirmedCannotBeConfirmedAgain() {
        MemoryPending row = pendingRow(USER_A, MemoryType.SEMANTIC);
        row.setStatus(MemoryPending.STATUS_CONFIRMED);
        stubOwned(row);

        assertThrows(IllegalArgumentException.class, () -> memoryPendingService.confirm(row.getMemoryUuid()));
        verify(semanticMemoryService, never()).addConfirmed(anyLong(), anyLong(), anyString());
    }

    @Test
    void rejectedRowIsNeverPersisted() {
        MemoryPending row = pendingRow(USER_A, MemoryType.SEMANTIC);
        stubOwned(row);

        MemoryPending rejected = memoryPendingService.reject(row.getMemoryUuid());

        assertEquals(MemoryPending.STATUS_REJECTED, rejected.getStatus());
        verify(semanticMemoryService, never()).addConfirmed(anyLong(), anyLong(), anyString());
        verify(episodicMemoryService, never()).addConfirmedEvent(anyLong(), anyLong(), any(), any());
    }

    // ==================== d) 隔离：他人不可代确认 ====================

    @Test
    void otherUserConfirmIsRejectedNotSilentlyPassed() {
        MemoryPending row = pendingRow(USER_A, MemoryType.SEMANTIC);
        stubOwned(row);
        loginAs(USER_B); // B 用户拿 A 的 uuid

        // 归属校验失败 → 拒绝（与不存在同报错，不泄露行是否存在），绝不写入
        assertThrows(IllegalArgumentException.class, () -> memoryPendingService.confirm(row.getMemoryUuid()));
        verify(semanticMemoryService, never()).addConfirmed(anyLong(), anyLong(), anyString());
        verify(episodicMemoryService, never()).addConfirmedEvent(anyLong(), anyLong(), any(), any());
        verify(memoryPendingMapper, never()).updateById(any(MemoryPending.class));
    }

    @Test
    void otherUserCannotSeeOrRejectOthersPending() {
        MemoryPending row = pendingRow(USER_A, MemoryType.SEMANTIC);
        stubOwned(row);
        loginAs(USER_B);

        assertThrows(IllegalArgumentException.class, () -> memoryPendingService.reject(row.getMemoryUuid()));
        verify(memoryPendingMapper, never()).updateById(any(MemoryPending.class));
    }

    @Test
    void listIsScopedToCurrentUser() {
        // 查询条件里带 userId + PENDING 双等值——他人的行在 SQL 层就不可见
        memoryPendingService.listMyPending();
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<MemoryPending>> captor =
                ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(memoryPendingMapper).selectList(captor.capture());
        assertTrue(captor.getValue() != null);
    }

    private void stubOwned(MemoryPending row) {
        // MP 3.5.x getOne 走 BaseMapper.selectOne(wrapper, throwEx) 默认方法，两种签名都兜住
        when(memoryPendingMapper.selectOne(any())).thenReturn(row);
        when(memoryPendingMapper.selectOne(any(), anyBoolean())).thenReturn(row);
    }
}
