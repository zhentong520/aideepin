package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * T7-01 用户端 RAG 回答反馈请求（👍/👎）。
 * <p>
 * 用户聊天页对每条带知识库来源的回答提交；rating 只接受 PASS / FAIL。
 * ⚠️ user-web 页面接入由用户确认后进行（本批只交付 API + 实体，见汇报）。
 */
@Data
public class RagFeedbackReq {

    @NotBlank
    @Schema(title = "检索 query_id（adi_rag_query_log.query_id）| Retrieval query id")
    private String queryId;

    @Schema(title = "回答消息uuid（可选，user-web 接入后传）| Answer message uuid (optional)")
    private String messageUuid;

    @NotBlank
    @Schema(title = "PASS=👍 / FAIL=👎 | PASS (thumbs up) or FAIL (thumbs down)")
    private String rating;

    @Schema(title = "可选文字说明（最长500字符）| Optional free-text explanation")
    private String comment;
}
