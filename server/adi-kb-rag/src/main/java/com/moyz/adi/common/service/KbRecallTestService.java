package com.moyz.adi.common.service;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.KbRecallTestReq;
import com.moyz.adi.common.dto.KbRecallTestResp;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.RerankModelContext;
import com.moyz.adi.common.languagemodel.OpenAiCompatibleScoringModel;
import com.moyz.adi.common.mapper.DocumentSegmentMapper;
import com.moyz.adi.common.rag.BigramTokenizer;
import com.moyz.adi.common.rag.ContentReranker;
import com.moyz.adi.common.rag.EmbeddingRag;
import com.moyz.adi.common.rag.EmbeddingRagContext;
import com.moyz.adi.common.rag.QueryEmbeddingCache;
import com.moyz.adi.common.rag.RerankContentReranker;
import com.moyz.adi.common.rag.RrfFusion;
import com.moyz.adi.common.rag.SegmentHitResolver;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.vo.KeywordSegmentHit;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库召回测试（管理后台「知识库 → 召回测试」）。
 * <p>
 * <b>它测的就是线上链路</b>：向量检索用同一个 {@link EmbeddingStore} 与嵌入模型，阈值口径、关键词路、
 * RRF 融合、重排全部复用生产组件（{@link BigramTokenizer} / {@link RrfFusion} /
 * {@link RerankContentReranker} / {@link SegmentHitResolver}），参数解析复用
 * {@link KnowledgeBaseService#buildRetrieverContext}。唯一的两处刻意的差异：
 * <ol>
 *   <li><b>不做 minScore 预过滤</b>——池子按原始分数取回，再在内存里切阈值。否则"被阈值滤掉几条"
 *       就永远看不见，而"没取回来"和"取了被滤掉"的处置完全不同（前者要扩池，后者要调阈值）。</li>
 *   <li><b>不应用左右扩窗与出处抬头</b>——它们只改送给模型的文本，不参与排序（见 kb-eval 的结论：
 *       扩窗不是召回手段）。页面会明确提示线上问答还会额外带上 ±N 邻段。</li>
 * </ol>
 * 两处差异都只影响"展示什么"，不影响"排什么"。
 */
@Slf4j
@Service
public class KbRecallTestService {

    /**
     * 单条命中的内容上限，防止超长片段把响应撑爆
     */
    private static final int MAX_CONTENT_CHARS = 4000;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private DocumentSegmentService documentSegmentService;

    @Resource
    private DocumentSegmentQuestionService questionService;

    @Resource
    private DocumentSegmentChildChunkService childChunkService;

    @Resource
    private DocumentSegmentMapper documentSegmentMapper;

    public KbRecallTestResp test(KbRecallTestReq req) {
        long started = System.currentTimeMillis();
        KbRecallTestResp resp = new KbRecallTestResp();
        resp.setKbUuid(req.getKbUuid());
        resp.setQuery(req.getQuery());

        KnowledgeBase kb = knowledgeBaseService.getReadableOrThrow(req.getKbUuid());
        resp.setKbTitle(kb.getTitle());

        // 临时覆盖：在配置对象的副本上改字段，再走生产同一套解析。不落库、不影响其他请求
        KnowledgeBase effective = new KnowledgeBase();
        BeanUtils.copyProperties(kb, effective);
        List<String> overridden = applyOverrides(effective, req);

        Set<String> disabledDocUuids = new HashSet<>(kbDocumentService.listDisabledItemUuids(kb.getUuid()));
        RetrieverContext ctx = knowledgeBaseService.buildRetrieverContext(effective, disabledDocUuids);

        EmbeddingRag rag = EmbeddingRagContext.get(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE);
        if (null == rag || null == rag.getEmbeddingStore() || null == rag.getEmbeddingModel()) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }

        KbRecallTestResp.Stage stage = new KbRecallTestResp.Stage();
        KbRecallTestResp.EffectiveParams params = buildParams(effective, ctx, overridden);
        stage.setPoolRequested(params.getTopK());

        // ---------- 1. 向量路：按原始分数取回整个池 ----------
        long t = System.currentTimeMillis();
        Embedding queryEmbedding = QueryEmbeddingCache.embed(rag.getEmbeddingModel(), req.getQuery());
        stage.setEmbeddingMs(System.currentTimeMillis() - t);

        t = System.currentTimeMillis();
        List<EmbeddingMatch<TextSegment>> poolMatches = searchPool(rag.getEmbeddingStore(), queryEmbedding,
                effective.getUuid(), params.getTopK(), disabledDocUuids);
        stage.setVectorMs(System.currentTimeMillis() - t);
        stage.setPoolReturned(poolMatches.size());

        // ---------- 2. 池内候选 → 关系层片段 ----------
        List<String> poolEmbeddingIds = poolMatches.stream().map(EmbeddingMatch::embeddingId).toList();
        SegmentHitResolver.Resolution resolution = SegmentHitResolver.resolve(
                documentSegmentService, questionService, childChunkService, poolEmbeddingIds);

        // 内容 → 明细的映射按「内容文本」索引：向量路与关键词路都以文本为去重键（与 RrfFusion 一致）
        Map<String, KbRecallTestResp.Hit> hitByText = new LinkedHashMap<>();
        List<Content> vectorContents = new ArrayList<>();
        int poolRank = 0;
        int poolResolved = 0;
        for (EmbeddingMatch<TextSegment> match : poolMatches) {
            poolRank++;
            SegmentHitResolver.Hit hit = resolution.resolveOne(match.embeddingId());
            if (null == hit || StringUtils.isBlank(hit.getText())) {
                continue;
            }
            poolResolved++;
            String text = hit.getText();
            KbRecallTestResp.Hit detail = hitByText.get(text);
            if (null == detail) {
                detail = newHit(hit, text);
                detail.setPoolRank(poolRank);
                detail.setStoreScore(match.score());
                detail.setCosine(storeScoreToCosine(match.score()));
                hitByText.put(text, detail);
                vectorContents.add(toContent(text, effective.getUuid(), hit.getSegmentId(), "vector"));
            }
        }
        stage.setPoolResolved(poolResolved);

        // ---------- 3. 阈值切分（等价于向量库的 min_score 过滤，但不丢信息） ----------
        List<Content> vectorKept = new ArrayList<>();
        int above = 0;
        int filtered = 0;
        for (Content content : vectorContents) {
            Double score = hitByText.get(content.textSegment().text()).getStoreScore();
            if (score >= params.getMinScore()) {
                above++;
                vectorKept.add(content);
            } else {
                filtered++;
            }
        }
        stage.setPoolAboveMinScore(above);
        stage.setFilteredByMinScore(filtered);

        // ---------- 4. 关键词路（混合检索） ----------
        List<Content> keywordContents = new ArrayList<>();
        Map<String, Integer> keywordRankByText = new HashMap<>();
        if (params.isHybridEnabled()) {
            t = System.currentTimeMillis();
            keywordContents = keywordRetrieve(req.getQuery(), effective.getUuid(), params.getKeywordLimit(),
                    disabledDocUuids, hitByText, keywordRankByText);
            stage.setKeywordMs(System.currentTimeMillis() - t);
        }
        stage.setKeywordReturned(keywordContents.size());

        // ---------- 5. 融合 ----------
        List<Content> fused;
        if (vectorKept.isEmpty() && !keywordContents.isEmpty()) {
            // 向量路无命中时混合检索仍可单独靠关键词兜底（与线上一致）
            fused = keywordContents;
        } else if (!vectorKept.isEmpty() && !keywordContents.isEmpty()) {
            fused = RrfFusion.fuse(List.of(vectorKept, keywordContents), params.getRrfK(),
                    item -> null == item.textSegment() ? "" : item.textSegment().text());
            markRrfScores(fused, vectorKept, keywordContents, params.getRrfK(), hitByText);
        } else {
            fused = vectorKept;
        }
        stage.setFusedReturned(fused.size());

        // 来源标注必须同时看两路：只看关键词路会把「只有关键词路才捞回来的段」也标成 vector+keyword，
        // 而那种段正是混合检索唯一的独立贡献，标错就等于把这个页面存在的理由藏起来了。
        // 判定基准是参与融合的两条路（vectorKept / keywordContents），不是「池里有没有」。
        Set<String> vectorTexts = new HashSet<>();
        for (Content content : vectorKept) {
            vectorTexts.add(content.textSegment().text());
        }
        Set<String> keywordTexts = new HashSet<>(keywordRankByText.keySet());
        for (Content content : fused) {
            String text = content.textSegment().text();
            KbRecallTestResp.Hit detail = hitByText.get(text);
            if (null == detail) {
                continue;
            }
            boolean fromVector = vectorTexts.contains(text);
            boolean fromKeyword = keywordTexts.contains(text);
            if (fromVector && fromKeyword) {
                detail.setSource("vector+keyword");
            } else if (fromKeyword) {
                detail.setSource("keyword");
            } else {
                detail.setSource("vector");
            }
        }

        // ---------- 6. 重排（复用生产实现，同时把分数带出来） ----------
        stage.setRerankInput(fused.size());
        t = System.currentTimeMillis();
        RerankContentReranker.RerankOutcome rerank = rerankWithScores(req.getQuery(), fused, ctx);
        stage.setRerankMs(System.currentTimeMillis() - t);
        stage.setRerankExecuted(rerank.isExecuted());
        List<Content> finalContents = rerank.getContents();
        stage.setRerankReturned(rerank.isExecuted() ? finalContents.size() : 0);
        for (Content content : finalContents) {
            Double score = rerank.getScoreByContent().get(content);
            if (null == score) {
                continue;
            }
            KbRecallTestResp.Hit detail = hitByText.get(content.textSegment().text());
            if (null != detail) {
                detail.setRerankScore(score);
            }
        }

        // ---------- 7. 命中明细：补文档与段序 ----------
        List<KbRecallTestResp.Hit> hits = new ArrayList<>();
        int rank = 0;
        for (Content content : finalContents) {
            KbRecallTestResp.Hit detail = hitByText.get(content.textSegment().text());
            if (null == detail) {
                continue;
            }
            rank++;
            detail.setRank(rank);
            hits.add(detail);
        }
        fillDocumentInfo(hits);
        resp.setHits(hits);
        stage.setFinalReturned(hits.size());

        resp.setParams(params);
        resp.setStage(stage);
        resp.setCostMs(System.currentTimeMillis() - started);
        resp.setNotes(buildNotes(effective, params, stage, kb, disabledDocUuids.size(), hits));
        log.info("kb recall test done,kb:{},query:{},pool:{},vectorKept:{},keyword:{},final:{},cost:{}ms",
                kb.getUuid(), req.getQuery(), stage.getPoolReturned(), stage.getPoolAboveMinScore(),
                stage.getKeywordReturned(), stage.getFinalReturned(), resp.getCostMs());
        return resp;
    }

    /**
     * 池检索：<b>minScore 传 0</b>，把阈值判断留给调用方，这样"被阈值滤掉多少条"才是可见的。
     * 过滤条件与线上一致：限定知识库 + 排除已停用文档。
     */
    private List<EmbeddingMatch<TextSegment>> searchPool(EmbeddingStore<TextSegment> store, Embedding queryEmbedding,
                                                         String kbUuid, int topK, Set<String> disabledDocUuids) {
        Filter filter = new IsEqualTo(AdiConstant.MetadataKey.KB_UUID, kbUuid);
        if (null != disabledDocUuids && !disabledDocUuids.isEmpty()) {
            filter = Filter.and(filter, new IsNotIn(AdiConstant.MetadataKey.KB_ITEM_UUID, disabledDocUuids));
        }
        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(0.0)
                .filter(filter)
                .build());
        return result.matches();
    }

    /**
     * 关键词路召回：与 {@code SegmentExpandProcessor#keywordRetrieve} 同一套（bigram → OR 语义 tsquery
     * → PG 全文检索），异常吞掉退化为空，绝不让测试页因为关键词索引缺失而整体失败。
     */
    private List<Content> keywordRetrieve(String query, String kbUuid, int limit, Set<String> disabledDocUuids,
                                          Map<String, KbRecallTestResp.Hit> hitByText,
                                          Map<String, Integer> keywordRankByText) {
        String tsQuery = BigramTokenizer.tokenizeForTsQuery(query);
        if (StringUtils.isBlank(tsQuery)) {
            return List.of();
        }
        List<KeywordSegmentHit> hits;
        try {
            hits = (null == disabledDocUuids || disabledDocUuids.isEmpty())
                    ? documentSegmentMapper.keywordSearch(kbUuid, tsQuery, limit)
                    : documentSegmentMapper.keywordSearchExcluding(kbUuid, tsQuery, limit, disabledDocUuids);
        } catch (Exception e) {
            log.error("recall test keyword search failed,kbUuid:{},msg:{}", kbUuid, e.getMessage());
            return List.of();
        }
        List<Content> contents = new ArrayList<>();
        int rank = 0;
        for (KeywordSegmentHit hit : hits) {
            if (null == hit || StringUtils.isBlank(hit.getContent())) {
                continue;
            }
            rank++;
            String text = hit.getContent();
            keywordRankByText.putIfAbsent(text, rank);
            KbRecallTestResp.Hit detail = hitByText.get(text);
            if (null == detail) {
                detail = new KbRecallTestResp.Hit();
                detail.setSegmentId(hit.getId());
                detail.setKind(SegmentHitResolver.Kind.TEXT.name());
                detail.setContent(preview(text));
                detail.setTruncated(text.length() > MAX_CONTENT_CHARS);
                detail.setSource("keyword");
                hitByText.put(text, detail);
            }
            detail.setKeywordRank(rank);
            contents.add(toContent(text, kbUuid, hit.getId(), "keyword"));
        }
        return contents;
    }

    /**
     * 记录 RRF 分。<b>注意</b>：顺序来自生产实现 {@link RrfFusion}，分数是为了展示而按同一公式
     * （{@code Σ 1/(k+rank)}）重算的——公式若变更，顺序仍正确、分数需要同步。
     * 名次只在"进入融合的那一路"里算，被阈值滤掉的候选不参与（与线上一致）。
     */
    private void markRrfScores(List<Content> fused, List<Content> vectorList, List<Content> keywordList, int k,
                               Map<String, KbRecallTestResp.Hit> hitByText) {
        int kk = k <= 0 ? RrfFusion.DEFAULT_K : k;
        Map<String, Double> scoreByText = new HashMap<>();
        accumulateRrf(scoreByText, vectorList, kk);
        accumulateRrf(scoreByText, keywordList, kk);
        for (Content content : fused) {
            String text = content.textSegment().text();
            KbRecallTestResp.Hit detail = hitByText.get(text);
            if (null != detail) {
                detail.setRrfScore(scoreByText.get(text));
            }
        }
    }

    private void accumulateRrf(Map<String, Double> scoreByText, List<Content> list, int k) {
        if (null == list) {
            return;
        }
        int rank = 1;
        for (Content content : list) {
            String text = content.textSegment() == null ? null : content.textSegment().text();
            if (null != text) {
                scoreByText.merge(text, 1.0 / (k + rank), Double::sum);
            }
            rank++;
        }
    }

    private RerankContentReranker.RerankOutcome rerankWithScores(String query, List<Content> contents, RetrieverContext ctx) {
        if (contents.isEmpty()) {
            return RerankContentReranker.RerankOutcome.skipped(contents);
        }
        // 走 Bean 拿实现：@Primary 上挂的是 RerankContentReranker，与生产链路同一个实例
        ContentReranker bean = SpringUtil.getBean(ContentReranker.class);
        if (bean instanceof RerankContentReranker concrete) {
            return concrete.rerankWithScores(query, contents, ctx);
        }
        return RerankContentReranker.RerankOutcome.skipped(bean.rerank(query, contents, ctx));
    }

    private Content toContent(String text, String kbUuid, Long segmentId, String from) {
        Map<String, Object> meta = new HashMap<>(4);
        meta.put(AdiConstant.MetadataKey.KB_UUID, kbUuid);
        if (null != segmentId) {
            meta.put(AdiConstant.MetadataKey.SEGMENT_ID, String.valueOf(segmentId));
        }
        meta.put("retrieve_from", from);
        return Content.from(TextSegment.from(text, Metadata.from(meta)));
    }

    private KbRecallTestResp.Hit newHit(SegmentHitResolver.Hit hit, String text) {
        KbRecallTestResp.Hit detail = new KbRecallTestResp.Hit();
        detail.setSegmentId(hit.getSegmentId());
        detail.setKind(hit.getKind().name());
        detail.setSource("vector");
        detail.setContent(preview(text));
        detail.setTruncated(text.length() > MAX_CONTENT_CHARS);
        return detail;
    }

    private String preview(String text) {
        if (null == text) {
            return "";
        }
        return text.length() > MAX_CONTENT_CHARS ? text.substring(0, MAX_CONTENT_CHARS) : text;
    }

    /**
     * pgvector 存的分数是被压到 [0,1] 的 {@code (1+cos)/2}，页面必须给出等价余弦，
     * 否则使用者会拿它去和余弦阈值比较——本仓库为此差点写出过一条假结论。
     */
    private static Double storeScoreToCosine(Double storeScore) {
        return null == storeScore ? null : 2 * storeScore - 1;
    }

    /**
     * 批量补文档标题与段序（与线上出处抬头同一批数据源）
     */
    private void fillDocumentInfo(List<KbRecallTestResp.Hit> hits) {
        if (hits.isEmpty()) {
            return;
        }
        Set<Long> segmentIds = new HashSet<>();
        hits.forEach(hit -> {
            if (null != hit.getSegmentId()) {
                segmentIds.add(hit.getSegmentId());
            }
        });
        Map<Long, DocumentSegment> segmentById = segmentIds.isEmpty()
                ? Map.of() : documentSegmentService.mapByIds(segmentIds);
        Set<String> docUuids = new HashSet<>();
        segmentById.values().forEach(segment -> {
            if (StringUtils.isNotBlank(segment.getDocUuid())) {
                docUuids.add(segment.getDocUuid());
            }
        });
        Map<String, String> titles = docUuids.isEmpty() ? Map.of() : kbDocumentService.mapTitlesByUuids(docUuids);
        for (KbRecallTestResp.Hit hit : hits) {
            DocumentSegment segment = null == hit.getSegmentId() ? null : segmentById.get(hit.getSegmentId());
            if (null == segment) {
                continue;
            }
            hit.setDocUuid(segment.getDocUuid());
            hit.setPosition(segment.getPosition());
            if (StringUtils.isNotBlank(segment.getDocUuid())) {
                hit.setDocTitle(titles.get(segment.getDocUuid()));
            }
        }
    }

    private KbRecallTestResp.EffectiveParams buildParams(KnowledgeBase kb, RetrieverContext ctx, List<String> overridden) {
        KbRecallTestResp.EffectiveParams params = new KbRecallTestResp.EffectiveParams();
        boolean auto = null == kb.getRetrieveMaxResults() || kb.getRetrieveMaxResults() <= 0;
        params.setTopK(auto ? AdiConstant.RAG_RETRIEVE_NUMBER_MAX : kb.getRetrieveMaxResults());
        params.setTopKAuto(auto);
        double minScore = null == kb.getRetrieveMinScore() ? AdiConstant.RAG_MIN_SCORE : kb.getRetrieveMinScore();
        params.setMinScore(minScore);
        params.setMinScoreAsCosine(2 * minScore - 1);
        params.setHybridEnabled(ctx.isHybridEnabled());
        params.setKeywordLimit(ctx.getKeywordLimit());
        params.setRrfK(ctx.getRrfK());
        params.setRerankEnabled(ctx.isRerankEnabled());
        params.setRerankModelId(ctx.getRerankModelId());
        params.setRerankTopN(ctx.getRerankTopN());
        params.setRerankMinScore(ctx.getRerankMinScore());
        params.setNeighborEnabled(ctx.isNeighborEnabled());
        params.setNeighborWindow(ctx.getNeighborWindow());
        // 重排模型名：用与重排器完全相同的解析方式（id<=0 时取第一个可用模型），页面据此提示
        // "开了但没模型可用"这种情况——否则「重排已开启却没生效」无法从界面上看出来
        OpenAiCompatibleScoringModel rerankModel = RerankModelContext.getById(ctx.getRerankModelId());
        params.setRerankModelName(null == rerankModel ? null : rerankModel.getAiModel().getName());
        params.setOverridden(overridden);
        return params;
    }

    /**
     * 把请求里的覆盖值写到配置副本上，返回被覆盖的字段名（用于页面提示"本次参数与库配置不一致"）。
     * <p>
     * 刻意不在请求里做默认值填充：null 一律表示"沿用知识库"。
     */
    private List<String> applyOverrides(KnowledgeBase kb, KbRecallTestReq req) {
        List<String> overridden = new ArrayList<>();
        if (null != req.getTopK()) {
            kb.setRetrieveMaxResults(req.getTopK() <= 0 ? 0 : req.getTopK());
            overridden.add("topK");
        }
        if (null != req.getMinScore()) {
            kb.setRetrieveMinScore(req.getMinScore());
            overridden.add("minScore");
        }
        if (null != req.getHybridEnabled()) {
            kb.setRetrieveHybridEnabled(req.getHybridEnabled());
            overridden.add("hybridEnabled");
        }
        if (null != req.getKeywordLimit()) {
            kb.setRetrieveKeywordLimit(req.getKeywordLimit());
            overridden.add("keywordLimit");
        }
        if (null != req.getRrfK()) {
            kb.setRetrieveRrfK(req.getRrfK());
            overridden.add("rrfK");
        }
        if (null != req.getRerankEnabled()) {
            kb.setRetrieveRerankEnabled(req.getRerankEnabled());
            overridden.add("rerankEnabled");
        }
        if (null != req.getRerankModelId()) {
            kb.setRetrieveRerankModelId(req.getRerankModelId());
            overridden.add("rerankModelId");
        }
        if (null != req.getRerankTopN()) {
            kb.setRetrieveRerankTopN(req.getRerankTopN());
            overridden.add("rerankTopN");
        }
        if (null != req.getRerankMinScore()) {
            kb.setRetrieveRerankMinScore(req.getRerankMinScore());
            overridden.add("rerankMinScore");
        }
        if (null != req.getNeighborEnabled()) {
            kb.setRetrieveNeighborEnabled(req.getNeighborEnabled());
            overridden.add("neighborEnabled");
        }
        if (null != req.getNeighborWindow()) {
            kb.setRetrieveNeighborWindow(req.getNeighborWindow());
            overridden.add("neighborWindow");
        }
        return overridden;
    }

    /**
     * 口径提示：把"看数时最容易误读的三件事"直接写在响应里
     */
    private List<String> buildNotes(KnowledgeBase kb, KbRecallTestResp.EffectiveParams params,
                                    KbRecallTestResp.Stage stage, KnowledgeBase origin, int disabledDocCount,
                                    List<KbRecallTestResp.Hit> hits) {
        List<String> notes = new ArrayList<>();
        notes.add(String.format("候选池 %d 条：任何重排 / 改写 / 扩窗能达到的召回上限就是这个池子，"
                        + "池外的东西救不回来。%s",
                stage.getPoolRequested(),
                params.isTopKAuto() ? "（池大小来自「自动」，知识库未显式配置 retrieve_max_results）" : ""));
        if (stage.getFilteredByMinScore() > 0) {
            notes.add(String.format("minScore=%.3f 把池内 %d 条候选滤掉了（等价余弦 %.3f）。"
                            + "注意 pgvector 的分数是 (1+cos)/2，不是余弦本身。",
                    params.getMinScore(), stage.getFilteredByMinScore(), params.getMinScoreAsCosine()));
        } else {
            notes.add(String.format("minScore=%.3f（等价余弦 %.3f）本次没有滤掉任何池内候选，阈值接近空转。",
                    params.getMinScore(), params.getMinScoreAsCosine()));
        }
        if (!params.isHybridEnabled()) {
            notes.add("本次未启用混合检索：只有向量路。关键词路对中文专有名词、编号类查询往往更稳，"
                    + "可在上面临时打开对比。");
        } else if (stage.getKeywordReturned() == 0) {
            notes.add("混合检索已开启，但关键词路 0 条命中（可能是查询切词后没有可用 bigram，或关键词索引未重建）。");
        } else {
            // 关键词路「独立贡献」：只有靠关键词路才进榜的条数（两路都命中的不算它的功劳）
            long keywordOnly = hits.stream()
                    .filter(h -> "keyword".equals(h.getSource()))
                    .count();
            notes.add(String.format("混合检索已开启：关键词路命中 %d 条，已与向量路做 RRF 融合，"
                            + "其中 %d 条只靠关键词路才进榜（这就是混合检索的独立贡献）。",
                    stage.getKeywordReturned(), keywordOnly));
        }
        if (params.isRerankEnabled() && !stage.isRerankExecuted()) {
            notes.add("重排已开启但本次没有真正执行：候选不足 2 条，或库上没有可用的 rerank 模型。");
        }
        if (!params.isRerankEnabled()) {
            notes.add("本次未启用重排：顺序即召回顺序（融合后）。");
        }
        if (params.isNeighborEnabled()) {
            notes.add(String.format("知识库开启了左右扩窗（±%d 段）：线上问答会在命中的基础上额外送入邻段，"
                    + "但那不改变排名，因此本页不展示邻段。", params.getNeighborWindow()));
        }
        if (disabledDocCount > 0) {
            notes.add(String.format("已停用的 %d 个文档不参与检索（向量路与关键词路都已排除）。", disabledDocCount));
        }
        if (origin.getEmbeddingCount() != null) {
            notes.add(String.format("知识库当前向量数：%d。若池内候选中找不到目标片段，先确认该文档已入库并完成向量化。",
                    origin.getEmbeddingCount()));
        }
        return notes;
    }
}
