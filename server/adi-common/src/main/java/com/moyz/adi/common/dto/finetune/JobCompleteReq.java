package com.moyz.adi.common.dto.finetune;

import lombok.Data;

/**
 * Worker 任务完成上报请求。
 * adapter 类产物以 multipart 文件回传服务器；merged/gguf 体积大，登记 external_path 留在 GPU 机。
 * 两者可同时上报（各生成一条产物记录）。
 */
@Data
public class JobCompleteReq {

    /**
     * adapter / merged / gguf
     */
    private String artifactType;

    /**
     * 产物名（导出模型 tag），缺省用 job 名
     */
    private String name;

    /**
     * GPU 机上的产物路径（merged/gguf）
     */
    private String externalPath;

    private Long externalSizeBytes;

    private String remark;
}
