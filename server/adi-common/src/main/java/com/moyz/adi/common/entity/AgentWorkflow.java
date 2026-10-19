package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_agent_workflow")
@Schema(title = "Agent to workflow binding | 智能体与工作流绑定")
public class AgentWorkflow extends BaseEntity {

    @Schema(title = "uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Agent uuid")
    @TableField("agent_uuid")
    private String agentUuid;

    @Schema(title = "Workflow uuid")
    @TableField("workflow_uuid")
    private String workflowUuid;

    @Schema(title = "Is the default workflow of the agent")
    @TableField("is_default")
    private Boolean isDefault;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;
}
