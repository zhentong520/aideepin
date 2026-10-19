package com.moyz.adi.common.workflow.node.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ClassifierNodeConfig {
    private List<ClassifierCategory> categories = new ArrayList<>();
    @JsonProperty("model_platform")
    private String modelPlatform;
    @JsonProperty("model_name")
    private String modelName;

    /**
     * 兜底分支：LLM 返回无法解析、类别匹配不上、或置信度低于阈值时跳转的节点 uuid。<br/>
     * <b>不加兜底就沿用旧行为直接抛异常</b>——宁可让工作流显式失败，也不要静默走进一条错分支。
     * 是否配置兜底应由编排者显式决定，这是「高置信也会选错」时唯一可控的出口。
     */
    @JsonProperty("fallback_target_node_uuid")
    private String fallbackTargetNodeUuid;

    /**
     * 置信度阈值（0-1）。为 null 或 &lt;= 0 表示不启用阈值判断，行为与改动前一致。<br/>
     * 注意：confidence 由 LLM 自评，<b>它不是答对的概率</b>，只适合做「低于阈值先看一眼」的粗筛，
     * 具体阈值必须用自己业务的标注数据验证，不可照抄别处的 0.6 / 0.95。
     */
    @JsonProperty("min_confidence")
    private Double minConfidence;
}
