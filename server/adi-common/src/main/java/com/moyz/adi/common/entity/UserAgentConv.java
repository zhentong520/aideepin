package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户基于全局智能体的会话实例。
 * <p>
 * 为什么需要这张表：场景与智能体全局共享，但对话记录必须按用户隔离。
 * 智能体上的 {@code characterUuid} 只是「模板」，所有用户共用它会同时踩两个坑——
 * 多处代码按 uuid 单条查询（{@code oneOpt()} 遇多行直接抛异常），
 * 以及短期记忆以 characterUuid 为键（会串到别人的对话内容）。
 * 所以这里为每个用户在每个智能体上落一份自己的会话。
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_user_agent_conv")
@Schema(title = "Per-user conversation instance of an agent | 用户基于智能体的会话实例")
public class UserAgentConv extends BaseEntity {

    @Schema(title = "uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "The shared agent uuid")
    @TableField("agent_uuid")
    private String agentUuid;

    @Schema(title = "Character uuid owned by this user")
    @TableField("character_uuid")
    private String characterUuid;
}
