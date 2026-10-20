package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * T7-01 失败案例一键转评测题请求。
 * <p>
 * 与明细表筛选同口径：给定时间范围 + 知识库 + 范围（low_score/missed/failed），
 * 服务端把命中的候选检索转成 kb-eval 格式题目 append 到派生题库文件。
 */
@Data
public class RagToEvalReq {

    public static final String SCOPE_LOW_SCORE = RagQualityDetailReq.SCOPE_LOW_SCORE;
    public static final String SCOPE_MISSED = RagQualityDetailReq.SCOPE_MISSED;
    public static final String SCOPE_FAILED = RagQualityDetailReq.SCOPE_FAILED;

    @Schema(title = "开始时间 yyyy-MM-dd HH:mm:ss | Begin time")
    private String beginTime;

    @Schema(title = "结束时间 yyyy-MM-dd HH:mm:ss | End time")
    private String endTime;

    @Schema(title = "知识库uuid（空=全部）| Knowledge base uuid (blank = all)")
    private String kbUuid;

    @Schema(title = "候选范围: low_score/missed/failed | Candidate scope")
    private String scope = SCOPE_LOW_SCORE;

    @Schema(title = "单次转换上限（防误操作一次性灌入几千题）| Max items per run")
    private Integer limit = 200;
}
