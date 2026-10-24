package com.moyz.adi.common.rag;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.helper.RerankModelContext;
import com.moyz.adi.common.service.DocumentSegmentChildChunkService;
import com.moyz.adi.common.service.DocumentSegmentQuestionService;
import com.moyz.adi.common.service.DocumentSegmentService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.moyz.adi.common.constant.AdiConstant.SplitStrategy.SENTENCE_CN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 检索回归基线（T6-02）：把已验证的调参效果固化成机器断言，改检索参数跑一次就知道有没有退化。
 * <p>
 * 每条基线对应一次已实测生效的迁移（编号见方法注释），全部用可控语料 + mock/离线路径，
 * 不依赖外部 LLM / 嵌入 API —— 基线测的是「参数被改掉会立刻红」，不是重新跑分。
 * <ul>
 *   <li>025 中文分句：块必须收在句界上，递归切分会拦腰截断条款</li>
 *   <li>026 左右扩窗：±1 邻段、阅读顺序、命中身份保持、不虚增命中分数</li>
 *   <li>027 候选池上限：池天花板 = {@link AdiConstant#RAG_RETRIEVE_NUMBER_MAX} = 10</li>
 *   <li>030 Rerank：开关关闭 / 模型缺失时直通原顺序（可预测的降级）</li>
 *   <li>017 混合检索：RRF 融合去重两路命中，同段不重复计数</li>
 * </ul>
 *
 * <p><b>候选池天花板（铁结论，写死在这里防止后人踩坑）</b>：
 * 检索条数是排序侧一切的硬天花板——重排、扩窗、关键词路都只能在「已被召回的候选」里做文章。
 * 实测（73 条中文金标，sentence_cn/512，37 篇产品文档）：池=3 → 答案在池内 86.3%；
 * 池=5 → 95.9%；池=10 → 100%（见 AdiConstant.RAG_RETRIEVE_NUMBER_MAX 注释）。
 * 另一组实测：topK=100 时向量路把全库 57 个段全部召回（池=57 = 全库覆盖），
 * 此时关键词路对融合结果的贡献为 0（向量路已包含关键词路能找到的一切）——
 * <b>要展示/度量关键词路的价值，候选池必须小于全库段数</b>，否则融合对比是无效实验。
 */
class RetrievalRegressionBaselineTest {

    private static final TokenCountEstimator ESTIMATOR =
            new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);

    // ---------- 025 中文分句基线 ----------

    /**
     * 条款语料：每条都以句号收尾，且包含「…但下列情形除外」这类压在段边界上的尾巴。
     * recursive 在中文上退化为按字符切，会把尾巴切进下一块；sentence_cn 必须整句成块。
     */
    private static final String REGULATION_TEXT =
            "第一条 为规范物业管理活动，维护业主和物业服务企业的合法权益，制定本条例。"
            + "第二条 本条例所称物业管理，是指业主通过选聘物业服务企业，对房屋及配套的设施设备进行维修、养护、管理的活动，"
            + "但单栋建筑业主共同决定自行管理的情形除外。"
            + "第三条 国家提倡业主通过公开、公平、公正的市场竞争机制选择物业服务企业。"
            + "第四条 业主大会选聘物业服务企业前，应当经专有部分面积过半数且人数过半数的业主同意，"
            + "但酬金制计费方式经合同另有约定的除外。"
            + "第五条 物业服务收费应当遵循合理、公开以及费用与服务水平相适应的原则。"
            + "第六条 县级以上地方人民政府房地产行政主管部门应当及时处理业主的投诉。";

    @Test
    void baseline025_sentenceCnNeverCutsAcrossClauseBoundaries() {
        Document text = new DefaultDocument(REGULATION_TEXT);

        // 段大小 150：每条单句都在限内，块只在句界上合并；recursive 在中文上没有任何可用边界，
        // 同尺寸下只能按字符硬切。超大句（>size）会走兜底子切，那是另一条路径，不进本基线。
        List<TextSegment> cnChunks = DocumentSplitterFactory.create(SENTENCE_CN, 150, 0, "", ESTIMATOR)
                .split(text);
        List<TextSegment> recursiveChunks = DocumentSplitterFactory.create("recursive", 150, 0, "", ESTIMATOR)
                .split(text);

        assertTrue(cnChunks.size() > 1, "语料必须被切成多块，否则基线没有意义");
        String joined = String.join("", cnChunks.stream().map(TextSegment::text).toList());
        assertEquals(stripSpaces(REGULATION_TEXT), stripSpaces(joined), "切分不得丢失或改写原文");

        // 基线断言：除最后一块外，每块必须收在 CJK 句末标点上（整句成块，尾巴不被切走）
        for (int i = 0; i < cnChunks.size(); i++) {
            String chunk = cnChunks.get(i).text().strip();
            assertFalse(chunk.isBlank(), "不得产生空块");
            if (i < cnChunks.size() - 1) {
                assertTrue(chunk.endsWith("。") || chunk.endsWith("！") || chunk.endsWith("？") || chunk.endsWith("；"),
                        "sentence_cn 第 " + i + " 块必须收在句界上，实际收尾：" + tail(chunk, 12));
            }
            assertTrue(chunk.contains("除外") == false || chunk.endsWith("。"),
                    "「…除外」这类条款尾巴不得独立成块悬空");
        }

        // 对照组：recursive 在同样语料上确实会拦腰截断（这正是 025 要修的病，一旦它也不再截断，
        // 说明 langchain4j 升级改变了行为，对照关系失效，需要重审本基线）
        long truncated = recursiveChunks.stream()
                .limit(recursiveChunks.size() - 1)
                .map(chunk -> chunk.text().strip())
                .filter(chunk -> !(chunk.endsWith("。") || chunk.endsWith("！") || chunk.endsWith("？") || chunk.endsWith("；")))
                .count();
        assertTrue(truncated > 0, "recursive 对照组应存在跨句界的块；当前为 0，对照失效需重审基线");
    }

    // ---------- 026 左右扩窗基线 ----------

    private static final String DOC = "doc-1";
    private static final String KB = "kb-1";
    private static final String META_FROM = "retrieve_from";

    private SegmentExpandProcessor processor;
    private DocumentSegmentService documentSegmentService;

    @BeforeEach
    void setUpProcessor() {
        processor = new SegmentExpandProcessor();
        documentSegmentService = mock(DocumentSegmentService.class);
        DocumentSegmentQuestionService questionService = mock(DocumentSegmentQuestionService.class);
        DocumentSegmentChildChunkService childChunkService = mock(DocumentSegmentChildChunkService.class);
        KbDocumentService kbDocumentService = mock(KbDocumentService.class);
        ContentReranker contentReranker = mock(ContentReranker.class);
        ReflectionTestUtils.setField(processor, "documentSegmentService", documentSegmentService);
        ReflectionTestUtils.setField(processor, "questionService", questionService);
        ReflectionTestUtils.setField(processor, "childChunkService", childChunkService);
        ReflectionTestUtils.setField(processor, "kbDocumentService", kbDocumentService);
        ReflectionTestUtils.setField(processor, "contentReranker", contentReranker);

        when(questionService.listByEmbeddingIds(anyList())).thenReturn(Collections.emptyList());
        when(childChunkService.listByEmbeddingIds(anyList())).thenReturn(Collections.emptyList());
        when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of(DOC, "物业管理条例"));
        when(contentReranker.rerank(anyString(), anyList(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    void baseline026_neighborWindowWidensByOneWithoutInflatingHitScores() {
        // 基线 1：默认窗口就是 ±1（026 实测 w=1 拿满增益，w=2 零收益，见 RetrieverContext 注释）
        assertEquals(1, RetrieverContext.DEFAULT_NEIGHBOR_WINDOW, "026 把默认窗口定为 ±1，改动需重新评测");
        assertEquals(1, RetrieverContext.none().getNeighborWindow(), "无增强上下文的默认窗口同样是 ±1");

        // 基线 2：命中第 2 段，window=1 → 输出 1/2/3 三段、阅读顺序、命中身份保持、邻段标记 neighbor
        when(documentSegmentService.mapByIds(any())).thenReturn(docRows());
        when(documentSegmentService.mapByEmbeddingIds(anyList()))
                .thenReturn(Map.of("emb-1", row(12L, DOC, 1)));
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(windowOf(11L, 12L, 13L));

        Map<String, Double> scores = new HashMap<>();
        List<Content> result = processor.process(
                Query.from("第二条怎么规定的"), List.of(match("emb-1", 0.87)), scores, ctx(true, 1));

        assertEquals(List.of("11", "12", "13"), segmentIds(result), "邻段必须按阅读顺序围绕命中展开");
        assertEquals("neighbor", metadata(result, 0).getString(META_FROM), "左邻必须标为上下文");
        assertEquals("vector", metadata(result, 1).getString(META_FROM), "命中自身不能被标成上下文");
        assertEquals("neighbor", metadata(result, 2).getString(META_FROM), "右邻必须标为上下文");
        // 扩窗不得虚增命中分数：分数表只能有真正的召回项
        assertEquals(Set.of("emb-1"), scores.keySet(), "邻段不得写入命中分数表");
        assertEquals(0.87, scores.get("emb-1"));
    }

    // ---------- 027 候选池上限基线 ----------

    @Test
    void baseline027_candidatePoolCeilingIsTenAndNeverExceedsIt() {
        // 基线 1：预算充足（maxInputTokens=0 表示不做预算换算）时池就是天花板 10（027：3→0 自适应 + 上限 10）
        assertEquals(AdiConstant.RAG_RETRIEVE_NUMBER_MAX, EmbeddingRag.getRetrieveMaxResults("短问题", 0, 0),
                "池天花板必须保持 10；调整前先重跑评测归因");
        // 基线 2：预算换算结果不得超过天花板
        int pool = EmbeddingRag.getRetrieveMaxResults("短问题", 100_000, 0);
        assertTrue(pool <= AdiConstant.RAG_RETRIEVE_NUMBER_MAX, "任何预算下池都不得突破天花板 10");
        // 基线 3：预算紧张时按 RAG_MAX_SEGMENT_SIZE_IN_TOKENS(=1000) 一段收缩：
        //  预算 4100 扣掉短问题（约 2 token）≈ 4098 → 4 段；预算 500 - 历史 600 < 0 → 0 段
        assertEquals(4, EmbeddingRag.getRetrieveMaxResults("问", 4100, 0),
                "预算 4100 应换算成 4 段（每段 1000 token）");
        assertEquals(0, EmbeddingRag.getRetrieveMaxResults("问", 500, 600),
                "预算被问题/历史吃光时池必须为 0，不得放行");
        // 基线 4：问题本身超预算时直接 0（保护模型窗口）
        assertEquals(0, EmbeddingRag.getRetrieveMaxResults("x".repeat(10_000), 2048, 0),
                "问题过长导致预算为负时必须返回 0");
    }

    // ---------- 030 Rerank 基线 ----------

    @Test
    void baseline030_rerankDisabledOrMissingModelKeepsOriginalOrder() {
        RerankModelContext.clearAll(); // 离线路径：注册表为空 = 无 rerank 模型
        List<Content> contents = List.of(
                contentWithSegmentId(11L, "第一条正文"),
                contentWithSegmentId(12L, "第二条正文"),
                contentWithSegmentId(13L, "第三条正文"));

        // 基线 1：开关关闭 → 直通原顺序、executed=false（030 开 rerank 前的默认行为）
        RerankContentReranker.RerankOutcome off =
                new RerankContentReranker().rerankWithScores("q", contents, RetrieverContext.none());
        assertFalse(off.isExecuted(), "开关关闭时不得触发模型调用");
        assertEquals(List.of("11", "12", "13"), segmentIds(off.getContents()), "关闭时必须原顺序直通");

        // 基线 2：开关打开但模型缺失 → 可预测降级（原顺序 + executed=false），不允许把检索打挂
        RetrieverContext enabledButMissing = RetrieverContext.builder()
                .kbUuid(KB)
                .hybridEnabled(false)
                .rerankEnabled(true)
                .rerankModelId(999L)
                .rerankTopN(0)
                .rerankMinScore(0.0)
                .excludedDocUuids(Set.of())
                .neighborEnabled(false)
                .neighborWindow(1)
                .build();
        RerankContentReranker.RerankOutcome missing =
                new RerankContentReranker().rerankWithScores("q", contents, enabledButMissing);
        assertFalse(missing.isExecuted(), "模型缺失是跳过而不是失败");
        assertEquals(List.of("11", "12", "13"), segmentIds(missing.getContents()), "模型缺失时保持召回原顺序");
    }

    // ---------- 017 混合检索基线 ----------

    @Test
    void baseline017_rrfFusesVectorAndKeywordPathsAndDedupsSameSegment() {
        // 向量路按余弦序：segB > segA > segC；关键词路：segA（精确词命中）> segD > segB
        List<String> vectorPath = List.of("segB", "segA", "segC");
        List<String> keywordPath = List.of("segA", "segD", "segB");

        List<String> fused = RrfFusion.fuse(List.of(vectorPath, keywordPath), 60, s -> s);

        // 基线 2：融合 = 两路并集，同段去重只出现一次
        assertEquals(Set.of("segA", "segB", "segC", "segD"), Set.copyOf(fused), "融合必须并集去重");
        assertEquals(4, fused.size(), "同一段两路都命中时只能输出一次");
        // 基线 2：RRF 分数可手工复核（公式 score=Σ1/(k+rank)，rank 从 1 起）
        // segA = 向量 rank2 + 关键词 rank1 = 1/62 + 1/61 ≈ 0.03253
        // segB = 向量 rank1 + 关键词 rank3 = 1/61 + 1/63 ≈ 0.03227 → segA 第一
        assertEquals("segA", fused.get(0), "双路命中且名次更优的 segA 必须排第一");
        assertEquals("segB", fused.get(1), "双路命中的 segB 排第二");
        double segA = 1.0 / 62 + 1.0 / 61;
        double segB = 1.0 / 61 + 1.0 / 63;
        assertTrue(segA > segB, "segA 两路综合名次更优，融合分必须更高");
        // 基线 3：单路独占的段名次低于双路命中段（segC/segD 只有一路贡献分）
        assertEquals(Set.of("segC", "segD"), Set.copyOf(fused.subList(2, 4)), "单路命中的段排在双路命中段之后");
    }

    @Test
    void baseline017_keywordPathContributesNothingWhenPoolCoversWholeCorpus() {
        // 候选池天花板的直接推论（铁结论）：池 = 全库段数时，向量路已包含关键词路能找到的一切，
        // RRF 融合对「新增内容」的贡献恒为 0。要度量关键词路价值，池必须 < 全库段数。
        // 复现 57 段全库实验：池=57（topK=100 被库规模截断到 57）
        List<String> wholeCorpus = new ArrayList<>();
        for (int i = 0; i < 57; i++) {
            wholeCorpus.add("seg" + i);
        }
        // 关键词路再强，也只能命中全库已有的段
        List<String> keywordPath = List.of("seg3", "seg50", "seg7");

        List<String> fused = RrfFusion.fuse(List.of(wholeCorpus, keywordPath), 60, s -> s);

        assertEquals(wholeCorpus.size(), fused.size(), "池覆盖全库时融合不得新增任何内容");
        assertTrue(fused.containsAll(keywordPath), "关键词路命中的段已在向量池内");
        // 结论注释：此时关键词路只有「改变排序」的价值，没有「补充内容」的价值；
        // 展示关键词路价值（如评估 hybrid 对 recall 的增益）时，必须把 topK 设为小于全库段数。
    }

    // ---------- helpers ----------

    private static RetrieverContext ctx(boolean neighborEnabled, int window) {
        return RetrieverContext.builder()
                .kbUuid(KB)
                .hybridEnabled(false)
                .rrfK(60)
                .keywordLimit(20)
                .rerankEnabled(false)
                .rerankTopN(0)
                .rerankMinScore(0.0)
                .excludedDocUuids(Set.of())
                .neighborEnabled(neighborEnabled)
                .neighborWindow(window)
                .build();
    }

    private static EmbeddingMatch<TextSegment> match(String embeddingId, double score) {
        return new EmbeddingMatch<>(score, embeddingId, null,
                TextSegment.from("vector-store placeholder", Metadata.from(Map.of(
                        AdiConstant.MetadataKey.KB_UUID, KB,
                        AdiConstant.MetadataKey.KB_ITEM_UUID, DOC))));
    }

    private static Content contentWithSegmentId(long id, String text) {
        return Content.from(TextSegment.from(text, Metadata.from(Map.of(
                AdiConstant.MetadataKey.SEGMENT_ID, String.valueOf(id),
                AdiConstant.MetadataKey.KB_UUID, KB,
                META_FROM, "vector"))));
    }

    /** 文档共 5 段：id 11..15 对应 position 0..4 */
    private static Map<Long, DocumentSegment> docRows() {
        Map<Long, DocumentSegment> rows = new HashMap<>();
        for (long id = 11; id <= 15; id++) {
            rows.put(id, row(id, DOC, (int) (id - 11)));
        }
        return rows;
    }

    private static Map<Long, DocumentSegment> windowOf(Long... ids) {
        Map<Long, DocumentSegment> rows = new LinkedHashMap<>();
        for (Long id : ids) {
            rows.put(id, row(id, DOC, (int) (id - 11)));
        }
        return rows;
    }

    private static DocumentSegment row(Long id, String docUuid, Integer position) {
        DocumentSegment segment = new DocumentSegment();
        segment.setId(id);
        segment.setDocUuid(docUuid);
        segment.setPosition(position);
        segment.setKbUuid(KB);
        segment.setContent(body(id));
        return segment;
    }

    private static String body(long segmentId) {
        return "第" + (segmentId - 10) + "条：正文";
    }

    private static List<String> segmentIds(List<Content> contents) {
        List<String> ids = new ArrayList<>();
        for (Content content : contents) {
            ids.add(content.textSegment().metadata().getString(AdiConstant.MetadataKey.SEGMENT_ID));
        }
        return ids;
    }

    private static Metadata metadata(List<Content> contents, int index) {
        return contents.get(index).textSegment().metadata();
    }

    private static String stripSpaces(String text) {
        return text.replaceAll("\\s+", "");
    }

    private static String tail(String text, int n) {
        return text.length() <= n ? text : text.substring(text.length() - n);
    }
}
