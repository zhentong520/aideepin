package com.moyz.adi.common.workflow.node.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class ClassifierCategory implements Serializable {

    @JsonProperty("category_uuid")
    private String categoryUuid;
    @JsonProperty("category_name")
    private String categoryName;

    /**
     * 判据：什么情况下应归入本类别。<br/>
     * 参照 Jev 的 questions.criteria 设计——类别名只作为机器可读的 KEY（用于取结果的 branch target），
     * 判据单独描述，两者职责分开。为空时退化为使用 categoryName，保证历史配置行为不变。
     */
    @JsonProperty("category_description")
    private String categoryDescription;

    /**
     * target node uuid
     */
    @JsonProperty("target_node_uuid")
    private String targetNodeUuid;

    /**
     * 判据缺失时退回类别名，保证旧配置（jsonb 中没有 category_description 字段）不受影响。
     */
    public String effectiveDescription() {
        if (null != categoryDescription && !categoryDescription.isBlank()) {
            return categoryDescription;
        }
        return categoryName;
    }
}
