package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * T7-01 质量看板明细表查询请求（下钻：按知识库/时间范围/质量维度筛选）。
 */
@Data
public class RagQualityDetailReq {

    /** 全部 */
    public static final String SCOPE_ALL = "all";
    /** 未命中（hit_count = 0） */
    public static final String SCOPE_MISSED = "missed";
    /** 低分（最高分 < 阈值，含未命中——与看板低分占比同口径） */
    public static final String SCOPE_LOW_SCORE = "low_score";
    /** 差评（adi_rag_feedback.rating = FAIL） */
    public static final String SCOPE_FAILED = "failed";

    @Schema(title = "开始时间 yyyy-MM-dd HH:mm:ss | Begin time")
    private String beginTime;

    @Schema(title = "结束时间 yyyy-MM-dd HH:mm:ss | End time")
    private String endTime;

    @Schema(title = "知识库uuid（空=全部）| Knowledge base uuid (blank = all)")
    private String kbUuid;

    @Schema(title = "用户uuid（空=全部）| User uuid (blank = all)")
    private String userUuid;

    @Schema(title = "筛选范围: all/missed/low_score/failed | Scope filter")
    private String scope = SCOPE_ALL;
}
