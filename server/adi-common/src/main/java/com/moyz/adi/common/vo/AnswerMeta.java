package com.moyz.adi.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AnswerMeta {

    private Integer inputTokens;
    private Integer outputTokens;
    private String uuid;
    @Builder.Default
    private Integer duration = 0;
    @Builder.Default
    private Boolean isRefEmbedding = false;
    @Builder.Default
    private Boolean isRefGraph = false;
    @Builder.Default
    private Boolean isRefMemoryEmbedding = false;
    /**
     * 二开新增（2026-09-21）：知识库引用来源明细（文档名 + 命中片段 + 分数），
     * 随 [META] 下发，前端渲染"来源"区块。无命中时为空。
     */
    @Builder.Default
    private List<KbRefItem> refs = List.of();
}
