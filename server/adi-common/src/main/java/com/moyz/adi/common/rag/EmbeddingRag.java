package com.moyz.adi.common.rag;

import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.interfaces.IRAGService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.util.InputAdaptor;
import org.apache.commons.lang3.StringUtils;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.vo.EmbeddingIngestParam;
import com.moyz.adi.common.vo.InputAdaptorMsg;
import com.moyz.adi.common.vo.RetrieverContext;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.moyz.adi.common.cosntant.AdiConstant.*;
import static com.moyz.adi.common.vo.InputAdaptorMsg.TOKEN_TOO_MUCH_QUESTION;

@Slf4j
public class EmbeddingRag implements IRAGService {

    /**
     * RAG名称，用于区分不同的实例
     */
    @Getter
    private final String name;

    private final EmbeddingModel embeddingModel;

    private final EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 暴露向量库/嵌入模型给召回测试（二开 2026-09-30）：测试页需要"绕过阈值"看完整候选池，
     * 而 {@link #createRetriever} 出来的检索器已经把 minScore 与展开逻辑烧进去了，看不到原始候选。
     * 取的是同一个 Bean，因此池里的分数与线上召回同源。
     */
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        return embeddingStore;
    }

    /**
     * 知识库实例的召回后置处理器（懒加载，避免构造期依赖 Spring 容器）
     */
    private volatile RetrievedContentProcessor segmentExpandProcessor;

    public EmbeddingRag(String name, EmbeddingModel embeddingModel, EmbeddingStore<TextSegment> embeddingStore) {
        this.name = name;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
    }

    /**
     * 对文档切块、向量化并存储到数据库
     *
     * @param document 知识库文档
     * @param params   入库配置参数
     */
    @Override
    public void ingest(Document document, EmbeddingIngestParam params) {
        log.info("EmbeddingRag ingest, strategy:{}, maxSegmentSize:{}, TokenCountEstimator:{}",
                params.getStrategy(), params.getMaxSegmentSize(), params.getTokenEstimator());
        DocumentSplitter documentSplitter = DocumentSplitterFactory.create(
                params.getStrategy(), params.getMaxSegmentSize(), params.getOverlap(),
                params.getCustomSeparator(), TokenEstimatorFactory.create(params.getTokenEstimator()));
        EmbeddingStoreIngestor embeddingStoreIngestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(documentSplitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
        embeddingStoreIngestor.ingest(document);
    }

    /**
     * 创建召回器
     *
     * @param param 条件
     * @return ContentRetriever
     */
    @Override
    public AdiEmbeddingStoreContentRetriever createRetriever(RetrieverCreateParam param) {
        Filter filter = param.getFilter();
        if (param.getExcludedItemUuids() != null && !param.getExcludedItemUuids().isEmpty()) {
            Filter excludeFilter = new IsNotIn(MetadataKey.KB_ITEM_UUID, param.getExcludedItemUuids());
            filter = filter != null ? Filter.and(filter, excludeFilter) : excludeFilter;
        }
        AdiEmbeddingStoreContentRetriever.AdiEmbeddingStoreContentRetrieverBuilder builder = AdiEmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(param.getMaxResults() <= 0 ? RAG_RETRIEVE_NUMBER_DEFAULT : param.getMaxResults())
                .minScore(param.getMinScore() <= 0 ? RAG_MIN_SCORE : param.getMinScore())
                .filter(filter)
                .breakIfSearchMissed(param.isBreakIfSearchMissed());
        if (AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE.equals(name)) {
            // 知识库召回挂"按模式展开"后置处理器（text 直用 / 问题→答案 / 子块→父段上卷去重）；
            // 角色记忆两条通道不设置，行为不变。四个检索入口（流式问答、阻塞问答、工作流、角色聊天KB通道）均经此处自动生效
            builder.contentPostProcessor(getSegmentExpandProcessor());
            // 二开：把知识库级检索配置透传给后置处理器，用于混合检索/RRF/rerank
            // 二开（T3-12，2026-10-19）：调用方未显式携带 context 时（工作流知识检索节点），
            // 按 kbUuid 回读库级检索配置作为默认值，实现「节点三态为空 = 继承库级」；
            // 回读失败只 warn 并退回 none()，与改动前行为一致
            RetrieverContext ctx = param.getContext() == null ? kbLevelContextOrDefault(param) : param.getContext();
            // T3-12：节点级三态覆盖——非 null 时覆盖库级开关，null 时保持库级（继承语义）
            if (null != param.getRerankEnabledOverride()) {
                ctx.setRerankEnabled(param.getRerankEnabledOverride());
            }
            if (null != param.getExpandWindowEnabledOverride()) {
                ctx.setNeighborEnabled(param.getExpandWindowEnabledOverride());
            }
            // 二开（T1-05，2026-10-16）：开关打开时为未显式携带 ACL 的入口（QA/工作流等单库路径）
            // 补齐检索主体与库级 ACL 快照；开关关闭时是恒等操作，行为与历史版本一致
            KbAclFilterBuilder.enrichAclContext(ctx, param.getKbUuid());
            builder.context(ctx);
        }
        return builder.build();
    }

    /**
     * T3-12：未显式携带 {@link RetrieverContext} 的知识库入口（工作流知识检索节点），
     * 按 kbUuid 回读库级检索配置（混合/RRF/rerank/扩窗），复用
     * {@link KnowledgeBaseService#buildRetrieverContext} 这唯一的库级配置读取点。
     * excludedDocUuids 直接复用调用方已算好的停用文档名单，保证关键词路过滤一致。
     */
    private RetrieverContext kbLevelContextOrDefault(RetrieverCreateParam param) {
        if (StringUtils.isBlank(param.getKbUuid())) {
            return RetrieverContext.none();
        }
        try {
            KnowledgeBaseService kbService = SpringUtil.getBean(KnowledgeBaseService.class);
            KnowledgeBase kb = kbService.getOrThrow(param.getKbUuid());
            return kbService.buildRetrieverContext(kb, param.getExcludedItemUuids());
        } catch (Exception e) {
            log.warn("Read kb-level retrieve config failed, fallback to none,kbUuid:{},error:{}",
                    param.getKbUuid(), e.getMessage());
            return RetrieverContext.none();
        }
    }

    private RetrievedContentProcessor getSegmentExpandProcessor() {
        if (null == segmentExpandProcessor) {
            segmentExpandProcessor = SpringUtil.getBean(SegmentExpandProcessor.class);
        }
        return segmentExpandProcessor;
    }

    /**
     * Calculates the maximum number of documents that can be retrieved based on the model's max input tokens.
     * <br/>Uses the maximum segment size in tokens ({@code RAG_MAX_SEGMENT_SIZE_IN_TOKENS}) from chunking as the factor.
     * <p>maxInputTokens must first subtract the tokens consumed by the user question, chat history, and system message;
     * the remaining space is then converted into the number of documents that can be accommodated.</p>
     * <p>This is the "auto" branch: it is what a knowledge base with {@code retrieve_max_results = 0}
     * gets, and it is capped by {@link AdiConstant#RAG_RETRIEVE_NUMBER_MAX}. The cap is the single
     * knob that decides how many candidates any later ranking step -- rerank included -- can ever
     * choose from, so it is set from measurement, not taste; see that constant for the numbers.</p>
     *
     * @param userQuestion   the user's question
     * @param maxInputTokens the model's input token limit (maxInputTokens), not the full context window size
     * @param reservedTokens tokens already consumed by chat history and system message, which will be deducted from the budget
     * @return the maximum number of documents that can be retrieved (0 means no remaining space)
     */
    public static int getRetrieveMaxResults(String userQuestion, int maxInputTokens, int reservedTokens) {
        if (maxInputTokens == 0) {
            return RAG_RETRIEVE_NUMBER_MAX;
        }
        InputAdaptorMsg inputAdaptorMsg = InputAdaptor.isQuestionValid(userQuestion, maxInputTokens);
        if (inputAdaptorMsg.getTokenTooMuch() == TOKEN_TOO_MUCH_QUESTION) {
            log.warn("User question too long, not enough tokens left for retrieved content");
            return 0;
        }
        int maxRetrieveDocLength = Math.max(0,
                maxInputTokens - inputAdaptorMsg.getUserQuestionTokenCount() - reservedTokens);
        if (maxRetrieveDocLength > RAG_RETRIEVE_NUMBER_MAX * RAG_MAX_SEGMENT_SIZE_IN_TOKENS) {
            return RAG_RETRIEVE_NUMBER_MAX;
        }
        return maxRetrieveDocLength / RAG_MAX_SEGMENT_SIZE_IN_TOKENS;
    }
}
