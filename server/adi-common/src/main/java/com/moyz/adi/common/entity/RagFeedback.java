package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二开新增（T7-01，2026-10-20）：RAG 回答 👍👎 反馈。
 * <p>
 * 用户聊天页对每条带知识库来源的评价落这张表：一条检索（adi_rag_query_log.query_id）
 * 一个用户一票，可改票（唯一部分索引 uk_rag_feedback_query_user 保证）。
 * 旧列 adi_rag_query_log.feedback 被本表取代，不再写入（保留仅为兼容）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_rag_feedback", autoResultMap = true)
@Schema(title = "RAG回答反馈 | RAG Answer Feedback", description = "用户对带知识库来源回答的👍👎评价 | Thumbs up/down on KB-sourced answers")
public class RagFeedback extends BaseEntity {

    public static final String RATING_PASS = "PASS";
    public static final String RATING_FAIL = "FAIL";

    @Schema(title = "反馈行唯一uuid | Feedback row unique uuid")
    @TableField("feedback_uuid")
    private String feedbackUuid;

    @Schema(title = "关联检索 query_id | Related adi_rag_query_log.query_id")
    @TableField("query_id")
    private String queryId;

    @Schema(title = "回答消息uuid（user-web 接入前可为空）| Answer message uuid (blank until user-web wiring)")
    @TableField("message_uuid")
    private String messageUuid;

    @Schema(title = "反馈提交用户uuid | Feedback author uuid")
    @TableField("user_uuid")
    private String userUuid;

    @Schema(title = "PASS=👍 / FAIL=👎 | PASS (thumbs up) or FAIL (thumbs down)")
    @TableField("rating")
    private String rating;

    @Schema(title = "可选文字说明（最长500字符）| Optional free-text explanation")
    @TableField("comment")
    private String comment;
}
