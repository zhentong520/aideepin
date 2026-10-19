package com.moyz.adi.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KbSearchReq {
    private String title;
    private Boolean isPublic;
    private Integer minItemCount;
    private Integer minEmbeddingCount;
    private Long[] createTime;
    private Long[] updateTime;
    private String ownerName;

    // ===== 二开新增（2026-10-09）：知识库分类过滤，null 或 0 表示不过滤 =====

    private Long categoryId;
}
