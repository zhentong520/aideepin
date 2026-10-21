package com.moyz.adi.common.rag;

import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.RagQueryLogService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.util.UuidUtil;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.function.Function;

import static com.moyz.adi.common.enums.ErrorEnum.B_BREAK_SEARCH;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.*;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

/**
 * 复制dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever并做了少许改动；
 * 增加支持：缓存命中的向量以便后续记录到数据库中
 */
@Slf4j
public class AdiEmbeddingStoreContentRetriever implements ContentRetriever {

    public static final Function<Query, Integer> DEFAULT_MAX_RESULTS = (query) -> 3;
    public static final Function<Query, Double> DEFAULT_MIN_SCORE = (query) -> 0.0;
    public static final Function<Query, Filter> DEFAULT_FILTER = (query) -> null;

    public static final String DEFAULT_DISPLAY_NAME = "Default";

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private final Function<Query, Integer> maxResultsProvider;
    private final Function<Query, Double> minScoreProvider;
    private final Function<Query, Filter> filterProvider;

    private final String displayName;

    /**
     * 新增的特性: 命中的向量及对应的分数
     */
    private final Map<String, Double> embeddingToScore = new HashMap<>();

    /**
     * T7-01（2026-10-21）：命中的 embeddingId → 本次检索的 query_id 映射。
     * <p>
     * 用途：让「回答 → 引用 → 👍👎 反馈」链路能拿到 query_id（feedback 接口以 query_id 为主键）。
     * 一次检索只生成一个 query_id，故同批命中共享同一个值；角色记忆通道不写日志也不填此映射。
     */
    private final Map<String, String> embeddingToQueryId = new HashMap<>();

    private final boolean breakIfSearchMissed;

    /**
     * 可选的召回后置处理器（知识库场景做按模式展开；角色记忆不设置，行为不变）
     */
    private final RetrievedContentProcessor contentPostProcessor;

    /**
     * 二开新增：知识库级检索配置（混合检索 / RRF / rerank 开关），下传给后置处理器
     */
    private final RetrieverContext context;

    public AdiEmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                DEFAULT_MAX_RESULTS,
                DEFAULT_MIN_SCORE,
                DEFAULT_FILTER,
                false,
                null,
                null
        );
    }

    public AdiEmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel,
                                             int maxResults) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                (query) -> maxResults,
                DEFAULT_MIN_SCORE,
                DEFAULT_FILTER,
                false,
                null,
                null
        );
    }

    public AdiEmbeddingStoreContentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel,
                                             Integer maxResults,
                                             Double minScore) {
        this(
                DEFAULT_DISPLAY_NAME,
                embeddingStore,
                embeddingModel,
                (query) -> maxResults,
                (query) -> minScore,
                DEFAULT_FILTER,
                false,
                null,
                null
        );
    }

    private AdiEmbeddingStoreContentRetriever(String displayName,
                                              EmbeddingStore<TextSegment> embeddingStore,
                                              EmbeddingModel embeddingModel,
                                              Function<Query, Integer> dynamicMaxResults,
                                              Function<Query, Double> dynamicMinScore,
                                              Function<Query, Filter> dynamicFilter,
                                              Boolean breakIfSearchMissed,
                                              RetrievedContentProcessor contentPostProcessor,
                                              RetrieverContext context) {
        this.displayName = getOrDefault(displayName, DEFAULT_DISPLAY_NAME);
        this.embeddingStore = ensureNotNull(embeddingStore, "embeddingStore");
        this.embeddingModel = ensureNotNull(
                getOrDefault(embeddingModel, AdiEmbeddingStoreContentRetriever::loadEmbeddingModel),
                "embeddingModel"
        );
        this.maxResultsProvider = getOrDefault(dynamicMaxResults, DEFAULT_MAX_RESULTS);
        this.minScoreProvider = getOrDefault(dynamicMinScore, DEFAULT_MIN_SCORE);
        this.filterProvider = getOrDefault(dynamicFilter, DEFAULT_FILTER);
        this.breakIfSearchMissed = breakIfSearchMissed;
        this.contentPostProcessor = contentPostProcessor;
        this.context = context;
    }

    private static EmbeddingModel loadEmbeddingModel() {
        Collection<EmbeddingModelFactory> factories = loadFactories(EmbeddingModelFactory.class);
        if (factories.size() > 1) {
            throw new RuntimeException("Conflict: multiple embedding models have been found in the classpath. " +
                    "Please explicitly specify the one you wish to use.");
        }

        for (EmbeddingModelFactory factory : factories) {
            return factory.create();
        }

        return null;
    }

    public static AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder builder() {
        return new AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder();
    }

    public static class AdiEmbeddingStoreContentRetrieverBuilder {

        private String displayName;
        private EmbeddingStore<TextSegment> embeddingStore;
        private EmbeddingModel embeddingModel;
        private Function<Query, Integer> dynamicMaxResults;
        private Function<Query, Double> dynamicMinScore;
        private Function<Query, Filter> dynamicFilter;

        private Boolean breakIfSearchMissed;

        private RetrievedContentProcessor contentPostProcessor;

        private RetrieverContext context;

        AdiEmbeddingStoreContentRetrieverBuilder() {
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder maxResults(Integer maxResults) {
            if (maxResults != null) {
                dynamicMaxResults = (query) -> ensureGreaterThanZero(maxResults, "maxResults");
            }
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder minScore(Double minScore) {
            if (minScore != null) {
                dynamicMinScore = (query) -> ensureBetween(minScore, 0, 1, "minScore");
            }
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder filter(Filter filter) {
            if (filter != null) {
                dynamicFilter = (query) -> filter;
            }
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder dynamicMaxResults(Function<Query, Integer> dynamicMaxResults) {
            this.dynamicMaxResults = dynamicMaxResults;
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder dynamicMinScore(Function<Query, Double> dynamicMinScore) {
            this.dynamicMinScore = dynamicMinScore;
            return this;
        }

        public AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder dynamicFilter(Function<Query, Filter> dynamicFilter) {
            this.dynamicFilter = dynamicFilter;
            return this;
        }

        public AdiEmbeddingStoreContentRetrieverBuilder breakIfSearchMissed(boolean breakIfSearchMissed) {
            this.breakIfSearchMissed = breakIfSearchMissed;
            return this;
        }

        public AdiEmbeddingStoreContentRetrieverBuilder contentPostProcessor(RetrievedContentProcessor contentPostProcessor) {
            this.contentPostProcessor = contentPostProcessor;
            return this;
        }

        public AdiEmbeddingStoreContentRetrieverBuilder context(RetrieverContext context) {
            this.context = context;
            return this;
        }

        public AdiEmbeddingStoreContentRetriever build() {
            return new AdiEmbeddingStoreContentRetriever(this.displayName, this.embeddingStore, this.embeddingModel, this.dynamicMaxResults, this.dynamicMinScore, this.dynamicFilter, this.breakIfSearchMissed, this.contentPostProcessor, this.context);
        }


        public String toString() {
            return "AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder(displayName=" + this.displayName + ", embeddingStore=" + this.embeddingStore + ", embeddingModel=" + this.embeddingModel + ", dynamicMaxResults=" + this.dynamicMaxResults + ", dynamicMinScore=" + this.dynamicMinScore + ", dynamicFilter=" + this.dynamicFilter + ", breakIfSearchMissed=" + this.breakIfSearchMissed + ")";
        }
    }

    /**
     * Creates an instance of an {@code EmbeddingStoreContentRetriever} from the specified {@link EmbeddingStore}
     * and {@link EmbeddingModel} found through SPI (see {@link EmbeddingModelFactory}).
     */
    public static AdiEmbeddingStoreContentRetriever from(EmbeddingStore<TextSegment> embeddingStore) {
        return builder().embeddingStore(embeddingStore).build();
    }

    @Override
    public List<Content> retrieve(Query query) {

        long startTime = System.currentTimeMillis();

        //Query vectors are a pure function of (model, text): cache them so repeated questions
        //(FAQ-style traffic) skip the embedding round trip. Retrieved content itself is never
        //cached, so knowledge base edits still take effect on the very next query.
        Embedding embeddedQuery = QueryEmbeddingCache.embed(embeddingModel, query.text());

        //二开（T1-05，2026-10-16）：向量层 ACL 过滤。
        //开关（adi.kb.acl.enabled）关闭时 wrapFilter 原样返回 baseFilter，行为与历史版本完全一致；
        //打开时在调用方 Filter（如 IsIn(KB_UUID, kbUuids)）上追加
        //Or(acl_sensitivity=PUBLIC, kb_owner_uuid=当前用户)，语义见 KbAclFilterBuilder。
        boolean aclEnabled = KbAclFilterBuilder.enabled();
        Filter baseFilter = filterProvider.apply(query);
        Filter effectiveFilter = aclEnabled ? KbAclFilterBuilder.wrapFilter(baseFilter, context, true) : baseFilter;

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddedQuery)
                .maxResults(maxResultsProvider.apply(query))
                .minScore(minScoreProvider.apply(query))
                .filter(effectiveFilter)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        //二开（T1-05，2026-10-16）：应用层双保险——取回后按 chunk metadata 再断言一次，
        //防止向量层过滤被旁路（如存储实现未下推 Filter）。剔除条数计入检索日志 acl_filtered_count。
        //仅知识库链路（context != null）执行；角色记忆通道 context 恒为 null，行为不变。
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        int aclFilteredCount = 0;
        if (aclEnabled && null != context) {
            List<EmbeddingMatch<TextSegment>> allowed = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                if (KbAclFilterBuilder.isAllowed(match.embedded().metadata(), context, true)) {
                    allowed.add(match);
                } else {
                    aclFilteredCount++;
                    log.warn("ACL double-check dropped hit,embeddingId:{},text:{}",
                            match.embeddingId(), StringUtils.abbreviate(match.embedded().text(), 50));
                }
            }
            matches = allowed;
        }

        List<Content> result;
        if (null != contentPostProcessor) {
            // 按模式展开（问题→答案、子块→父段上卷去重），并由处理器登记保留下来的命中分数；
            // Hits without a relational row are dropped (vector-store text is only a placeholder)
            result = contentPostProcessor.process(query, matches, embeddingToScore, context);
        } else {
            result = matches.stream()
                    .peek(item -> {
                        embeddingToScore.put(item.embeddingId(), item.score());
                        log.info("embeddingToScore,embeddingId:{},score:{}", item.embeddingId(), item.score());
                    })
                    .map(EmbeddingMatch::embedded)
                    .map(Content::from)
                    .collect(toList());
        }

        //二开（T1-01，2026-10-16）：检索日志（仅知识库链路），异步写入，失败只 warn 不阻断
        saveQueryLog(query, effectiveFilter, matches, aclFilteredCount, startTime);

//Determine whether to forcibly interrupt the query, if no valid content then stop further operations
        //判断是否要强行中断查询：strict 模式的 miss 判断后移到展开+过滤之后，"无有效内容则中断"
        if (breakIfSearchMissed && CollectionUtils.isEmpty(result)) {
            log.warn("Embedding search missed,query:{}", query.text());
            throw new BaseException(B_BREAK_SEARCH);
        }
        return result;
    }

    /**
     * 二开（T1-01，2026-10-16）：组装并异步落一条检索日志。
     * <ul>
     *   <li>仅知识库链路（context != null）记录；角色记忆通道 context 恒为 null，不记录</li>
     *   <li>hit_segments = ACL 双保险过滤后、展开前的原始命中</li>
     *   <li>query_text = 用户原始问句（截断 500 字符，T7-01 失败案例转评测题的题面来源）</li>
     *   <li>filter_expression = 实际下发向量库的表达式（含 ACL 条件）</li>
     *   <li>全程 try/catch：日志缺失可以被接受，检索被拖垮不行</li>
     *   <li>T7-01：返回本次 query_id（成功时），并把 query_id 绑到命中 embeddingId</li>
     * </ul>
     */
    private String saveQueryLog(Query query, Filter effectiveFilter,
                              List<EmbeddingMatch<TextSegment>> matches,
                              int aclFilteredCount, long startTime) {
        if (null == context) {
            return null;
        }
        final String queryId = UuidUtil.createShort();
        try {
            List<Map<String, Object>> hits = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                Metadata meta = match.embedded().metadata();
                Map<String, Object> hit = new LinkedHashMap<>();
                hit.put("kb_uuid", safeMeta(meta, AdiConstant.MetadataKey.KB_UUID));
                hit.put("segment_id", safeMeta(meta, AdiConstant.MetadataKey.SEGMENT_ID));
                hit.put("doc_uuid", safeMeta(meta, AdiConstant.MetadataKey.DOC_UUID));
                hit.put("score", match.score());
                hits.add(hit);
            }
            List<String> kbUuids;
            if (null != context.getKbUuids() && !context.getKbUuids().isEmpty()) {
                kbUuids = context.getKbUuids();
            } else if (StringUtils.isNotBlank(context.getKbUuid())) {
                kbUuids = List.of(context.getKbUuid());
            } else {
                kbUuids = List.of();
            }
            RagQueryLog row = RagQueryLogService.build(
                    queryId,
                    context.getUserUuid(),
                    kbUuids,
                    String.valueOf(effectiveFilter),
                    JsonUtil.toJson(hits),
                    matches.size(),
                    aclFilteredCount,
                    (int) (System.currentTimeMillis() - startTime),
                    embeddingModelName(),
                    query.text());
            SpringUtil.getBean(RagQueryLogService.class).saveAsync(row);
            // T7-01：把 query_id 绑到本次命中的 embeddingId，供「回答→反馈」链路取用
            for (EmbeddingMatch<TextSegment> match : matches) {
                embeddingToQueryId.put(match.embeddingId(), queryId);
            }
            return queryId;
        } catch (Exception e) {
            log.warn("save rag query log failed,query:{},error:{}", StringUtils.abbreviate(query.text(), 50), e.getMessage());
        }
        return null;
    }

    private static String safeMeta(Metadata metadata, String key) {
        return null != metadata && metadata.containsKey(key) ? String.valueOf(metadata.getString(key)) : "";
    }

    /**
     * embedding 模型配置串（仅用于日志展示；拿不到时留空，不影响检索）
     */
    private static String embeddingModelName() {
        try {
            return StringUtils.defaultString(SpringUtil.getBean(com.moyz.adi.common.config.AdiProperties.class).getEmbeddingModel());
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * aideepin新增方法
     *
     * @return
     */
    public Map<String, Double> getRetrievedEmbeddingToScore() {
        return this.embeddingToScore;
    }

    /**
     * T7-01：本次检索命中的 embeddingId → query_id 映射（供引用与 👍👎 反馈关联）。
     */
    public Map<String, String> getRetrievedEmbeddingToQueryId() {
        return this.embeddingToQueryId;
    }

    @Override
    public String toString() {
        return "AdiEmbeddingStoreContentRetriever{" +
                "displayName='" + displayName + '\'' +
                '}';
    }
}
