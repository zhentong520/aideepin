package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户安装的技能（照 adi_user_mcp 范式）：市场安装记录 + 用户级启停。
 * 角色绑定的 skillIds 必须是 is_enable 的技能才会在对话中生效。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("adi_user_skill")
public class UserSkill extends BaseEntity {

    @TableField("uuid")
    private String uuid;

    @TableField("user_id")
    private Long userId;

    @TableField("skill_id")
    private Long skillId;

    @TableField("is_enable")
    private Boolean isEnable;
}
