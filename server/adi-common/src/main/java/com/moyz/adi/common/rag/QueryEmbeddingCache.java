package com.moyz.adi.common.rag;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

/**
 * 查询向量缓存：同一问题在同一嵌入模型下的向量是纯函数结果，与知识库内容无关，
 * 因此可以安全复用。
 * <p>
 * 商业价值：FAQ / 客服 / 巡检这类场景里重复提问比例很高，缓存后每次问答省掉一次
 * embedding 远程调用（约 50~200ms 延迟 + 按次计费）。检索结果本身不缓存，
 * 知识库内容更新后召回立即生效，不存在脏读。
 * <p>
 * 正确性边界：不同嵌入模型的向量空间不可比，所以 key 里带模型指纹。指纹取
 * {@code class + toString()}；若某个实现没有有意义的 toString，最坏情况是退化为
 * 按实例缓存（命中率下降），不会产生错误结果——模型换了实例就换了指纹。
 */
@Slf4j
public final class QueryEmbeddingCache {

    /** 缓存上限：按「不同问题数」计，一个问题约几 KB，2000 条约几十 MB 封顶 */
    private static final long MAX_ENTRIES = 2000;

    /** TTL：模型/配置变更后最长 10 分钟自动失效，无需手工清缓存 */
    private static final int TTL_MINUTES = 10;

    /** 超过此长度的查询不缓存（长文本重复率低，占空间收益差） */
    private static final int MAX_CACHED_QUERY_CHARS = 2000;

    private static final Cache<String, float[]> CACHE = CacheBuilder.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
            .recordStats()
            .build();

    private QueryEmbeddingCache() {
    }

    /**
     * 取查询向量，未命中时调用模型并回写缓存。任何异常都不影响主流程。
     */
    public static Embedding embed(EmbeddingModel model, String queryText) {
        if (null == model) {
            // 快捷分支里原本会直接 model.embed(...) 空指针；显式抛出更可诊断
            throw new IllegalArgumentException("embedding model must not be null");
        }
        if (null == queryText || queryText.isBlank()) {
            return model.embed(queryText).content();
        }
        String key = null;
        try {
            if (queryText.length() <= MAX_CACHED_QUERY_CHARS) {
                key = buildKey(model, queryText);
                float[] cached = CACHE.getIfPresent(key);
                if (null != cached) {
                    return Embedding.from(cached.clone());
                }
            }
        } catch (Exception e) {
            //Cache problems must never break retrieval
            log.debug("Query embedding cache lookup failed", e);
            key = null;
        }
        Embedding embedded = model.embed(queryText).content();
        try {
            if (null != key && null != embedded && null != embedded.vector()) {
                CACHE.put(key, embedded.vector());
            }
        } catch (Exception e) {
            log.debug("Query embedding cache write failed", e);
        }
        return embedded;
    }

    /**
     * 缓存命中统计（供运维观测，无侵入）
     */
    public static String stats() {
        var s = CACHE.stats();
        return String.format("hit=%d miss=%d hitRate=%.2f size=%d",
                s.hitCount(), s.missCount(), s.hitRate(), CACHE.size());
    }

    public static void invalidateAll() {
        CACHE.invalidateAll();
    }

    private static String buildKey(EmbeddingModel model, String queryText) throws NoSuchAlgorithmException {
        String fingerprint = model.getClass().getName() + "|" + String.valueOf(model);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(fingerprint.getBytes(StandardCharsets.UTF_8));
        md.update((byte) '|');
        md.update(queryText.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest());
    }
}
