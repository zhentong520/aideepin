package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 二开新增（P0-1，2026-11-03）：用户事件时间线（bi-temporal）。
 * <p>
 * append-only：抽取出的情景性事实（转行、搬家、买了什么等）追加成一行，
 * {@code valid_from}/{@code invalid_at} 构成事实的有效区间（Zep 风格
 * bi-temporal）——支持「用户以前做什么」类时序问题，且归并出错时可审计回放。
 * 失效（invalid_at 置值）由画像合并轮的 LLM 决策驱动。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_user_profile_event", autoResultMap = true)
@Schema(title = "User event timeline | 用户事件时间线", description = "Bi-temporal append-only event facts | bi-temporal 追加式事件事实")
public class UserProfileEvent extends BaseEntity {

    @Schema(title = "Row uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id | 归属用户id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Event fact text | 事件事实文本")
    @TableField("event")
    private String event;

    @Schema(title = "Source character message id | 来源角色消息id")
    @TableField("source_msg_id")
    private Long sourceMsgId;

    @Schema(title = "Fact valid-from time | 事实生效时间")
    @TableField("valid_from")
    private LocalDateTime validFrom;

    @Schema(title = "Fact invalidation time; NULL = still current | 事实失效时间；NULL=仍生效")
    @TableField("invalid_at")
    private LocalDateTime invalidAt;
}
