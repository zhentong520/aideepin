package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_scene")
@Schema(title = "Chat scene binding multiple agents | 对话场景（绑定多个智能体）")
public class Scene extends BaseEntity {

    @Schema(title = "Scene uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Scene title")
    @TableField("title")
    private String title;

    @Schema(title = "Description")
    @TableField("remark")
    private String remark;

    @Schema(title = "Icon")
    @TableField("icon")
    private String icon;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
