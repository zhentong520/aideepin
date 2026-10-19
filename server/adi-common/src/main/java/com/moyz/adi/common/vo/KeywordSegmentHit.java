package com.moyz.adi.common.vo;

import lombok.Data;

/**
 * 关键词路（PG 全文检索）命中结果，用于与向量路做 RRF 融合。
 * <p>
 * 只带融合必需的最小字段：段标识 + 内容 + ts_rank 分数。
 */
@Data
public class KeywordSegmentHit {

    /**
     * adi_document_segment.id
     */
    private Long id;

    /**
     * adi_document_segment.uuid
     */
    private String uuid;

    /**
     * 展开后的正文内容
     */
    private String content;

    /**
     * ts_rank 分数，仅用于日志与同路内排序；跨路比较由 RRF 名次完成
     */
    private Double score;
}
