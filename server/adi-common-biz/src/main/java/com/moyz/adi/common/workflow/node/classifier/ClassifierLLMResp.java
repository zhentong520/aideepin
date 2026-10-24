package com.moyz.adi.common.workflow.node.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 分类节点的 LLM 返回结构。<br/>
 * 已移除原本要求 LLM 抽取的 keywords：节点从不消费它，纯属浪费 token 并扩大解析面。
 */
@Data
public class ClassifierLLMResp {

    @JsonProperty("category_uuid")
    private String categoryUuid;
    @JsonProperty("category_name")
    private String categoryName;

    /**
     * LLM 自评的置信度（0-1）。可能为 null、可能越界、可能写成百分比。
     * 统一由 {@link ClassifierDecision#normalizeConfidence(Double)} 归一，不做无条件的信任。
     */
    private Double confidence;
}
