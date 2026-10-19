package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_scene_agent")
@Schema(title = "Scene to agent binding | 场景与智能体绑定")
public class SceneAgent extends BaseEntity {

    @Schema(title = "uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Scene uuid")
    @TableField("scene_uuid")
    private String sceneUuid;

    @Schema(title = "Agent uuid")
    @TableField("agent_uuid")
    private String agentUuid;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;
}
