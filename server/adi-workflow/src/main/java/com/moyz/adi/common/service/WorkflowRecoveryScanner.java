package com.moyz.adi.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 业务编排 Harness M2-T4（L1 启动扫描恢复）：进程启动后扫描停驻态孤儿运行实例。
 * <p>
 * 进程重启后内存注册表（InterruptedFlow/PausedFlow）必然为空，所有 WAITING_INPUT/
 * PAUSED 实例都失去了进程内恢复能力。本扫描器只做标记与日志（不自动续跑——暂停是
 * 用户主动行为，自动恢复可能违背用户意图），用户点「继续」按钮即走 L2 重建路径。
 */
@Slf4j
@Component
public class WorkflowRecoveryScanner implements ApplicationRunner {

    private final WorkflowRuntimeService workflowRuntimeService;

    public WorkflowRecoveryScanner(WorkflowRuntimeService workflowRuntimeService) {
        this.workflowRuntimeService = workflowRuntimeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int marked = workflowRuntimeService.markOrphanedParkedAfterRestart();
            if (marked > 0) {
                log.info("WorkflowRecoveryScanner: marked {} orphaned parked runtime(s) after restart", marked);
            }
        } catch (Throwable e) {
            //扫描失败不阻断应用启动
            log.error("WorkflowRecoveryScanner failed (non-blocking)", e);
        }
    }
}
