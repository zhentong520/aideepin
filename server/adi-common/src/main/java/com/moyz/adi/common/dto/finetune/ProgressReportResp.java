package com.moyz.adi.common.dto.finetune;

import lombok.Builder;
import lombok.Data;

/**
 * 进度回写响应：stopRequested=true 表示管理端已请求停止，
 * Worker 应尽快中止训练并以 stopped 终态上报。
 */
@Data
@Builder
public class ProgressReportResp {

    /**
     * 本次回写是否被接受（false = 任务已不属于该 Worker，应立即退出）
     */
    private boolean accepted;

    private boolean stopRequested;
}
