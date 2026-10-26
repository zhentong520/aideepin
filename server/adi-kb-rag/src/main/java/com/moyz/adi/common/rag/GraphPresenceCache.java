package com.moyz.adi.common.rag;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 知识库「是否有图谱数据」的短 TTL 缓存。
 * <p>
 * 存在的意义：图谱检索器每次提问都要先花一次 LLM 调用（约 6 秒 + 一次计费）从问题里抽实体，
 * 而绝大多数知识库压根没建过图谱索引——抽完实体去空图里查，必然零命中，属于纯浪费。
 * 加缓存后空图判断被摊薄成 60 秒一次轻量查询。
 * <p>
 * TTL 选 60 秒而不是更长，是因为图谱索引是异步建的：建完之后最长 1 分钟即可被检索感知，
 * 不会出现"我明明建了图谱，问答却一直不走图谱路"的疑难工单。缓存只缓存"图谱是否为空"
 * 这一事实，不缓存任何检索结果。
 */
@Slf4j
public final class GraphPresenceCache {

    /** 缓存上限：按知识库数量计，实际规模远小于此 */
    private static final long MAX_ENTRIES = 2000;

    /** 空图判断的保鲜期，兼顾异步建索引的可见延迟 */
    private static final int TTL_SECONDS = 60;

    private static final Cache<String, Boolean> CACHE = CacheBuilder.newBuilder()
            .maximumSize(MAX_ENTRIES)
            .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS)
            .build();

    private GraphPresenceCache() {
    }

    /**
     * 给定的知识库是否没有图谱数据。
     *
     * @param kbUuid 知识库 uuid；为空表示无法确定归属，此时不缓存、每次实探（保守）
     * @param probe  真实的空图探测逻辑
     * @return true 表示图谱为空，可以跳过图谱检索
     */
    public static boolean isGraphEmpty(String kbUuid, Supplier<Boolean> probe) {
        if (StringUtils.isBlank(kbUuid)) {
            return Boolean.TRUE.equals(probe.get());
        }
        Boolean cached = CACHE.getIfPresent(kbUuid);
        if (cached != null) {
            return cached;
        }
        boolean empty;
        try {
            empty = Boolean.TRUE.equals(probe.get());
        } catch (Exception e) {
            // 探不明就当图谱非空（保持改造前行为），绝不因为探针异常把图谱路关掉
            log.warn("Graph presence probe failed for kb:{}, keeping graph path enabled", kbUuid, e);
            return false;
        }
        CACHE.put(kbUuid, empty);
        return empty;
    }

    /**
     * 图谱索引写入完成后调用，让下一次提问立刻走图谱路，不等 TTL 过期。
     */
    public static void invalidate(String kbUuid) {
        if (StringUtils.isNotBlank(kbUuid)) {
            CACHE.invalidate(kbUuid);
        }
    }
}
