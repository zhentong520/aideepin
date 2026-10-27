package com.moyz.adi.common.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.entity.TaskQueue;
import com.moyz.adi.common.mapper.TaskQueueMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2-01：退避重试与 DEAD 判定。
 */
class TaskQueueServiceTest {

    private TaskQueueService taskQueueService;
    private TaskQueueMapper taskQueueMapper;

    @BeforeEach
    void setUp() {
        taskQueueService = new TaskQueueService();
        taskQueueMapper = mock(TaskQueueMapper.class);
        ReflectionTestUtils.setField(taskQueueService, "taskQueueMapper", taskQueueMapper);
        // fail() 返回 markRetry/markDead 影响行数 > 0，mock 默认 0 会让断言失败
        when(taskQueueMapper.markRetry(anyLong(), anyInt(), any(LocalDateTime.class), anyString())).thenReturn(1);
        when(taskQueueMapper.markDead(anyLong(), anyInt(), anyString())).thenReturn(1);
    }

    @Test
    void submitInsertsWithPriorityAndKeepsDbDefaultsForStatus() {
        when(taskQueueMapper.insert(any(TaskQueue.class))).thenAnswer(inv -> {
            TaskQueue t = inv.getArgument(0);
            t.setId(99L);
            return 1;
        });

        Long id = taskQueueService.submit("KB_INDEX", new ObjectMapper().valueToTree(java.util.Map.of("a", 1)), 7, 42L);

        assertEquals(99L, id);
        ArgumentCaptor<TaskQueue> captor = ArgumentCaptor.forClass(TaskQueue.class);
        verify(taskQueueMapper).insert(captor.capture());
        assertEquals("KB_INDEX", captor.getValue().getTaskType());
        assertEquals(7, captor.getValue().getPriority());
        // status/retry 等初始值留给 DB 默认值，Java 侧不显式赋值
        assertEquals(null, captor.getValue().getStatus());
    }

    @Test
    void failSchedulesFirstRetryWith30sBackoff() {
        TaskQueue claimed = claimed(0, 3);
        LocalDateTime before = LocalDateTime.now();

        assertTrue(taskQueueService.fail(claimed, "boom"));

        ArgumentCaptor<LocalDateTime> nextRun = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskQueueMapper).markRetry(eq(claimed.getId()), eq(1), nextRun.capture(), eq("boom"));
        // 第一次重试退避约 30s
        assertTrue(nextRun.getValue().isAfter(before.plusSeconds(25)));
        assertTrue(nextRun.getValue().isBefore(before.plusSeconds(35)));
        verify(taskQueueMapper, never()).markDead(anyLong(), anyInt(), anyString());
    }

    @Test
    void failBackoffDoublesEachAttempt() {
        TaskQueue claimed = claimed(1, 3);
        LocalDateTime before = LocalDateTime.now();

        taskQueueService.fail(claimed, "boom");

        ArgumentCaptor<LocalDateTime> nextRun = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskQueueMapper).markRetry(eq(claimed.getId()), eq(2), nextRun.capture(), eq("boom"));
        // 第二次重试退避约 60s
        assertTrue(nextRun.getValue().isAfter(before.plusSeconds(55)));
        assertTrue(nextRun.getValue().isBefore(before.plusSeconds(65)));
    }

    @Test
    void failTurnsDeadWhenRetryExhausted() {
        TaskQueue claimed = claimed(2, 3);

        assertTrue(taskQueueService.fail(claimed, "boom"));

        verify(taskQueueMapper).markDead(claimed.getId(), 3, "boom");
        verify(taskQueueMapper, never()).markRetry(anyLong(), anyInt(), any(), anyString());
    }

    @Test
    void failBackoffIsCappedAt30Minutes() {
        TaskQueue claimed = claimed(10, 20);
        LocalDateTime before = LocalDateTime.now();

        taskQueueService.fail(claimed, "boom");

        ArgumentCaptor<LocalDateTime> nextRun = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(taskQueueMapper).markRetry(eq(claimed.getId()), eq(11), nextRun.capture(), eq("boom"));
        // 指数退避封顶 30 分钟（否则 2^n 溢出/天文数字）
        assertTrue(nextRun.getValue().isBefore(before.plusMinutes(31)));
    }

    @Test
    void deferSchedulesRerunWithoutAdvancingRetryCount() {
        TaskQueue claimed = claimed(1, 3);
        LocalDateTime before = LocalDateTime.now();

        assertTrue(taskQueueService.defer(claimed, 60, "same-doc busy"));

        ArgumentCaptor<LocalDateTime> nextRun = ArgumentCaptor.forClass(LocalDateTime.class);
        // retry_count 原样保留（1 不变）——defer 是资源忙，不是失败
        verify(taskQueueMapper).markRetry(eq(claimed.getId()), eq(1), nextRun.capture(), eq("same-doc busy"));
        assertTrue(nextRun.getValue().isAfter(before.plusSeconds(55)));
        assertTrue(nextRun.getValue().isBefore(before.plusSeconds(65)));
        verify(taskQueueMapper, never()).markDead(anyLong(), anyInt(), anyString());
    }

    @Test
    void deferNeverTurnsDeadEvenAtRetryBudgetEdge() {
        // 已到 max_retry-1：defer 也绝不推进计数进 DEAD，否则串行排队中的任务会被误杀
        TaskQueue claimed = claimed(2, 3);

        assertTrue(taskQueueService.defer(claimed, 60, "busy"));

        verify(taskQueueMapper).markRetry(eq(claimed.getId()), eq(2), any(LocalDateTime.class), anyString());
        verify(taskQueueMapper, never()).markDead(anyLong(), anyInt(), anyString());
    }

    private TaskQueue claimed(int retryCount, int maxRetry) {
        TaskQueue task = new TaskQueue();
        task.setId(7L);
        task.setTaskType("KB_INDEX");
        task.setStatus(TaskQueue.STATUS_RUNNING);
        task.setRetryCount(retryCount);
        task.setMaxRetry(maxRetry);
        return task;
    }
}
