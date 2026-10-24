package com.moyz.adi.common.rag;

import com.moyz.adi.common.helper.RerankModelContext;
import com.moyz.adi.common.languagemodel.OpenAiCompatibleScoringModel;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 真实重排实现（二开新增，2026-09-15），取代基座的 {@link NoOpContentReranker}。
 * <p>
 * 是否真正调用模型由 {@link RetrieverContext} 决定：知识库未开启 rerank、或未配置可用 rerank 模型时
 * 直通返回，行为与旧版完全一致，保证不开启即零影响。
 * <p>
 * 分数归一化：不同 rerank 模型分数量纲不同（有负有正且范围不定），统一过 sigmoid 映射到 (0,1)，
 * 使知识库上的 {@code retrieve_rerank_min_score} 阈值具有跨模型可比性。
 */
@Slf4j
@Primary
@Component
public class RerankContentReranker implements ContentReranker {

    @Override
    public List<Content> rerank(String queryText, List<Content> contents) {
        return rerank(queryText, contents, RetrieverContext.none());
    }

    @Override
    public List<Content> rerank(String queryText, List<Content> contents, RetrieverContext context) {
        return rerankWithScores(queryText, contents, context).getContents();
    }

    /**
     * 重排并同时带出每条内容的分数（二开 2026-09-30，供管理后台「召回测试」页面展示）。
     * <p>
     * {@link #rerank(String, List, RetrieverContext)} 就是本方法的结果视图，因此**只有一份排序 /
     * 阈值 / 截断的实现**：测试页看到的顺序与分数必然与线上问答一致，不会因为复制一份逻辑而漂移。
     * 代价是需要分数的调用方要自己读 {@link RerankOutcome#getScoreByContent()}（按 Content 实例身份取，
     * 因为同一段内容可能在多路命中里出现同名文本）。
     *
     * @return {@code executed=false} 表示本次没有真正调用重排模型（开关关闭 / 仅一条 / 模型缺失 / 分数异常），
     * 此时 {@code contents} 为原顺序，且分数表为空——调用方据此区分"没重排"与"重排后没变"
     */
    public RerankOutcome rerankWithScores(String queryText, List<Content> contents, RetrieverContext context) {
        if (CollectionUtils.isEmpty(contents) || contents.size() == 1) {
            return RerankOutcome.skipped(contents);
        }
        if (context == null || !context.isRerankEnabled()) {
            return RerankOutcome.skipped(contents);
        }
        OpenAiCompatibleScoringModel scoringModel = RerankModelContext.getById(context.getRerankModelId());
        if (scoringModel == null) {
            log.warn("rerank enabled but no rerank model configured,fallback to origin order");
            return RerankOutcome.skipped(contents);
        }

        List<TextSegment> segments = contents.stream().map(Content::textSegment).toList();
        Response<List<Double>> response = scoringModel.scoreAll(segments, queryText);
        List<Double> rawScores = response.content();
        if (rawScores == null || rawScores.size() != contents.size()) {
            log.error("rerank score size mismatch,expected:{},actual:{},fallback", contents.size(),
                    rawScores == null ? -1 : rawScores.size());
            return RerankOutcome.skipped(contents);
        }
        // 全 0 分一律视为调用失败。OpenAiCompatibleScoringModel.scoreAll 在**网络异常 / 非 2xx /
        // 响应解析失败**时会预填 0 分返回（为了满足"返回列表长度与入参一致"的契约），若不在这里识别，
        // 这批 0 分经 sigmoid 会变成清一色 0.5 分、排序退回原顺序——在界面与日志上跟
        // 「模型认为这些片段都很相近」完全无法区分，等于把故障伪装成正常结果。
        // 真实的 rerank 分数是 logits，不可能恰好全为 0；即便真的全 0，跳过重排（保持召回顺序）
        // 也与原结果等价，是更诚实的降级。
        if (rawScores.stream().allMatch(score -> null == score || 0.0 == score)) {
            log.warn("rerank returned all-zero scores,treated as failure,fallback to origin order,size:{}",
                    contents.size());
            return RerankOutcome.skipped(contents);
        }

        List<ScoredContent> scored = new ArrayList<>(contents.size());
        for (int i = 0; i < contents.size(); i++) {
            scored.add(new ScoredContent(contents.get(i), sigmoid(rawScores.get(i)), i));
        }
        // 分数相同时保持原召回顺序，避免结果抖动
        scored.sort(Comparator.<ScoredContent>comparingDouble(item -> -item.score)
                .thenComparingInt(item -> item.originIndex));

        double minScore = context.getRerankMinScore();
        List<ScoredContent> kept = new ArrayList<>(scored.size());
        for (ScoredContent item : scored) {
            if (minScore > 0 && item.score < minScore) {
                log.debug("rerank drop content,score:{} < minScore:{}", item.score, minScore);
                continue;
            }
            kept.add(item);
        }
        // 阈值把全部结果都过滤掉时退回原顺序，避免返回空上下文导致严格模式直接中断
        if (kept.isEmpty()) {
            log.warn("rerank filtered out all contents,fallback to origin order");
            return RerankOutcome.skipped(contents);
        }

        int topN = context.getRerankTopN();
        if (topN > 0 && kept.size() > topN) {
            kept = new ArrayList<>(kept.subList(0, topN));
        }
        log.info("rerank done,model:{},input:{},output:{}", scoringModel.getAiModel().getName(), contents.size(), kept.size());

        List<Content> result = new ArrayList<>(kept.size());
        Map<Content, Double> scoreByContent = new IdentityHashMap<>(kept.size());
        for (ScoredContent item : kept) {
            result.add(item.content);
            scoreByContent.put(item.content, item.score);
        }
        return RerankOutcome.executed(result, scoreByContent, scoringModel.getAiModel().getName());
    }

    /**
     * 重排结果：内容顺序 + 每条内容的归一化分数 + 是否真的执行了模型调用
     */
    @Getter
    public static final class RerankOutcome {

        private final List<Content> contents;
        private final Map<Content, Double> scoreByContent;
        private final boolean executed;
        private final String modelName;

        private RerankOutcome(List<Content> contents, Map<Content, Double> scoreByContent, boolean executed, String modelName) {
            this.contents = contents;
            this.scoreByContent = scoreByContent;
            this.executed = executed;
            this.modelName = modelName;
        }

        public static RerankOutcome skipped(List<Content> contents) {
            return new RerankOutcome(contents, Map.of(), false, null);
        }

        public static RerankOutcome executed(List<Content> contents, Map<Content, Double> scoreByContent, String modelName) {
            return new RerankOutcome(contents, scoreByContent, true, modelName);
        }
    }

    private static double sigmoid(double x) {
        if (x > 20) {
            return 1.0;
        }
        if (x < -20) {
            return 0.0;
        }
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static class ScoredContent {
        private final Content content;
        private final double score;
        private final int originIndex;

        ScoredContent(Content content, double score, int originIndex) {
            this.content = content;
            this.score = score;
            this.originIndex = originIndex;
        }
    }
}
