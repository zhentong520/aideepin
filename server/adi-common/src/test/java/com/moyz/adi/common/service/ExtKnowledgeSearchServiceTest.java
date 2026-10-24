package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.extapi.ExtKbRetrieveResp;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.rag.AdiEmbeddingStoreContentRetriever;
import com.moyz.adi.common.rag.EmbeddingRag;
import com.moyz.adi.common.rag.EmbeddingRagContext;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExtKnowledgeSearchService 多库检索测试（T1-08）。
 * <p>
 * <b>检索内核复用证明</b>（本测试的结构本身就是证明）：
 * mocked {@link EmbeddingRag#createRetriever} 返回的是一个<b>真实的</b>
 * {@link AdiEmbeddingStoreContentRetriever}（生产检索内核），其内部的
 * {@code embeddingStore.search()} 用 mock 向量库承接。因此：
 * <ol>
 *   <li>{@code verify(embeddingStore, times(N)).search(any())}——检索真实地经过内核里的
 *       {@code embeddingStore.search}（全仓库唯一底层检索入口）。若服务旁路另写
 *       {@code embeddingStore.search}（例如自建检索），此处次数为 0，测试立刻失败；</li>
 *   <li>captured {@link RetrieverCreateParam} 断言 filter/maxResults/kbUuid 与生产问答链路
 *       （KnowledgeBaseService.doBlockingAsk:935）同构；</li>
 *   <li>响应 score 来自内核登记的 {@code getRetrievedEmbeddingToScore()}——只有内核知道分数。</li>
 * </ol>
 * 多库含无权库：返回有权库结果 + skippedKbUuids 正确。
 */
@ExtendWith(MockitoExtension.class)
class ExtKnowledgeSearchServiceTest {

    private static final String KB_READABLE = "kb-readable";
    private static final String KB_DENIED = "kb-denied";
    private static final String KB_MISSING = "kb-not-exists";

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private KbDocumentService kbDocumentService;

    @Mock
    private DocumentSegmentService documentSegmentService;

    @Mock
    private DocumentSegmentQuestionService documentSegmentQuestionService;

    @Mock
    private DocumentSegmentChildChunkService documentSegmentChildChunkService;

    @Mock
    private EmbeddingStore<TextSegment> embeddingStore;

    @Mock
    private EmbeddingModel embeddingModel;

    private ExtKnowledgeSearchService service;
    private EmbeddingRag mockedRag;

    @BeforeEach
    void setUp() {
        service = new ExtKnowledgeSearchService();
        ReflectionTestUtils.setField(service, "knowledgeBaseService", knowledgeBaseService);
        ReflectionTestUtils.setField(service, "kbDocumentService", kbDocumentService);
        ReflectionTestUtils.setField(service, "documentSegmentService", documentSegmentService);
        ReflectionTestUtils.setField(service, "documentSegmentQuestionService", documentSegmentQuestionService);
        ReflectionTestUtils.setField(service, "documentSegmentChildChunkService", documentSegmentChildChunkService);
        ReflectionTestUtils.setField(service, "adiProperties", new AdiProperties());

        // 知识库实体：kb-readable 属于 user-a；kb-denied 属于 user-b 且非公开
        KnowledgeBase readable = kb(KB_READABLE, "user-a", false);
        KnowledgeBase denied = kb(KB_DENIED, "user-b", false);
        lenient().when(knowledgeBaseService.getOrThrow(KB_READABLE)).thenReturn(readable);
        lenient().when(knowledgeBaseService.getOrThrow(KB_DENIED)).thenReturn(denied);
        lenient().when(knowledgeBaseService.getOrThrow(KB_MISSING))
                .thenThrow(new BaseException(A_DATA_NOT_FOUND));

        // 两个库共用同一套库级检索配置来源（mock buildRetrieverContext）
        lenient().when(knowledgeBaseService.buildRetrieverContext(any(KnowledgeBase.class), anySet()))
                .thenAnswer(inv -> com.moyz.adi.common.vo.RetrieverContext.none());

        lenient().when(kbDocumentService.listDisabledItemUuids(anyString())).thenReturn(List.of());
        lenient().when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of());
        lenient().when(documentSegmentQuestionService.listByEmbeddingIds(any())).thenReturn(List.of());
        lenient().when(documentSegmentChildChunkService.listByEmbeddingIds(any())).thenReturn(List.of());

        // mocked EmbeddingRag：捕获 createParam，返回【真实的检索内核】
        mockedRag = mock(EmbeddingRag.class);
        lenient().when(mockedRag.getName()).thenReturn(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE);
        lenient().when(mockedRag.createRetriever(any(RetrieverCreateParam.class))).thenAnswer(inv -> {
            RetrieverCreateParam param = inv.getArgument(0);
            return AdiEmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(param.getMaxResults())
                    .minScore(param.getMinScore())
                    .filter(param.getFilter())
                    .breakIfSearchMissed(param.isBreakIfSearchMissed())
                    .context(param.getContext())
                    .build();
        });
        EmbeddingRagContext.add(mockedRag);
    }

    @AfterEach
    void tearDown() {
        EmbeddingRagContext.remove(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE);
    }

    @DisplayName("多库含无权库：返回有权库结果，无权/不存在库进 skippedKbUuids")
    @Test
    void multiKbWithDeniedKbSkipsSilently() {
        User userA = user("user-a");
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();

        ExtKbRetrieveResp resp = service.retrieve(userA,
                List.of(KB_READABLE, KB_DENIED, KB_MISSING), "what is adi", 5);

        // 有权库的结果正常返回
        assertEquals(1, resp.getItems().size(), "应只返回有权库（kb-readable）的命中");
        ExtKbRetrieveResp.Item item = resp.getItems().get(0);
        assertEquals("hello adi content", item.getContent());
        assertEquals(KB_READABLE, item.getKbUuid());
        assertEquals("doc-1", item.getDocUuid());
        assertEquals(0.87, item.getScore(), 1e-9, "score 必须来自内核登记的 embeddingToScore");

        // 无权 + 不存在 → skipped，不报错
        assertTrue(resp.getSkippedKbUuids().contains(KB_DENIED), "无权库必须静默跳过");
        assertTrue(resp.getSkippedKbUuids().contains(KB_MISSING), "不存在的库必须静默跳过");
        assertEquals(2, resp.getSkippedKbUuids().size());
    }

    @DisplayName("检索内核复用证明：search 发生在内核内的 embeddingStore.search，且参数与问答链路同构")
    @Test
    void retrievalGoesThroughTheKernel() {
        User userA = user("user-a");
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();

        service.retrieve(userA, List.of(KB_READABLE), "what is adi", 5);

        // 1. embeddingStore.search 只在内核 AdiEmbeddingStoreContentRetriever.retrieve 里被调用：
        //    每库恰好一次；若本服务旁路另写检索，此处会是 0 次
        verify(embeddingStore, times(1)).search(any(EmbeddingSearchRequest.class));

        // 2. createParam 与生产问答链路（doBlockingAsk:935）同构
        ArgumentCaptor<RetrieverCreateParam> captor = ArgumentCaptor.forClass(RetrieverCreateParam.class);
        verify(mockedRag).createRetriever(captor.capture());
        RetrieverCreateParam param = captor.getValue();
        assertEquals(KB_READABLE, param.getKbUuid());
        assertEquals(5, param.getMaxResults(), "topK=5 应透传给内核");
        assertEquals(0.6, param.getMinScore(), 1e-9, "未配置时取 RAG_MIN_SCORE");
        assertEquals(AdiConstant.RAG_MIN_SCORE, param.getMinScore(), 0);
        assertTrue(param.getFilter() instanceof IsEqualTo, "库过滤条件应与问答链路同为 IsEqualTo(KB_UUID, kbUuid)");
        assertEquals(AdiConstant.MetadataKey.KB_UUID, ((IsEqualTo) param.getFilter()).key());
        assertEquals(KB_READABLE, ((IsEqualTo) param.getFilter()).comparisonValue());
        assertFalse(param.isBreakIfSearchMissed(), "ext 检索不因空命中抛错");
        // 3. 检索主体与 ACL 快照已填进 RetrieverContext（内核 wrapFilter/isAllowed 的输入）
        assertEquals("user-a", param.getContext().getUserUuid());
        assertEquals(KB_READABLE, param.getContext().getAclInfos().get(KB_READABLE).getKbUuid());
    }

    @DisplayName("topK 超过 adi.ext.max-top-k 时被封顶")
    @Test
    void topKIsCappedByExtConfig() {
        User userA = user("user-a");
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();
        AdiProperties properties = (AdiProperties) ReflectionTestUtils.getField(service, "adiProperties");
        properties.getExt().setMaxTopK(50);

        service.retrieve(userA, List.of(KB_READABLE), "what is adi", 500);

        ArgumentCaptor<RetrieverCreateParam> captor = ArgumentCaptor.forClass(RetrieverCreateParam.class);
        verify(mockedRag).createRetriever(captor.capture());
        assertEquals(50, captor.getValue().getMaxResults(), "topK=500 必须被 max-top-k=50 封顶");
    }

    @DisplayName("metadata 只输出白名单字段")
    @Test
    void metadataIsWhitelisted() {
        User userA = user("user-a");
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();

        ExtKbRetrieveResp resp = service.retrieve(userA, List.of(KB_READABLE), "what is adi", 5);

        Map<String, Object> metadata = resp.getItems().get(0).getMetadata();
        assertTrue(metadata.containsKey("kb_uuid"));
        assertTrue(metadata.containsKey("segment_id"));
        assertEquals(metadata.keySet(), java.util.Set.of("kb_uuid", "segment_id"),
                "只允许白名单键，测试向量里塞的 secret 字段必须被剔除");
    }

    @DisplayName("无权用户（非 owner 且库非公开）不能通过 ext 检索读到该库")
    @Test
    void nonOwnerUserCannotReadPrivateKb() {
        User userB = user("user-b");
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();

        ExtKbRetrieveResp resp = service.retrieve(userB, List.of(KB_READABLE), "what is adi", 5);

        assertTrue(resp.getItems().isEmpty(), "user-b 对 user-a 的私有库必须拿不到任何内容");
        assertTrue(resp.getSkippedKbUuids().contains(KB_READABLE));
    }

    @DisplayName("公开库对所有 ext key 主体可读")
    @Test
    void publicKbIsReadableByAnyUser() {
        User userB = user("user-b");
        when(knowledgeBaseService.getOrThrow(KB_READABLE)).thenReturn(kb(KB_READABLE, "user-a", true));
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();

        ExtKbRetrieveResp resp = service.retrieve(userB, List.of(KB_READABLE), "what is adi", 5);

        assertEquals(1, resp.getItems().size());
        assertTrue(resp.getSkippedKbUuids().isEmpty());
    }

    @DisplayName("重复 uuid 去重，只检索一次")
    @Test
    void duplicateKbUuidsAreDeduplicated() {
        User userA = user("user-a");
        embedReturnsOneHit();
        segmentResolverReturnsTextHit();

        service.retrieve(userA, List.of(KB_READABLE, KB_READABLE), "what is adi", 5);

        verify(embeddingStore, times(1)).search(any(EmbeddingSearchRequest.class));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** 内核路径的必要输入：查询向量 */
    private void embedReturnsOneHit() {
        lenient().when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        Metadata metadata = Metadata.from(Map.of(
                AdiConstant.MetadataKey.KB_UUID, KB_READABLE,
                AdiConstant.MetadataKey.SEGMENT_ID, "11",
                "secret", "must-not-leak"));
        lenient().when(embeddingStore.search(any(EmbeddingSearchRequest.class)))
                .thenReturn(new EmbeddingSearchResult<>(List.of(
                        new EmbeddingMatch<>(0.87, "emb-1", Embedding.from(new float[]{0.1f, 0.2f}),
                                TextSegment.from("hello adi content", metadata)))));
    }

    /** 内核命中 → 关系行：emb-1 是 adi_document_segment 主表行（id=11，doc-1） */
    private void segmentResolverReturnsTextHit() {
        DocumentSegment segment = new DocumentSegment();
        segment.setId(11L);
        segment.setContent("hello adi content");
        segment.setDocUuid("doc-1");
        lenient().when(documentSegmentService.mapByEmbeddingIds(any()))
                .thenReturn(Map.of("emb-1", segment));
        lenient().when(documentSegmentService.mapByIds(anySet())).thenReturn(Map.of());
    }

    private static KnowledgeBase kb(String uuid, String ownerUuid, boolean isPublic) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(uuid);
        kb.setOwnerUuid(ownerUuid);
        kb.setIsPublic(isPublic);
        kb.setTitle("kb-" + uuid);
        return kb;
    }

    private static User user(String uuid) {
        User user = new User();
        user.setUuid(uuid);
        user.setIsAdmin(false);
        return user;
    }
}
