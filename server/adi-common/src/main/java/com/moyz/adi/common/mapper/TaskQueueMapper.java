package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.TaskQueue;
import org.apache.ibatis.annotations.Param;

/**
 * S2-01：task_queue 数据访问。抢占等原子语句在 XML（TaskQueueMapper.xml）。
 */
public interface TaskQueueMapper extends BaseMapper<TaskQueue> {

    /**
     * SKIP LOCKED 原子抢占一条到期任务：UPDATE..RETURNING 一步完成
     * 「锁定候选行 → 置 RUNNING → 记 owner」，多实例并发调用互不重复。
     */
    TaskQueue claimNext(@Param("ownerInstance") String ownerInstance);

    /** 本实例 RUNNING 任务心跳（update_time 由 BEFORE UPDATE 触发器自动刷新） */
    int heartbeatByOwner(@Param("ownerInstance") String ownerInstance);

    /** 超过 timeoutSeconds 无心跳的 RUNNING 任务重回 PENDING（孤儿回收），返回回收行数 */
    int requeueOrphans(@Param("timeoutSeconds") int timeoutSeconds);

    int markSuccess(@Param("id") Long id);

    /** 退避重试：置回 PENDING 并记录 retry_count / next_run_at / last_error */
    int markRetry(@Param("id") Long id,
                  @Param("retryCount") int retryCount,
                  @Param("nextRunAt") java.time.LocalDateTime nextRunAt,
                  @Param("lastError") String lastError);

    /** 重试耗尽转 DEAD（终态，只能经 admin 手工 retry 复活） */
    int markDead(@Param("id") Long id, @Param("retryCount") int retryCount, @Param("lastError") String lastError);

    /** admin 手工重试：DEAD/FAILED → PENDING，计数清零 */
    int requeueForRetry(@Param("id") Long id);

    /** admin 终止：PENDING/RUNNING → DEAD */
    int terminate(@Param("id") Long id);
}
