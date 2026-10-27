package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.TaskQueue;
import com.moyz.adi.common.task.TaskQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S2-01：task_queue 管理端——查询 / 手工重试 / 终止。
 * 数据访问收敛在 {@link TaskQueueService}（S1-01：controller 禁止直查 mapper）。
 */
@Tag(name = "任务队列 | Task Queue", description = "统一异步任务队列管理 | Unified async task queue")
@RestController
@RequestMapping("/admin/task-queue")
@AdminOnly
public class AdminTaskQueueController {

    @Resource
    private TaskQueueService taskQueueService;

    @Operation(summary = "任务分页 | Paged task list",
            description = "按类型/状态过滤，update_time 倒序 | Filter by type/status, ordered by update_time desc")
    @GetMapping("/page")
    public Page<TaskQueue> page(@RequestParam(required = false) String taskType,
                                @RequestParam(required = false) String status,
                                @RequestParam(defaultValue = "1") long current,
                                @RequestParam(defaultValue = "20") long size) {
        return taskQueueService.page(taskType, status, current, size);
    }

    @Operation(summary = "手工重试 | Manual retry",
            description = "DEAD/FAILED → PENDING，重试计数清零 | DEAD/FAILED back to PENDING with retry_count reset")
    @PostMapping("/{id}/retry")
    public boolean retry(@PathVariable Long id) {
        return taskQueueService.retryDead(id);
    }

    @Operation(summary = "终止任务 | Terminate",
            description = "PENDING/RUNNING → DEAD | PENDING/RUNNING to DEAD")
    @PostMapping("/{id}/terminate")
    public boolean terminate(@PathVariable Long id) {
        return taskQueueService.terminate(id);
    }
}
