package com.moyz.adi.common.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbInfoResp {
    private Long id;
    private String uuid;
    private String title;
    private String remark;
    private Boolean isPublic;
    private Boolean isStrict;
    private Integer starCount;
    private Integer ingestMaxOverlap;
    private String ingestSplitStrategy;
    private Integer ingestMaxSegmentSize;
    private String ingestCustomSeparator;
    private String ingestModelName;
    private Long ingestModelId;
    private String ingestTokenEstimator;
    private String ingestEmbeddingModel;
    private Integer retrieveMaxResults;
    private Double retrieveMinScore;
    // 检索增强开关必须能读回来：admin 的编辑弹窗用列表行直接 Object.assign 到表单，
    // 响应里缺字段就会显示成「关闭」，运维保存一次就把库里的配置关掉了。
    private Boolean retrieveHybridEnabled;
    private Integer retrieveRrfK;
    private Integer retrieveKeywordLimit;
    private Boolean retrieveRerankEnabled;
    private Long retrieveRerankModelId;
    private Integer retrieveRerankTopN;
    private Double retrieveRerankMinScore;
    private Boolean retrieveNeighborEnabled;
    private Integer retrieveNeighborWindow;
    private Boolean isRetrievalEnabled;
    private Double queryLlmTemperature;
    private String querySystemMessage;
    private String ownerUuid;
    private String ownerName;
    private Integer itemCount;
    private Integer embeddingCount;

    // ===== 二开新增（2026-10-09）：知识库分类，0 = 未分类 =====

    private Long categoryId;

    // ===== 二开新增（T1-03，2026-10-16）：库级 ACL（铁律 3：编辑弹窗必须能读回，否则保存一次即关闭）=====

    /** 默认可见组（JSON 数组，恒含合成组 owner:<ownerUuid>），前端按数组读写 */
    private JsonNode defaultVisibilityGroups;

    /** 默认密级 PUBLIC/ORG/PRIVATE */
    private String defaultSensitivity;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
