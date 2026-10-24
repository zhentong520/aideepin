package com.moyz.adi.common.workflow.node.knowledgeretrieval;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
@Data
public class KnowledgeRetrievalNodeConfig {
    @JsonProperty("knowledge_base_uuid")
    private String knowledgeBaseUuid;
    @JsonProperty("knowledge_base_name")
    private String knowledgeBaseName;
    private Double score;
    @JsonProperty("top_n")
    private Integer topN;
    @JsonProperty("is_strict")
    private Boolean isStrict;
    @JsonProperty("default_response")
    private String defaultResponse;

    // ===== 二开新增（T3-12，2026-10-19）：节点级 rerank/扩窗三态覆盖 =====
    // 前端存三态字符串：'' = 继承库级（Jackson 反序列化为 null）/'true' / 'false'；
    // null 时执行处不传 override，保持库级语义

    /** 三态：null=继承库级 retrieve_rerank_enabled */
    @JsonProperty("rerank")
    private Boolean rerank;

    /** 三态：null=继承库级 retrieve_neighbor_enabled */
    @JsonProperty("expand_window")
    private Boolean expandWindow;
}
