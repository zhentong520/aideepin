package com.moyz.adi.common.dto.finetune;

import lombok.Data;

/**
 * Worker 进度回写请求
 */
@Data
public class ProgressReportReq {

    /**
     * 总进度百分比 0-100
     */
    private Double progress;

    private Integer currentEpoch;

    private Double trainLoss;

    private Double evalLoss;
}
