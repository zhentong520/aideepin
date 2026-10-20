package com.moyz.adi.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * T9-01：工作流触发配置 DTO。
 */
@Data
public class WfTriggerDto {
    private Long id;
    private String uuid;
    private String workflowUuid;
    private String triggerType;
    private String cronExpr;
    /** 完整 webhook URL（仅 webhook 类型填充） */
    private String webhookUrl;
    private ArrayNode inputParams;
    private Boolean isEnable;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastFireTime;
    /** 0=未触发 1=已派发 2=派发失败 */
    private Integer lastFireStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
