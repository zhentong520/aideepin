package com.moyz.adi.common.dto.workflow;

import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * T9-01：触发配置保存请求。uuid 空 = 新建，非空 = 更新。
 */
@Data
public class WfTriggerSaveReq {
    /** 新建必填：归属工作流 */
    private String workflowUuid;
    /** 更新必填：触发器 uuid */
    private String uuid;
    @NotBlank
    private String triggerType;
    /** schedule 类型必填：Spring 6 CronExpression（6 域） */
    private String cronExpr;
    /** 触发时默认入参（与 /workflow/run inputs 同构） */
    private ArrayNode inputParams;
    private Boolean isEnable;
}
