package com.moyz.adi.common.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * T7-01 质量看板明细行（一次检索一行）。
 * <p>
 * topScore 在 Java 侧从 hit_segments jsonb 计算（聚合 SQL 用同一口径，见
 * {@code RagQueryLogMapper.QUALITY_SUMMARY_SQL} 注释）；feedbackRating/feedbackComment
 * 来自 adi_rag_feedback（按页批量查询，避免 N+1）。
 */
@Data
public class RagQualityDetailItem {

    private String queryId;

    private LocalDateTime createTime;

    private String userUuid;

    /** 用户原始问句（转评测题题面来源） */
    private String queryText;

    private List<String> kbUuids = new ArrayList<>();

    private Integer hitCount;

    /** 单次检索最高分；无命中为 null（不展示 0） */
    private Double topScore;

    private Integer elapsedMs;

    private String embeddingModel;

    /** PASS / FAIL / 空（未评价） */
    private String feedbackRating = "";

    private String feedbackComment = "";
}
