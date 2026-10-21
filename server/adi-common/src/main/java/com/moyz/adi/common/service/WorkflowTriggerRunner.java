package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowTrigger;
import com.moyz.adi.common.mapper.WorkflowTriggerMapper;
import com.moyz.adi.common.workflow.WorkflowStarter;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/**
 * T9-01：触发派发后的异步执行器（复用 workflowExecutor 线程池）。
 * 定时触发执行前做错峰抖动（adi.wf.trigger-stagger-seconds）；执行结果
 * （output/status）与来源（trigger_source）落运行历史。
 *
 * <p>2026-10-21 修：原实现让本类依赖 {@link WorkflowTriggerService}、而 Service 又依赖本类，
 * 形成 Bean 循环依赖，Spring 启动直接失败（APPLICATION FAILED TO START: cycle）。
 * 现把「执行侧」职责全部下沉到本类（runTriggered / staggerSleep / markFireStatus），
 * Service 只负责配置管理与派发，依赖方向单向：Service → Runner。
 */
@Slf4j
@Component
public class WorkflowTriggerRunner {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Lazy
    @Resource
    private WorkflowTriggerRunner self;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private UserService userService;

    @Resource
    private WorkflowStarter workflowStarter;

    @Resource
    private WorkflowTriggerMapper workflowTriggerMapper;

    @Resource
    private AdiProperties adiProperties;

    /**
     * 定时触发执行：错峰抖动 → 阻塞运行（trigger_source=schedule）。
     */
    @Async("workflowExecutor")
    public void asyncFireSchedule(Long triggerId, String workflowUuid, ArrayNode inputs) {
        try {
            staggerSleep();
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
        runTriggered(triggerId, workflowUuid, inputs, triggerSource);
    }

    /**
     * 派发后的实际执行（workflowExecutor 线程调用）：
     * 以工作流属主身份阻塞运行，trigger_source 落运行记录；失败更新 last_fire_status。
     * 包私有以便单测。
     */
    void runTriggered(Long triggerId, String workflowUuid, ArrayNode inputs, String triggerSource) {
        try {
            Workflow workflow = workflowService.getByUuid(workflowUuid);
            if (null == workflow) {
                log.error("Triggered run aborted, workflow not found:{}", workflowUuid);
                return;
            }
            //触发场景无登录态，以属主身份执行；属主缺失则放弃并记录
            User ownerUser = userService.getById(workflow.getUserId());
            if (null == ownerUser) {
                log.error("Triggered run aborted, workflow owner missing, workflowUuid:{}", workflowUuid);
                markFireStatus(triggerId, WorkflowTriggerService.FIRE_STATUS_FAILED);
                return;
            }
            List<ObjectNode> userInputList = new ArrayList<>();
            if (null != inputs) {
                inputs.forEach(item -> userInputList.add((ObjectNode) item));
            }
            workflowStarter.blocking(ownerUser, workflowUuid, userInputList, triggerSource);
        } catch (Exception e) {
            log.error("Triggered run failed, workflowUuid:{}, source:{}", workflowUuid, triggerSource, e);
            if (null != triggerId) {
                markFireStatus(triggerId, WorkflowTriggerService.FIRE_STATUS_FAILED);
            }
        }
    }

    /**
     * 错峰抖动（adi.wf.trigger-stagger-seconds）：派发后执行前随机等待 0~N 秒，
     * 避免大量工作流整点同时起跑。在 async 线程里 sleep，不阻塞轮询线程。
     */
    public void staggerSleep() {
        int max = adiProperties.getWf().getTriggerStaggerSeconds();
        if (max <= 0) {
            return;
        }
        try {
            long millis = RANDOM.nextInt(max) * 1000L;
            log.info("Trigger stagger sleep {}ms", millis);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 更新触发的上次派发状态（Service 侧派发失败时也走这里，依赖方向保持 Service → Runner 单向）。
     */
    public void markFireStatus(Long triggerId, int status) {
        if (null == triggerId) {
            return;
        }
        ChainWrappers.lambdaUpdateChain(workflowTriggerMapper)
                .eq(WorkflowTrigger::getId, triggerId)
                .set(WorkflowTrigger::getLastFireStatus, status)
                .update();
    }
}
