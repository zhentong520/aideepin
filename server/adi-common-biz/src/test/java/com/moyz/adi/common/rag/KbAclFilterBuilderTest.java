package com.moyz.adi.common.rag;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.moyz.adi.common.constant.AdiConstant.MetadataKey.KB_UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T1-05 验收（mock embeddingStore 层面）：
 * a) 开关关闭时 Filter 与历史行为完全一致（原样透传，结果不剔除）；
 * b) 开关打开时：owner 检索自己的库命中 / PUBLIC 库命中 / 其他用户 PRIVATE 库被过滤
 *    （向量层 Filter 追加 ACL 条件 + 应用层双保险剔除）。
 */
class KbAclFilterBuilderTest {

    private static final String USER_A = "user-a";
    private static final String USER_B = "user-b";

    private final AtomicReference<EmbeddingSearchRequest> captured = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        KbAclFilterBuilder.setEnabledForTest(true);
    }

    @AfterEach
    void tearDown() {
        KbAclFilterBuilder.setEnabledForTest(null);
    }

    private RetrieverContext ctx(String userUuid, boolean admin, Map<String, KbAclInfo> infos) {
        RetrieverContext ctx = RetrieverContext.none();
        ctx.setUserUuid(userUuid);
        ctx.setUserIsAdmin(admin);
        ctx.setKbUuids(List.copyOf(infos.keySet()));
        ctx.setAclInfos(infos);
        return ctx;
    }

    private KbAclInfo kbInfo(String owner, boolean isPublic, String sensitivity) {
        return KbAclInfo.of("kb-1", owner, isPublic, sensitivity, List.of());
    }

    // ==================== 向量层 Filter ====================

    @Test
    void switchOffKeepsFilterUntouched() {
        Filter base = new IsIn(KB_UUID, List.of("kb-1"));
        RetrieverContext ctx = ctx(USER_B, false, Map.of("kb-1", kbInfo(USER_A, false, "PRIVATE")));

        assertSame(base, KbAclFilterBuilder.wrapFilter(base, ctx, false));
        // 双保险关闭时也必须恒放行（与历史行为一致）
        assertTrue(KbAclFilterBuilder.isAllowed(privateMeta(USER_A), ctx, false));
    }

    @Test
    void switchOnAppendsAclConditionToBaseFilter() {
        Filter base = new IsIn(KB_UUID, List.of("kb-1"));
        RetrieverContext ctx = ctx(USER_A, false, Map.of("kb-1", kbInfo(USER_A, false, "PRIVATE")));

        Filter wrapped = KbAclFilterBuilder.wrapFilter(base, ctx, true);
        And and = assertInstanceOf(And.class, wrapped, "应组合为 And(base, acl)");
        assertSame(base, and.left(), "左侧必须是调用方原有 Filter");
        Or acl = assertInstanceOf(Or.class, and.right(), "右侧必须是 ACL 条件");
        IsEqualTo publicLeg = assertInstanceOf(IsEqualTo.class, acl.left());
        assertEquals(AdiConstant.MetadataKey.ACL_SENSITIVITY, publicLeg.key());
        assertEquals(AdiConstant.Sensitivity.PUBLIC, publicLeg.comparisonValue());
        IsEqualTo ownerLeg = assertInstanceOf(IsEqualTo.class, acl.right());
        assertEquals(AdiConstant.MetadataKey.KB_OWNER_UUID, ownerLeg.key());
        assertEquals(USER_A, ownerLeg.comparisonValue());
    }

    @Test
    void adminBypassesAclFilter() {
        Filter base = new IsIn(KB_UUID, List.of("kb-1"));
        RetrieverContext ctx = ctx(USER_B, true, Map.of("kb-1", kbInfo(USER_A, false, "PRIVATE")));
        assertSame(base, KbAclFilterBuilder.wrapFilter(base, ctx, true));
        assertTrue(KbAclFilterBuilder.isAllowed(privateMeta(USER_A), ctx, true));
    }

    // ==================== retriever 级（mock embeddingStore）====================

    @SuppressWarnings("unchecked")
    private AdiEmbeddingStoreContentRetriever newRetriever(EmbeddingStore<TextSegment> store,
                                                           RetrieverContext context,
                                                           Filter baseFilter) {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyString())).thenReturn(Response.from(Embedding.from(new float[]{0.1f, 0.2f})));
        return AdiEmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .filter(baseFilter)
                .breakIfSearchMissed(false)
                .context(context)
                .build();
    }

    @SuppressWarnings("unchecked")
    private EmbeddingStore<TextSegment> storeReturning(EmbeddingMatch<TextSegment>... matches) {
        EmbeddingStore<TextSegment> store = mock(EmbeddingStore.class);
        when(store.search(any(EmbeddingSearchRequest.class))).thenAnswer(inv -> {
            captured.set(inv.getArgument(0));
            return new EmbeddingSearchResult<>(List.of(matches));
        });
        return store;
    }

    private EmbeddingMatch<TextSegment> match(Metadata metadata) {
        return new EmbeddingMatch<>(0.9, "emb-1", Embedding.from(new float[]{0.1f, 0.2f}),
                TextSegment.from("secret content", metadata));
    }

    private Metadata privateMeta(String owner) {
        return Metadata.from(Map.of(
                KB_UUID, "kb-1",
                AdiConstant.MetadataKey.ACL_SENSITIVITY, AdiConstant.Sensitivity.PRIVATE,
                AdiConstant.MetadataKey.KB_OWNER_UUID, owner));
    }

    private Metadata publicMeta(String owner) {
        return Metadata.from(Map.of(
                KB_UUID, "kb-1",
                AdiConstant.MetadataKey.ACL_SENSITIVITY, AdiConstant.Sensitivity.PUBLIC,
                AdiConstant.MetadataKey.KB_OWNER_UUID, owner));
    }

    @Test
    void ownerRetrievesOwnPrivateKbHit() {
        EmbeddingMatch<TextSegment> hit = match(privateMeta(USER_A));
        Filter base = new IsIn(KB_UUID, List.of("kb-1"));
        AdiEmbeddingStoreContentRetriever retriever =
                newRetriever(storeReturning(hit), ctx(USER_A, false, Map.of("kb-1", kbInfo(USER_A, false, "PRIVATE"))), base);

        List<dev.langchain4j.rag.content.Content> result = retriever.retrieve(Query.from("q"));

        assertEquals(1, result.size(), "owner 检索自己的 PRIVATE 库必须命中（双保险放行 owner）");
        And wrapped = assertInstanceOf(And.class, captured.get().filter());
        assertSame(base, wrapped.left());
    }

    @Test
    void publicKbHitForAnyUser() {
        EmbeddingMatch<TextSegment> hit = match(publicMeta(USER_A));
        AdiEmbeddingStoreContentRetriever retriever =
                newRetriever(storeReturning(hit), ctx(USER_B, false, Map.of("kb-1", kbInfo(USER_A, true, "PUBLIC"))), null);

        List<dev.langchain4j.rag.content.Content> result = retriever.retrieve(Query.from("q"));

        assertEquals(1, result.size(), "PUBLIC 库对非 owner 用户也要命中");
        Or acl = assertInstanceOf(Or.class, captured.get().filter(), "无 base Filter 时 ACL 条件即最终 Filter");
    }

    @Test
    void otherUsersPrivateKbFilteredByDoubleCheck() {
        EmbeddingMatch<TextSegment> hit = match(privateMeta(USER_A));
        AdiEmbeddingStoreContentRetriever retriever =
                newRetriever(storeReturning(hit), ctx(USER_B, false, Map.of("kb-1", kbInfo(USER_A, false, "PRIVATE"))), null);

        List<dev.langchain4j.rag.content.Content> result = retriever.retrieve(Query.from("q"));

        // mock store 不会真的执行 Filter，这条命中依赖双保险在应用层剔除
        assertTrue(result.isEmpty(), "其他用户的 PRIVATE chunk 必须被双保险剔除");
        Or acl = assertInstanceOf(Or.class, captured.get().filter());
        // 向量层条件也已带上 ACL（真实 pgvector 会把 PRIVATE chunk 挡在库层）
        assertEquals(AdiConstant.MetadataKey.ACL_SENSITIVITY, assertInstanceOf(IsEqualTo.class, acl.left()).key());
    }

    @Test
    void switchOffRetrieverBehaviourUnchanged() {
        EmbeddingMatch<TextSegment> hit = match(privateMeta(USER_A));
        AdiEmbeddingStoreContentRetriever retriever =
                newRetriever(storeReturning(hit), ctx(USER_B, false, Map.of("kb-1", kbInfo(USER_A, false, "PRIVATE"))), null);
        KbAclFilterBuilder.setEnabledForTest(false);

        List<dev.langchain4j.rag.content.Content> result = retriever.retrieve(Query.from("q"));

        assertEquals(1, result.size(), "开关关闭时行为与历史完全一致（不追加 Filter、不剔除命中）");
        assertNullFilter(captured.get());
    }

    @SuppressWarnings("unchecked")
    private void assertNullFilter(EmbeddingSearchRequest request) {
        org.junit.jupiter.api.Assertions.assertNull(request.filter());
    }
}
