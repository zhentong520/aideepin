package com.moyz.adi.common.vo;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Content Retriever创建参数
 */
@Builder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RetrieverCreateParam {
    /**
     * 用来生成图谱，生成向量时不使用
     */
    private ChatModel chatModel;
    /**
     * 过滤条件
     */
    private Filter filter;
    /**
     * 最大返回数量
     */
    private int maxResults;
    /**
     * 最小命中分数
     */
    private double minScore;
    /**
     * 如果数据库中搜索不到数据，是否强行中断该搜索，不继续往下执行（即不继续请求LLM进行回答）
     */
    private boolean breakIfSearchMissed;
    /**
     * 停用文档的 UUID 集合。向量路径将其组合为 IsNotIn filter 在数据库层面排除；
     * 图谱路径在 Java 层后置过滤（因 kb_item_uuid 可能为逗号分隔多值）。
     */
    private Set<String> excludedItemUuids;

    /**
     * 二开新增（2026-09-15）：知识库级检索配置（混合检索 / RRF / rerank）。
     * 为 null 时按 {@code RetrieverContext.none()} 处理，即保持原有纯向量召回行为。
     */
    private RetrieverContext context;

    /**
     * 知识库 uuid。用于「图谱是否为空」的缓存键；角色记忆等无归属场景留空
     * （此时不缓存，行为与改造前一致）。
     */
    private String kbUuid;

    /**
     * 二开新增（T3-12，2026-10-19）：节点级 rerank 覆盖。
     * null = 不覆盖（继承库级 retrieve_rerank_enabled）；非 null 时在
     * {@code EmbeddingRag.createRetriever} 处覆盖库级配置。
     */
    private Boolean rerankEnabledOverride;

    /**
     * 二开新增（T3-12，2026-10-19）：节点级扩窗覆盖。
     * null = 不覆盖（继承库级 retrieve_neighbor_enabled）；非 null 时覆盖库级配置。
     */
    private Boolean expandWindowEnabledOverride;
}
