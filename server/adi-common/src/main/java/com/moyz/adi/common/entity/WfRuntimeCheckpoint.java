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

/**
 * 业务编排 Harness M2-T4（2026-11-11）：工作流运行检查点（停驻态快照）。
 * 仅 PAUSED/WAITING_INPUT 各落一行（upsert），终态即删（决策点①）。
 * 进程重启后由「继续」按钮触发 L2 重建恢复：按 paused_node_uuid + runtime_node
 * 输出上下文从断点节点起跑。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_workflow_runtime_checkpoint", autoResultMap = true)
@Schema(title = "工作流运行检查点 | Workflow runtime checkpoint")
public class WfRuntimeCheckpoint extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("runtime_uuid")
    private String runtimeUuid;

    @TableField("workflow_uuid")
    private String workflowUuid;

    @Schema(title = "停驻状态：5=WAITING_INPUT / 8=PAUSED")
    @TableField("process_status")
    private Integer processStatus;

    @Schema(title = "停驻节点（恢复时从该节点起跑）")
    @TableField("paused_node_uuid")
    private String pausedNodeUuid;

    @Schema(title = "轻量状态快照（已完成节点 uuid 等）")
    @TableField(value = "state_snapshot", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ObjectNode stateSnapshot;
}
