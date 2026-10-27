package com.moyz.adi.common.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.service.IndexTaskService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * S2-02：KB 索引任务的 task_queue handler（第一个迁入 ADR-002 统一队列的执行体）。
 * payload: {"indexTaskId": <adi_index_task 主键>}——状态与进度仍由 index_task 行承载
 * （前端轮询不受影响），task_queue 只负责「何时、在哪台实例上执行」。
 * 幂等性：executeById 的 claimById 只认 pending 行，重复投递/孤儿重跑不会重复执行。
 */
@Component
public class IndexTaskHandler implements TaskHandler {

    /** 与 IndexTaskService.TASK_TYPE_KB_INDEX 一致（互引会造成不必要耦合，此处独立声明） */
    public static final String TYPE = "KB_INDEX";

    @Resource
    private IndexTaskService indexTaskService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public TaskResult handle(JsonNode payload) {
        return indexTaskService.executeById(payload.get("indexTaskId").asLong());
    }
}
