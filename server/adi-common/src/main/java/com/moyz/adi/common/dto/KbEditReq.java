package com.moyz.adi.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
public class KbEditReq {

    private Long id;

    private String uuid;

    @NotBlank
    private String title;

    private String remark;

    private Boolean isPublic;

    private Boolean isStrict;

    private Integer retrieveMaxResults;

    private Double retrieveMinScore;

    private Integer ingestMaxOverlap;

    private String ingestSplitStrategy;

    private Integer ingestMaxSegmentSize;

    private String ingestCustomSeparator;

    private Long ingestModelId;

    private String ingestTokenEstimator;

    private Double queryLlmTemperature;

    private String querySystemMessage;

    // ===== 二开新增（2026-09-15）：混合检索与重排配置 =====

    private Boolean retrieveHybridEnabled;

    // ===== 二开新增（2026-09-21）：对话检索开关（false = 对话链路跳过该库）=====

    private Boolean isRetrievalEnabled;

    private Integer retrieveRrfK;

    private Integer retrieveKeywordLimit;

    private Boolean retrieveRerankEnabled;

    private Long retrieveRerankModelId;

    private Integer retrieveRerankTopN;

    private Double retrieveRerankMinScore;

    // ===== 二开新增（2026-09-22）：命中片段左右扩窗 =====

    /**
     * 是否把命中片段的相邻片段一并送入上下文
     */
    private Boolean retrieveNeighborEnabled;

    /**
     * 左右各取多少个相邻片段，0 表示不扩窗
     */
    private Integer retrieveNeighborWindow;

    // ===== 二开新增（2026-10-09）：知识库分类，0/null = 未分类 =====

    private Long categoryId;

    // ===== 二开新增（T1-03，2026-10-16）：库级 ACL 默认值 =====

    /** 默认可见组（JSON 数组），保存链路经 BeanUtils 拷入实体（KbInfoResp 能读回，铁律 3） */
    private com.fasterxml.jackson.databind.JsonNode defaultVisibilityGroups;

    /** 默认密级 PUBLIC/ORG/PRIVATE */
    private String defaultSensitivity;
}
