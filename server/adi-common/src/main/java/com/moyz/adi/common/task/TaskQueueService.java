package com.moyz.adi.common.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.entity.TaskQueue;
import com.moyz.adi.common.mapper.TaskQueueMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * S2-01（ADR-002）：task_queue 生产与生命周期管理。
 * <p>
 * {@link #submit} 是 Outbox 入口：<b>不</b>开 REQUIRES_NEW、<b>不</b>自建事务，
 * 直接 INSERT 进调用方的业务事务——业务回滚时任务行一起回滚，天然一致。
 */
@Slf4j
@Service
public class TaskQueueService {

    /** 首次重试退避 30s，此后翻倍（30s → 60s → 120s …），上限 {@link #MAX_BACKOFF_SECONDS} */
    static final long BACKOFF_BASE_SECONDS = 30;
    static final long MAX_BACKOFF_SECONDS = 30 * 60;

    @Resource
    private TaskQueueMapper taskQueueMapper;

    /**
     * 事务内提交任务（Outbox）。status/retry 等初始值由 DB 默认值提供，
     * Java 端不显式赋值，避免与 DDL 默认值两处漂移。
     */
    public Long submit(String taskType, JsonNode payload, int priority, Long createdBy) {
        TaskQueue task = new TaskQueue();
        task.setTaskType(taskType);
        task.setPayload(payload);
        task.setPriority(priority);
        task.setCreatedBy(createdBy);
        taskQueueMapper.insert(task);
        log.info("Task submitted, id:{}, type:{}, priority:{}", task.getId(), taskType, priority);
        return task.getId();
    }

    /** SKIP LOCKED 抢占一条到期任务，无到期任务返回 null */
    public TaskQueue claimNext(String ownerInstance) {
        return taskQueueMapper.claimNext(ownerInstance);
    }

    public boolean markSuccess(Long id) {
        return taskQueueMapper.markSuccess(id) > 0;
    }

    /**
     * 失败处理：retry_count+1 后未达 max_retry → 回 PENDING 并按指数退避；
     * 达到 max_retry → DEAD（终态）。基于 claim 时返回的行内计数判断——
     * 同一条 RUNNING 任务仅被抢到的实例处理，无并发竞争。
     */
    public boolean fail(TaskQueue claimed, String error) {
        int newCount = claimed.getRetryCount() + 1;
        if (newCount >= claimed.getMaxRetry()) {
            boolean ok = taskQueueMapper.markDead(claimed.getId(), newCount, error) > 0;
            log.warn("Task dead after {} attempts, id:{}, type:{}, error:{}", newCount,
                    claimed.getId(), claimed.getTaskType(), error);
            return ok;
        }
        long backoff = Math.min(BACKOFF_BASE_SECONDS * (1L << Math.min(newCount - 1, 20)), MAX_BACKOFF_SECONDS);
        boolean ok = taskQueueMapper.markRetry(claimed.getId(), newCount, LocalDateTime.now().plusSeconds(backoff), error) > 0;
        log.warn("Task retry scheduled, id:{}, type:{}, attempt:{}/{} backoff:{}s, error:{}",
                claimed.getId(), claimed.getTaskType(), newCount, claimed.getMaxRetry(), backoff, error);
        return ok;
    }

    /**
     * S2-02：资源忙延后（defer）。回 PENDING 但 retry_count<b>不前进</b>——
     * 任务没有失败，只是暂时不能执行；永不因 defer 进 DEAD。
     */
    public boolean defer(TaskQueue claimed, long deferSeconds, String reason) {
        boolean ok = taskQueueMapper.markRetry(claimed.getId(), claimed.getRetryCount(),
                LocalDateTime.now().plusSeconds(deferSeconds), reason) > 0;
        log.info("Task deferred (busy), id:{}, type:{}, retry stays {}/{}, defer:{}s, reason:{}",
                claimed.getId(), claimed.getTaskType(), claimed.getRetryCount(), claimed.getMaxRetry(),
                deferSeconds, reason);
        return ok;
    }

    public int heartbeat(String ownerInstance) {
        return taskQueueMapper.heartbeatByOwner(ownerInstance);
    }

    public int requeueOrphans(int timeoutSeconds) {
        int requeued = taskQueueMapper.requeueOrphans(timeoutSeconds);
        if (requeued > 0) {
            log.warn("Orphan tasks requeued, count:{}, timeoutSeconds:{}", requeued, timeoutSeconds);
        }
        return requeued;
    }

    /** admin 手工重试：DEAD/FAILED → PENDING 计数清零 */
    public boolean retryDead(Long id) {
        return taskQueueMapper.requeueForRetry(id) > 0;
    }

    /** admin 终止：PENDING/RUNNING → DEAD */
    public boolean terminate(Long id) {
        return taskQueueMapper.terminate(id) > 0;
    }

    /** admin 分页查询：按类型/状态过滤，update_time 倒序 */
    public Page<TaskQueue> page(String taskType, String status, long current, long size) {
        LambdaQueryWrapper<TaskQueue> wrapper = new LambdaQueryWrapper<TaskQueue>()
                .eq(StringUtils.isNotBlank(taskType), TaskQueue::getTaskType, taskType)
                .eq(StringUtils.isNotBlank(status), TaskQueue::getStatus, status)
                .orderByDesc(TaskQueue::getUpdateTime);
        return taskQueueMapper.selectPage(new Page<>(current, size), wrapper);
    }
}
