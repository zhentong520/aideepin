package com.moyz.adi.common.task;

import com.moyz.adi.common.entity.TaskQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * S2-01（ADR-002）：task_queue 消费引擎。
 * <p>
 * 轮询抢占（{@code FOR UPDATE SKIP LOCKED}，多实例安全——正确性由 SKIP LOCKED 保证，
 * ShedLock 按 S1-07a 治理规则统一加锁防重，锁在方法完成即释放，不损失跨实例吞吐）
 * → 提交 {@code taskQueueExecutor} 执行 → 成功/退避重试。
 * 心跳与孤儿回收独立调度。handler 由 Spring 自动发现注册（{@link TaskHandler}）。
 */
@Slf4j
@Component
public class TaskQueueConsumer {

    /** 每轮最多认领数：调度线程只做抢占与分发，不执行 handler */
    static final int MAX_CLAIM_PER_POLL = 16;
    /** 孤儿判定阈值：RUNNING 超过 10 分钟无心跳即回收（远大于任何合法 handler 心跳间隔） */
    static final int ORPHAN_TIMEOUT_SECONDS = 600;

    private final TaskQueueService taskQueueService;
    private final AsyncTaskExecutor taskQueueExecutor;
    private final Map<String, TaskHandler> handlers;
    private final String ownerInstance;

    public TaskQueueConsumer(TaskQueueService taskQueueService,
                             @Qualifier("taskQueueExecutor") AsyncTaskExecutor taskQueueExecutor,
                             @Value("${adi.host:localhost}") String host,
                             List<TaskHandler> handlerList) {
        this.taskQueueService = taskQueueService;
        this.taskQueueExecutor = taskQueueExecutor;
        this.handlers = handlerList.stream()
                .collect(Collectors.toUnmodifiableMap(TaskHandler::type, Function.identity()));
        this.ownerInstance = resolveOwnerInstance(host);
        log.info("TaskQueueConsumer started, owner:{}, handler types:{}", ownerInstance, handlers.keySet());
    }

    /**
     * 消费轮询：每轮抢占至多 {@link #MAX_CLAIM_PER_POLL} 条，空轮即返回。
     * fixedDelay 串行防重入（同方法不会并发），跨实例并发由 SKIP LOCKED 保证。
     */
    @Scheduled(fixedDelay = 2000)
    @SchedulerLock(name = "TaskQueueConsumer_pollOnce", lockAtMostFor = "1m")
    public void pollOnce() {
        for (int i = 0; i < MAX_CLAIM_PER_POLL; i++) {
            TaskQueue task;
            try {
                task = taskQueueService.claimNext(ownerInstance);
            } catch (Exception e) {
                log.error("Claim task failed", e);
                return;
            }
            if (null == task) {
                return;
            }
            dispatch(task);
        }
    }

    /** 本实例 RUNNING 任务心跳：60s 一次，刷新 update_time 证明进程存活 */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "TaskQueueConsumer_heartbeat", lockAtMostFor = "5m")
    public void heartbeat() {
        try {
            taskQueueService.heartbeat(ownerInstance);
        } catch (Exception e) {
            log.warn("Task heartbeat failed", e);
        }
    }

    /** 孤儿回收：kill -9 等场景下 RUNNING 无心跳超 10 分钟的任务重回 PENDING */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "TaskQueueConsumer_requeueOrphans", lockAtMostFor = "5m")
    public void requeueOrphans() {
        try {
            taskQueueService.requeueOrphans(ORPHAN_TIMEOUT_SECONDS);
        } catch (Exception e) {
            log.warn("Orphan requeue failed", e);
        }
    }

    void dispatch(TaskQueue task) {
        TaskHandler handler = handlers.get(task.getTaskType());
        if (null == handler) {
            // 未注册类型重试无意义，直接 DEAD（提交侧应校验 type，此处兜底）
            taskQueueService.fail(task, "no handler registered for type " + task.getTaskType());
            return;
        }
        try {
            taskQueueExecutor.execute(() -> execute(task, handler));
        } catch (RejectedExecutionException e) {
            // 池满（AbortPolicy）：立即退避重试，避免任务卡 RUNNING 到孤儿超时
            taskQueueService.fail(task, "consumer pool saturated: " + e.getMessage());
        }
    }

    private void execute(TaskQueue task, TaskHandler handler) {
        try {
            TaskResult result = handler.handle(task.getPayload());
            if (result.success()) {
                taskQueueService.markSuccess(task.getId());
            } else {
                taskQueueService.fail(task, result.error());
            }
        } catch (Exception e) {
            log.error("Task handler threw, id:{}, type:{}", task.getId(), task.getTaskType(), e);
            taskQueueService.fail(task, String.valueOf(e));
        }
    }

    private static String resolveOwnerInstance(String host) {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = host;
        }
        return hostname + ":" + ProcessHandle.current().pid();
    }
}
