package com.moyz.adi.common.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Reciprocal Rank Fusion（RRF）：把多路召回结果按名次融合，无需关心各路分数的量纲差异。
 * <p>
 * 公式：score(d) = Σ 1 / (k + rank_i(d))，rank 从 1 开始；k 默认 60（原论文经验值）。
 * 只在部分路出现的文档，缺失路不贡献分数（等价于 rank=∞）。
 */
public final class RrfFusion {

    /**
     * 原论文经验值
     */
    public static final int DEFAULT_K = 60;

    private RrfFusion() {
    }

    /**
     * 融合多路有序结果
     *
     * @param rankedLists 各路召回结果，每路内部按相关性降序
     * @param k           RRF 常数，<=0 时取默认值 60
     * @param keyFn       去重键（同一内容在多路出现时合并计分）
     * @param <T>         元素类型
     * @return 融合后按分数降序的列表
     */
    public static <T> List<T> fuse(List<List<T>> rankedLists, int k, Function<T, String> keyFn) {
        int kk = k <= 0 ? DEFAULT_K : k;
        Map<String, Double> scoreByKey = new HashMap<>();
        Map<String, T> itemByKey = new HashMap<>();
        Map<String, Integer> firstRankByKey = new HashMap<>();

        int listIndex = 0;
        for (List<T> list : rankedLists) {
            if (list == null || list.isEmpty()) {
                listIndex++;
                continue;
            }
            int rank = 1;
            for (T item : list) {
                String key = keyFn.apply(item);
                if (key == null) {
                    rank++;
                    continue;
                }
                scoreByKey.merge(key, 1.0 / (kk + rank), Double::sum);
                // 同一 key 在多路出现时保留首次出现的元素，并记录最小名次用于稳定排序
                itemByKey.putIfAbsent(key, item);
                firstRankByKey.putIfAbsent(key, listIndex * 1000 + rank);
                rank++;
            }
            listIndex++;
        }

        List<T> result = new ArrayList<>(itemByKey.values());
        result.sort(
                Comparator.<T>comparingDouble(item -> -scoreByKey.getOrDefault(keyFn.apply(item), 0.0))
                        .thenComparingInt(item -> firstRankByKey.getOrDefault(keyFn.apply(item), Integer.MAX_VALUE))
        );
        return result;
    }
}
