package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二开新增（P0-1，2026-11-03）：结构化用户画像（Memobase 风格）。
 * <p>
 * 每用户一行，{@code profile} 是 JSON 维度对象（职业/偏好/语言/技能等维度名→值）。
 * 写入侧：长期记忆抽取轮由 {@code LongTermMemoryService} 用一次 LLM 合并调用
 * 把新事实合并进画像（纯状态合并，不是 append）；读取侧：聊天注入时纯 SQL 查询，
 * 零 embedding、零向量检索——「用户是做什么的」这类稳定属性问题直接读画像字段。
 * <p>
 * jsonb 字段必须 autoResultMap + 字段级 typeHandler + updateById 写入
 * （T1-03 铁律 4），否则「更新了但查出来是 null」。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_user_profile", autoResultMap = true)
@Schema(title = "Structured user profile | 结构化用户画像", description = "Per-user JSON profile merged during memory extraction, injected via pure SQL | 抽取轮合并的用户画像，注入时纯 SQL 读取")
public class UserProfile extends BaseEntity {

    @Schema(title = "Row uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id (unique) | 归属用户id（唯一）")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Profile dimensions JSON, e.g. {\"occupation\":\"developer\"} | 画像维度 JSON 对象")
    @TableField(value = "profile", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode profile;

    @Schema(title = "Monotonic merge counter | 合并版本号")
    @TableField("profile_version")
    private Integer profileVersion;
}
