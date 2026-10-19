package com.moyz.adi.common.dto.finetune;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Data;

/**
 * Worker 领取任务响应：含任务内容、数据集定位与 worker_epoch。
 * worker_epoch 必须随后续 progress/log/complete/fail 回传，服务端据此拒绝
 * 被回收重派后的僵尸 Worker 迟到写入。
 */
@Data
@Builder
public class WorkerClaimResp {

    private String jobUuid;

    private String jobName;

    private String baseModelName;

    private String method;

    private ObjectNode hyperParams;

    /**
     * 服务端生成的领取代次
     */
    private String workerEpoch;

    private String datasetUuid;

    private String datasetName;

    private String datasetFormat;

    private Integer datasetSampleCount;

    /**
     * 数据集文件在 adi_file 中的 uuid（下载走 /ext/v1/finetune/dataset/{fileUuid}/download）
     */
    private String datasetFileUuid;
}
