package com.moyz.adi.common.rag;

import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.service.DocumentSegmentChildChunkService;
import com.moyz.adi.common.service.DocumentSegmentQuestionService;
import com.moyz.adi.common.service.DocumentSegmentService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 命中片段左右扩窗的断言。
 * <p>
 * 约定：文档共 5 段，id 11..15 对应 position 0..4，正文写成「第N条：正文」，
 * 与抬头里展示的「第 N 段」编号一致，便于肉眼核对顺序。
 * <p>
 * 重点锁三条不变量：邻段按阅读顺序输出、同一段全局只出现一次、命中自身始终保持命中身份
 * （不能被标成「上下文」）。另外覆盖开关关闭 / 取数失败 / 段行缺失三条降级路径，
 * 以及「扩窗不得虚增命中分数」——否则命中统计与前端出处列表会被污染。
 */
class SegmentExpandProcessorNeighborWindowTest {

    private static final String DOC = "doc-1";
    private static final String KB = "kb-1";
    private static final String META_FROM = "retrieve_from";

    private SegmentExpandProcessor processor;
    private DocumentSegmentService documentSegmentService;
    private KbDocumentService kbDocumentService;
    private ContentReranker contentReranker;

    @BeforeEach
    void setUp() {
        processor = new SegmentExpandProcessor();
        documentSegmentService = mock(DocumentSegmentService.class);
        DocumentSegmentQuestionService questionService = mock(DocumentSegmentQuestionService.class);
        DocumentSegmentChildChunkService childChunkService = mock(DocumentSegmentChildChunkService.class);
        kbDocumentService = mock(KbDocumentService.class);
        contentReranker = mock(ContentReranker.class);
        ReflectionTestUtils.setField(processor, "documentSegmentService", documentSegmentService);
        ReflectionTestUtils.setField(processor, "questionService", questionService);
        ReflectionTestUtils.setField(processor, "childChunkService", childChunkService);
        ReflectionTestUtils.setField(processor, "kbDocumentService", kbDocumentService);
        ReflectionTestUtils.setField(processor, "contentReranker", contentReranker);

        when(questionService.listByEmbeddingIds(anyList())).thenReturn(Collections.emptyList());
        when(childChunkService.listByEmbeddingIds(anyList())).thenReturn(Collections.emptyList());
        when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of(DOC, "物业管理条例"));
        when(documentSegmentService.mapByIds(any())).thenReturn(docRows());
        // rerank 用直通实现：这里测的是扩窗，不是重排模型
        when(contentReranker.rerank(anyString(), anyList(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    // ---------- 开关 ----------

    @Test
    void leavesContentsAloneWhenSwitchIsOff() {
        List<Content> hits = List.of(hit(12L));

        List<Content> result = widen(hits, ctx(false, 1));

        assertSame(hits, result, "开关关闭时必须原样返回，连列表都不重建");
        verify(documentSegmentService, never()).mapNeighborWindow(any(), anyInt());
    }

    @Test
    void leavesContentsAloneWhenWindowIsZero() {
        List<Content> hits = List.of(hit(12L));

        List<Content> result = widen(hits, ctx(true, 0));

        assertSame(hits, result);
        verify(documentSegmentService, never()).mapNeighborWindow(any(), anyInt());
    }

    // ---------- 正常扩窗 ----------

    @Test
    void emitsNeighboursInReadingOrderAroundTheHit() {
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(windowOf(11L, 12L, 13L));

        List<Content> result = pipeline(List.of(hit(12L)), ctx(true, 1));

        assertEquals(3, result.size());
        assertEquals("【物业管理条例 · 第 1 段（上下文）】\n第1条：正文", text(result, 0));
        assertEquals("【物业管理条例 · 第 2 段】\n第2条：正文", text(result, 1), "命中自身不能带「上下文」标记");
        assertEquals("【物业管理条例 · 第 3 段（上下文）】\n第3条：正文", text(result, 2));
    }

    @Test
    void ordersNeighboursByPositionEvenWhenTheLookupReturnsThemShuffled() {
        // 数据层不保证顺序，输出顺序必须由 processor 自己排
        Map<Long, DocumentSegment> shuffled = new LinkedHashMap<>();
        shuffled.put(13L, row(13L, DOC, 2));
        shuffled.put(11L, row(11L, DOC, 0));
        shuffled.put(12L, row(12L, DOC, 1));
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(shuffled);

        List<Content> result = widen(List.of(hit(12L)), ctx(true, 1));

        assertEquals(List.of(11L, 12L, 13L), segmentIds(result));
    }

    @Test
    void emitsEachSegmentOnceWhenTwoHitsAreAdjacent() {
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(windowOf(11L, 12L, 13L, 14L));

        List<Content> result = pipeline(List.of(hit(12L), hit(13L)), ctx(true, 1));

        // 段 11..14 各一次：命中 12 的窗口带出 11/13，命中 13 的窗口只剩 14 是新的
        assertEquals(List.of(11L, 12L, 13L, 14L), segmentIds(result));
        assertEquals(4, result.size());
        // 段 13 既是命中又是邻居：必须保持命中身份（无「上下文」标记）
        assertEquals("【物业管理条例 · 第 3 段】\n第3条：正文", text(result, 2));
    }

    @Test
    void keepsNonRelationalContentUntouchedAndDoesNotWidenIt() {
        Content withoutId = Content.from(TextSegment.from("图节点文本", Metadata.from(Map.of())));

        List<Content> result = widen(List.of(withoutId), ctx(true, 1));

        assertEquals(1, result.size());
        assertEquals("图节点文本", text(result, 0));
        verify(documentSegmentService, never()).mapNeighborWindow(any(), anyInt());
    }

    @Test
    void neighboursCarryOnlyKbAndSegmentId() {
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(windowOf(11L));

        List<Content> result = widen(List.of(hit(12L)), ctx(true, 1));

        Metadata meta = metadata(result, 0);
        assertEquals("neighbor", meta.getString(META_FROM));
        assertEquals(KB, meta.getString(AdiConstant.MetadataKey.KB_UUID));
        assertEquals("11", meta.getString(AdiConstant.MetadataKey.SEGMENT_ID));
        // 不能把命中的 kb_item_uuid 套到邻段上，否则归属错误
        assertNull(meta.getString(AdiConstant.MetadataKey.KB_ITEM_UUID));
        assertEquals(Set.of(AdiConstant.MetadataKey.KB_UUID, AdiConstant.MetadataKey.SEGMENT_ID, META_FROM),
                meta.toMap().keySet(), "邻段元数据应保持最小集");
    }

    // ---------- 降级 ----------

    @Test
    void returnsHitsUnchangedWhenNeighbourLookupThrows() {
        when(documentSegmentService.mapNeighborWindow(any(), anyInt()))
                .thenThrow(new IllegalStateException("db down"));

        List<Content> result = widen(List.of(hit(12L)), ctx(true, 1));

        assertEquals(1, result.size());
        assertEquals("第2条：正文", text(result, 0), "取数失败只能降级为「只给命中」，不能把检索打挂");
    }

    @Test
    void returnsHitsUnchangedWhenZeroRowMatchesTheWindow() {
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(Map.of());

        List<Content> result = widen(List.of(hit(12L)), ctx(true, 1));

        assertEquals(1, result.size());
        assertEquals("第2条：正文", text(result, 0));
    }

    @Test
    void returnsHitsUnchangedWhenNoSegmentRowResolves() {
        when(documentSegmentService.mapByIds(any())).thenReturn(Map.of());

        List<Content> result = widen(List.of(hit(12L)), ctx(true, 1));

        assertEquals(1, result.size());
        assertEquals("第2条：正文", text(result, 0));
        verify(documentSegmentService, never()).mapNeighborWindow(any(), anyInt());
    }

    @Test
    void keepsHitWithoutDocumentUuid() {
        when(documentSegmentService.mapByIds(any())).thenReturn(Map.of(12L, row(12L, "", 1)));

        List<Content> result = widen(List.of(hit(12L)), ctx(true, 1));

        assertEquals(1, result.size());
        assertEquals("第2条：正文", text(result, 0), "没有文档归属就无法判断邻段，应只保留命中");
    }

    // ---------- 与整条管线的交互 ----------

    @Test
    void wideningDoesNotInflateHitScores() {
        when(documentSegmentService.mapByEmbeddingIds(anyList()))
                .thenReturn(Map.of("emb-1", row(12L, DOC, 1)));
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(windowOf(11L, 12L, 13L));

        Map<String, Double> scores = new HashMap<>();
        List<Content> result = processor.process(
                Query.from("第二条怎么规定的"), List.of(match("emb-1", 0.87)), scores, ctx(true, 1));

        assertEquals(3, result.size(), "命中 + 左右邻段");
        // 邻段不是命中：命中分数表只能有真正的召回项，否则命中统计与前端出处列表都会虚增
        assertEquals(Set.of("emb-1"), scores.keySet());
        assertEquals(0.87, scores.get("emb-1"));
        assertEquals("neighbor", metadata(result, 0).getString(META_FROM));
        assertEquals("vector", metadata(result, 1).getString(META_FROM));
    }

    @Test
    void looksUpNeighboursOnceForAllHits() {
        when(documentSegmentService.mapByEmbeddingIds(anyList()))
                .thenReturn(Map.of("emb-1", row(12L, DOC, 1), "emb-2", row(13L, DOC, 2)));
        when(documentSegmentService.mapNeighborWindow(any(), anyInt())).thenReturn(windowOf(11L, 12L, 13L, 14L));

        processor.process(Query.from("q"), List.of(match("emb-1", 0.9), match("emb-2", 0.8)),
                new HashMap<>(), ctx(true, 1));

        verify(documentSegmentService, times(1)).mapNeighborWindow(any(), anyInt());
    }

    @Test
    void wideningIsSkippedWhenContextHasNoNeighbourConfiguration() {
        // 非知识库场景（角色记忆等）走 RetrieverContext.none()，行为必须与扩窗前完全一致
        when(documentSegmentService.mapByEmbeddingIds(anyList()))
                .thenReturn(Map.of("emb-1", row(12L, DOC, 1)));

        List<Content> result = processor.process(Query.from("q"), List.of(match("emb-1", 0.9)),
                new HashMap<>(), null);

        assertEquals(1, result.size());
        verify(documentSegmentService, never()).mapNeighborWindow(any(), anyInt());
    }

    // ---------- helpers ----------

    @SuppressWarnings("unchecked")
    private List<Content> widen(List<Content> contents, RetrieverContext ctx) {
        Object result = ReflectionTestUtils.invokeMethod(processor, "withNeighborWindow", contents, ctx);
        assertNotNull(result);
        return (List<Content>) result;
    }

    /** 扩窗 + 出处抬头连着跑，才能断言模型最终看到的文本 */
    @SuppressWarnings("unchecked")
    private List<Content> pipeline(List<Content> contents, RetrieverContext ctx) {
        Object widened = ReflectionTestUtils.invokeMethod(processor, "withNeighborWindow", contents, ctx);
        Object cited = ReflectionTestUtils.invokeMethod(processor, "withCitation", widened);
        assertNotNull(cited);
        return (List<Content>) cited;
    }

    private static RetrieverContext ctx(boolean enabled, int window) {
        return RetrieverContext.builder()
                .kbUuid(KB)
                .hybridEnabled(false)
                .rrfK(60)
                .keywordLimit(20)
                .rerankEnabled(false)
                .rerankTopN(0)
                .rerankMinScore(0.0)
                .excludedDocUuids(Set.of())
                .neighborEnabled(enabled)
                .neighborWindow(window)
                .build();
    }

    private static Content hit(Long segmentId) {
        return Content.from(TextSegment.from(body(segmentId), Metadata.from(Map.of(
                AdiConstant.MetadataKey.SEGMENT_ID, String.valueOf(segmentId),
                AdiConstant.MetadataKey.KB_UUID, KB,
                META_FROM, "vector"))));
    }

    /** 正文与段序对齐：id 11..15 → 第1条..第5条，方便直接核对该段是第几段 */
    private static String body(long segmentId) {
        return "第" + (segmentId - 10) + "条：正文";
    }

    private static EmbeddingMatch<TextSegment> match(String embeddingId, double score) {
        return new EmbeddingMatch<>(score, embeddingId, null,
                TextSegment.from("vector-store placeholder", Metadata.from(Map.of(
                        AdiConstant.MetadataKey.KB_UUID, KB,
                        AdiConstant.MetadataKey.KB_ITEM_UUID, DOC))));
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

    private static List<Long> segmentIds(List<Content> contents) {
        List<Long> ids = new ArrayList<>();
        for (Content content : contents) {
            ids.add(Long.parseLong(
                    content.textSegment().metadata().getString(AdiConstant.MetadataKey.SEGMENT_ID)));
        }
        return ids;
    }

    private static Metadata metadata(List<Content> contents, int index) {
        return contents.get(index).textSegment().metadata();
    }

    private static String text(List<Content> contents, int index) {
        return contents.get(index).textSegment().text();
    }
}
