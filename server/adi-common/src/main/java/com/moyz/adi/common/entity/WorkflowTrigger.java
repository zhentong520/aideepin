package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * T9-01：工作流触发配置（定时 cron / webhook token）。
 * <p>
 * Workflow trigger config: one row per trigger bound to a workflow.
 * type=schedule fires via {@code WorkflowTriggerScheduler} when cron_expr is due;
 * type=webhook fires via POST /workflow/webhook/{webhook_token}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_workflow_trigger", autoResultMap = true)
@Schema(title = "工作流触发配置 | workflow trigger config")
public class WorkflowTrigger extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("uuid")
    private String uuid;

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("user_id")
    private Long userId;

    /** schedule | webhook */
    @TableField("trigger_type")
    private String triggerType;

    /** Spring 6 CronExpression（6 域），仅 schedule 类型 */
    @TableField("cron_expr")
    private String cronExpr;

    /** webhook 路径 token，URL = {backend}/workflow/webhook/{token}，唯一 */
    @TableField("webhook_token")
    private String webhookToken;

    /** 触发时默认入参，与 /workflow/run inputs 同构 */
    @TableField(value = "input_params", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ArrayNode inputParams;

    @TableField("is_enable")
    private Boolean isEnable;

    /** 最近一次派发时间（派发即写，供 cron 到期计算防重复） */
    @TableField("last_fire_time")
    private LocalDateTime lastFireTime;

    /** 0=未触发 1=已派发 2=派发失败 */
    @TableField("last_fire_status")
    private Integer lastFireStatus;
}
