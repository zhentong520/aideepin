package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * S2-01（ADR-002）：统一异步任务队列。
 * <p>
 * status 生命周期：PENDING →（SKIP LOCKED 抢占）→ RUNNING → SUCCESS；
 * RUNNING 异常/失败 → retry_count&lt;max_retry 回 PENDING（next_run_at 指数退避），否则 DEAD。
 * RUNNING 无心跳（update_time 超时）由对账重回 PENDING。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "task_queue", autoResultMap = true)
@Schema(title = "异步任务队列 | Task Queue")
public class TaskQueue extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DEAD = "DEAD";

    @Schema(title = "任务类型 | task type (KB_INDEX/DRAW/...)")
    @TableField("task_type")
    private String taskType;

    @Schema(title = "任务参数（jsonb）")
    @TableField(value = "payload", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode payload;

    @Schema(title = "状态 PENDING/RUNNING/SUCCESS/FAILED/DEAD")
    @TableField("status")
    private String status;

    @Schema(title = "优先级（数字小=高）")
    @TableField("priority")
    private Integer priority;

    @Schema(title = "已重试次数")
    @TableField("retry_count")
    private Integer retryCount;

    @Schema(title = "最大重试次数")
    @TableField("max_retry")
    private Integer maxRetry;

    @Schema(title = "下次可执行时间（退避用）")
    @TableField("next_run_at")
    private LocalDateTime nextRunAt;

    @Schema(title = "抢占实例标识 hostname:pid")
    @TableField("owner_instance")
    private String ownerInstance;

    @Schema(title = "最近一次失败原因")
    @TableField("last_error")
    private String lastError;

    @Schema(title = "提交人用户 id")
    @TableField("created_by")
    private Long createdBy;
}
