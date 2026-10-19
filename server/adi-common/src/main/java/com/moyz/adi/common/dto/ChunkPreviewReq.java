package com.moyz.adi.common.dto;

import lombok.Data;

/**
 * 分块预览请求：所有参数都可空，空值取知识库/文档的当前配置。
 * <p>
 * Chunk preview request: every field is optional; blanks fall back to the KB/document settings.
 */
@Data
public class ChunkPreviewReq {

    /**
     * 切分策略：recursive / paragraph / line / sentence / sentence_cn / custom
     */
    private String strategy;

    /**
     * 段大小（token），非法值回退 512
     */
    private Integer maxSegmentSize;

    /**
     * 重叠（token），≥ 段大小时夹到段大小的 1/4
     */
    private Integer overlap;

    /**
     * 自定义分隔符（策略为 custom 时生效）
     */
    private String separator;

    /**
     * 是否清洗；null = 跟随全局配置 adi.ingest.clean.enabled
     */
    private Boolean clean;

    /**
     * 合并软换行；null = 跟随全局配置
     */
    private Boolean mergeSoftLineBreaks;

    /**
     * 丢弃重复短行（页眉页脚）；null = 跟随全局配置
     */
    private Boolean dropDuplicateLines;

    /**
     * 父子模式子块大小（token）
     */
    private Integer childMaxChunkSize;

    private Integer page = 1;

    private Integer pageSize = 50;
}
