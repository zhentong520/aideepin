package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_workflow_runtime", autoResultMap = true)
@Schema(title = "工作流运行时 | Workflow runtime")
public class WorkflowRuntime extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("uuid")
    private String uuid;

    @TableField("user_id")
    private Long userId;

    @TableField("workflow_id")
    private Long workflowId;

    @TableField(value = "input", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ObjectNode input;

    @TableField(value = "\"output\"", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ObjectNode output;

    @TableField("status")
    private Integer status;

    @TableField("status_remark")
    private String statusRemark;

    /**
     * Total input tokens aggregated from LLM-typed nodes. Written at terminal status (success / fail / waiting_input).
     */
    @TableField("input_tokens")
    private Integer inputTokens;

    /**
     * Total output tokens aggregated from LLM-typed nodes. Written at terminal status.
     */
    @TableField("output_tokens")
    private Integer outputTokens;

    /**
     * Total run duration in milliseconds aggregated from all nodes. Written at terminal status.
     */
    @TableField("duration")
    private Integer duration;

    /**
     * 二开新增（T3-10，2026-10-19）：true = 单节点试跑记录。
     * 试跑复用引擎全链路（SSE/runtime_node/指标），但正式执行记录列表（page）过滤本列。
     */
    @TableField("is_test")
    private Boolean isTest;

    /**
     * 二开新增（T9-01，2026-10-20）：触发来源 manual/schedule/webhook/api。
     * 触发记录（来源/参数/结果）进运行历史：来源=本列，参数=input，结果=output+status。
     */
    @TableField("trigger_source")
    private String triggerSource;
}
