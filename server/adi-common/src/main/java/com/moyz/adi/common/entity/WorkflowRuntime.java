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

    /**
     * 业务编排 Harness M1（2026-10-30）：发起会话 uuid（= adi_character.uuid）。
     * 对话触发（trigger_source=chat）时落列，前端 WorkflowCard 与消息关联靠它检索。
     */
    @TableField("conversation_uuid")
    private String conversationUuid;

    /**
     * 业务编排 Harness M1：发起提问的 questionUuid（LLM 调用前生成，早于消息落库）。
     * 与 conversation_uuid 一起唯一定位「哪次提问触发了哪次运行」。
     */
    @TableField("message_uuid")
    private String messageUuid;

    /**
     * 业务编排 Harness M1：发起时所在场景 uuid（普通聊天为空串）。
     */
    @TableField("scene_uuid")
    private String sceneUuid;
}
