package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.moyz.adi.common.enums.MemoryType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二开新增（T7-07，2026-10-20）：敏感（PII）记忆用户确认队列表。
 * <p>
 * 抽取出的记忆命中 PII 特征（联系方式/证件/账号密码）时不直接写入向量库，
 * 先落本表 PENDING；用户在 API 层 confirm 后由 {@code MemoryPendingService}
 * 按 memory_type 路由写入语义/情景向量库，reject 则仅标记驳回（数据不落向量库）。
 * ⚠️ confirm/reject 必须校验 user_id = 当前登录用户——只有记忆归属人能确认，
 * 任何人不能替他人确认敏感信息入库。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_memory_pending", autoResultMap = true)
@Schema(title = "敏感记忆确认队列 | Sensitive Memory Pending Confirmation", description = "PII 记忆持久化前的用户确认队列 | Pending PII memories awaiting user confirmation")
public class MemoryPending extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Schema(title = "待确认行唯一uuid | Pending row unique uuid")
    @TableField("memory_uuid")
    private String memoryUuid;

    @Schema(title = "归属用户id（仅本人可确认/驳回）| Owner user id (only owner may confirm/reject)")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "归属角色id | Owning character id")
    @TableField("character_id")
    private Long characterId;

    @Schema(title = "记忆类型路由码：1=semantic 2=episodic | MemoryType routing code")
    @TableField("memory_type")
    private MemoryType memoryType;

    @Schema(title = "待确认的记忆内容 | Memory content awaiting confirmation")
    @TableField("content")
    private String content;

    @Schema(title = "PENDING / CONFIRMED / REJECTED")
    @TableField("status")
    private String status;

    @Schema(title = "来源角色消息id | Source character message id")
    @TableField("source_msg_id")
    private Long sourceMsgId;
}
