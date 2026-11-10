package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 场景与业务工作流绑定（业务编排 Harness M1，2026-10-30）。
 * <p>
 * Scene-to-workflow binding. Mirrors {@link SceneAgent}: admin binds workflows to a
 * scene with full-replace semantics; the chat assembly layer reads bindings to inject
 * per-scene launch_workflow tools.
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_scene_workflow")
@Schema(title = "Scene to workflow binding | 场景与业务工作流绑定")
public class SceneWorkflow extends BaseEntity {

    @Schema(title = "uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Scene uuid")
    @TableField("scene_uuid")
    private String sceneUuid;

    @Schema(title = "Workflow uuid")
    @TableField("workflow_uuid")
    private String workflowUuid;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;
}
