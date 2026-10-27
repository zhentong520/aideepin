package com.moyz.adi.common.base;

import com.moyz.adi.common.service.WorkflowTriggerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T9-01：定时触发轮询调度器。每 30s 调 {@link WorkflowTriggerService#pollAndDispatch()}：
 * <ul>
 *   <li>总开关 adi.wf.trigger-enabled=false 时直接返回（铁律 10 热回滚位）；</li>
 *   <li>cron 到期（nextFireTime <= now）才派发；Redis 派发锁防多实例重复派发；</li>
 *   <li>上次未跑完（存在 DOING 运行记录）不重复触发；</li>
 *   <li>错峰：派发后在异步执行线程内做 0~N 秒抖动（adi.wf.trigger-stagger-seconds）。</li>
 * </ul>
 * 复用既有 @EnableScheduling 设施（BootstrapApplication），与 Jobs/IndexTaskService 同机制。
 */
@Slf4j
@Component
public class WorkflowTriggerScheduler {

    @Resource
    private WorkflowTriggerService workflowTriggerService;

    /**
     * initialDelay 60s：避开应用启动高峰（索引任务/缓存预热）。
     */
    @Scheduled(initialDelay = 60_000, fixedDelay = 30_000)
    @SchedulerLock(name = "WorkflowTriggerScheduler_poll", lockAtMostFor = "5m")
    public void poll() {
        try {
            workflowTriggerService.pollAndDispatch();
        } catch (Exception e) {
            //轮询本身绝不抛出：调度线程挂了所有触发器都停摆
            log.error("Workflow trigger poll failed", e);
        }
    }
}
