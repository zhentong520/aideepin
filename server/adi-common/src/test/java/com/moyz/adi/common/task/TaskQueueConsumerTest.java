package com.moyz.adi.common.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.entity.TaskQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2-01：消费引擎分发行为（handler 分发 / 无 handler 兜底 / 池满退避 / 空轮停止）。
 */
class TaskQueueConsumerTest {

    private TaskQueueService taskQueueService;
    private AsyncTaskExecutor executor;
    private TaskQueueConsumer consumer;

    @BeforeEach
    void setUp() {
        taskQueueService = mock(TaskQueueService.class);
        executor = mock(AsyncTaskExecutor.class);
        consumer = new TaskQueueConsumer(taskQueueService, executor, "localhost", List.of(new EchoHandler()));
    }

    @Test
    void pollOnceStopsWhenNoClaimableTask() {
        when(taskQueueService.claimNext(anyString())).thenReturn(null);

        consumer.pollOnce();

        // 只探测一次即返回，不空转 MAX_CLAIM_PER_POLL 次
        verify(taskQueueService, times(1)).claimNext(anyString());
    }

    @Test
    void pollOnceDrainsUntilClaimReturnsNull() {
        when(taskQueueService.claimNext(anyString())).thenReturn(task("T1"), task("T1"), null);

        consumer.pollOnce();

        verify(taskQueueService, times(3)).claimNext(anyString());
    }

    @Test
    void dispatchRunsHandlerAndMarksSuccess() {
        TaskQueue t = task("ECHO");
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        doAnswer(inv -> {
            submitted.set(inv.getArgument(0));
            return null;
        }).when(executor).execute(any(Runnable.class));

        consumer.dispatch(t);

        // claim 线程只提交，handler 在 executor 线程执行
        submitted.get().run();
        verify(taskQueueService).markSuccess(t.getId());
        verify(taskQueueService, never()).fail(any(TaskQueue.class), anyString());
    }

    @Test
    void dispatchUnregisteredTypeGoesStraightToDead() {
        TaskQueue t = task("NO_SUCH_TYPE");

        consumer.dispatch(t);

        // 未注册类型重试无意义 → 直接走 fail（内部按退避/DEAD 判定）；不得占用 executor
        verify(taskQueueService).fail(eq(t), contains("no handler registered for type NO_SUCH_TYPE"));
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void dispatchOnSaturatedPoolSchedulesRetryInsteadOfHanging() {
        TaskQueue t = task("ECHO");
        doThrow(new RejectedExecutionException("pool full")).when(executor).execute(any(Runnable.class));

        consumer.dispatch(t);

        verify(taskQueueService).fail(eq(t), contains("saturated"));
    }

    @Test
    void handlerFailureIsTranslatedToBackoffRetry() {
        TaskQueue t = task("ECHO");
        TaskQueueConsumer failing = new TaskQueueConsumer(taskQueueService, executor, "localhost",
                List.of(new TaskHandler() {
                    @Override
                    public String type() {
                        return "ECHO";
                    }

                    @Override
                    public TaskResult handle(JsonNode payload) {
                        throw new IllegalStateException("embedding endpoint down");
                    }
                }));
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));

        failing.dispatch(t);

        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(taskQueueService).fail(any(TaskQueue.class), error.capture());
        assertEquals(true, error.getValue().contains("embedding endpoint down"));
    }

    private TaskQueue task(String type) {
        TaskQueue task = new TaskQueue();
        task.setId(11L);
        task.setTaskType(type);
        task.setStatus(TaskQueue.STATUS_RUNNING);
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setPayload(new ObjectMapper().valueToTree(java.util.Map.of("k", "v")));
        return task;
    }

    /** 测试用 handler：返回成功 */
    private static class EchoHandler implements TaskHandler {
        @Override
        public String type() {
            return "ECHO";
        }

        @Override
        public TaskResult handle(JsonNode payload) {
            return TaskResult.ok();
        }
    }
}
