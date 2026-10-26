package com.moyz.adi.common.rag;

import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.rag.content.Content;

import java.util.List;

/**
 * 重排序扩展点。
 * <p>
 * 默认实现 {@link NoOpContentReranker} 直通返回。二开（2026-09-15）新增 {@link RerankContentReranker}
 * 作为 {@code @Primary} 实现，按知识库 {@link RetrieverContext} 的开关决定是否真正调用 rerank 模型；
 * 未开启时行为与 NoOp 完全一致，保证零影响。
 * <p>
 * 启用方式：知识库 {@code retrieve_rerank_enabled=true} 且存在可用的 {@code type=rerank} 模型。
 */
public interface ContentReranker {

    /**
     * 对展开后的内容重排序（输入已按召回分数降序）
     *
     * @param queryText 用户问题
     * @param contents  展开后的内容列表
     * @return 重排后的内容列表
     */
    List<Content> rerank(String queryText, List<Content> contents);

    /**
     * 带检索上下文的重排序（二开新增）
     *
     * @param queryText 用户问题
     * @param contents  展开后的内容列表
     * @param context   知识库级检索配置；为 null 或未启用时等价于 {@link #rerank(String, List)}
     * @return 重排后的内容列表
     */
    default List<Content> rerank(String queryText, List<Content> contents, RetrieverContext context) {
        return rerank(queryText, contents);
    }
}
