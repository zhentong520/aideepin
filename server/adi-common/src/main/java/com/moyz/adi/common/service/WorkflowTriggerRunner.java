package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

/**
 * T9-01：触发派发后的异步执行器（复用 workflowExecutor 线程池）。
 * 定时触发执行前做错峰抖动（adi.wf.trigger-stagger-seconds）；执行结果
 * （output/status）与来源（trigger_source）落运行历史。
 */
@Slf4j
@Component
public class WorkflowTriggerRunner {

    @Lazy
    @Resource
    private WorkflowTriggerRunner self;

    @Resource
    private WorkflowTriggerService workflowTriggerService;

    /**
     * 定时触发执行：错峰抖动 → 阻塞运行（trigger_source=schedule）。
     */
    @Async("workflowExecutor")
    public void asyncFireSchedule(Long triggerId, String workflowUuid, ArrayNode inputs) {
        try {
            workflowTriggerService.staggerSleep();
        } catch (Exception e) {
            log.warn("stagger sleep failed, ignore:{}", e.getMessage());
        }
        self.doRun(triggerId, workflowUuid, inputs, "schedule");
    }

    /**
     * webhook 触发执行：阻塞运行（trigger_source=webhook）。
     */
    @Async("workflowExecutor")
    public void asyncFireWebhook(String workflowUuid, ArrayNode inputs) {
        self.doRun(null, workflowUuid, inputs, "webhook");
    }

    /**
     * 内部 API 触发执行：阻塞运行（trigger_source=api）。
     */
    @Async("workflowExecutor")
    public void asyncFireApi(String workflowUuid, ArrayNode inputs) {
        self.doRun(null, workflowUuid, inputs, "api");
    }

    /**
     * 线程池饱和时拒绝：调用方（限流/webhook 场景）需捕获并自行兜底，
     * 这里只记日志——拒绝的触发不推进 last_fire_time，下一轮询会重试。
     */
    public boolean trySubmit(Runnable task) {
        try {
            task.run();
            return true;
        } catch (RejectedExecutionException e) {
            log.warn("workflowExecutor saturated, trigger task rejected");
            return false;
        }
    }

    void doRun(Long triggerId, String workflowUuid, ArrayNode inputs, String triggerSource) {
        workflowTriggerService.runTriggered(triggerId, workflowUuid, inputs, triggerSource);
    }
}
