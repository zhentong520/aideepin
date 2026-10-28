package com.moyz.adi.common.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.service.DrawService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * S2-03：绘图任务的 task_queue handler（ADR-002 统一队列第二个迁入的执行体）。
 * payload: {"drawId": <adi_draw 主键>}——draw 行自身承载 process_status（doing/success/fail）
 * 领域状态，task_queue 只负责「何时、在哪台实例上执行」。
 * <p>
 * 幂等性：{@code DrawService#executeById} 对已终态（success/fail）的 draw 直接返回 ok，
 * 孤儿回收重跑不会重复生成、不会重复扣配额；doing 状态重跑即幂等续跑（重启自愈）。
 */
@Component
public class DrawTaskHandler implements TaskHandler {

    /** 与 DrawService.TASK_TYPE_DRAW 一致（互引会造成不必要耦合，此处独立声明） */
    public static final String TYPE = "DRAW";

    @Resource
    private DrawService drawService;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public TaskResult handle(JsonNode payload) {
        return drawService.executeById(payload.get("drawId").asLong());
    }
}
