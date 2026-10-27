package com.moyz.adi.common.rag;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.vo.RetrieverContext;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import com.moyz.adi.common.workflow.node.knowledgeretrieval.KnowledgeRetrievalNodeConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T3-12（2026-10-19）：知识检索节点 rerank/扩窗节点级覆盖的断言。
 * <p>
 * 锁三件事：
 * <ol>
 *   <li>override 非 null 时，无论 ctx 来自调用方还是库级回读，都必须生效；</li>
 *   <li>override 为 null（前端 ''）时保持库级语义——工作流节点未携带 context，
 *       EmbeddingRag 按 kbUuid 回读库级配置，而不是一律 none()；</li>
 *   <li>nodeConfig 三态字符串（'' / 'true' / 'false'）到 Boolean 的映射，'' 必须落成 null。</li>
 * </ol>
 */
class EmbeddingRagNodeOverrideTest {

    private static final String KB_UUID = "kb-override-1";

    private KnowledgeBaseService knowledgeBaseService;
    private EmbeddingRag embeddingRag;

    /** SpringUtil 的 applicationContext 是静态字段，测试结束必须还原，否则殃及其他测试类 */
    private ApplicationContext previousContext;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        previousContext = (ApplicationContext) ReflectionTestUtils.getField(SpringUtil.class, "applicationContext");
        embeddingRag = new EmbeddingRag(
                AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE,
                mock(EmbeddingModel.class),
                (EmbeddingStore<TextSegment>) mock(EmbeddingStore.class));

        knowledgeBaseService = mock(KnowledgeBaseService.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(KnowledgeBaseService.class)).thenReturn(knowledgeBaseService);
        SegmentExpandProcessor expandProcessor = mock(SegmentExpandProcessor.class);
        when(applicationContext.getBean(SegmentExpandProcessor.class)).thenReturn(expandProcessor);
        new SpringUtil().setApplicationContext(applicationContext);
        // S1-02：EmbeddingRag 改为启动期静态推送（RagStaticConfigInitializer），无容器测试需手动推送
        EmbeddingRag.initStatics(knowledgeBaseService, expandProcessor);

        // 库级配置读取点的最小同构：rerank/扩窗开关按库行落进 ctx。
        // buildRetrieverContext 本身的字段映射已有独立覆盖，这里不重复。
        // 用 any() 而不是 anySet()：工作流节点的停用名单可能为 null，anySet() 匹配不上会静默返回 null
        when(knowledgeBaseService.buildRetrieverContext(any(KnowledgeBase.class), any()))
                .thenAnswer(invocation -> {
                    KnowledgeBase kb = invocation.getArgument(0);
                    return RetrieverContext.builder()
                            .kbUuid(kb.getUuid())
                            .hybridEnabled(Boolean.TRUE.equals(kb.getRetrieveHybridEnabled()))
                            .rerankEnabled(Boolean.TRUE.equals(kb.getRetrieveRerankEnabled()))
                            .neighborEnabled(Boolean.TRUE.equals(kb.getRetrieveNeighborEnabled()))
                            .neighborWindow(RetrieverContext.DEFAULT_NEIGHBOR_WINDOW)
                            .excludedDocUuids(Set.of())
                            .build();
                });
    }

    @AfterEach
    void tearDown() {
        KbAclFilterBuilder.setEnabledForTest(null);
        EmbeddingRag.initStatics(null, null);
        new SpringUtil().setApplicationContext(previousContext);
    }

    // ---------- 覆盖生效 ----------

    @Test
    void nodeOverridesBeatKbLevelConfig() {
        // 库级：rerank 关 / 扩窗开；节点：rerank 开 / 扩窗关 → 节点值赢
        stubKb(false, true);

        AdiEmbeddingStoreContentRetriever retriever = embeddingRag.createRetriever(RetrieverCreateParam.builder()
                .kbUuid(KB_UUID)
                .rerankEnabledOverride(true)
                .expandWindowEnabledOverride(false)
                .build());

        RetrieverContext ctx = contextOf(retriever);
        assertTrue(ctx.isRerankEnabled(), "节点 rerank=true 必须覆盖库级 false");
        assertFalse(ctx.isNeighborEnabled(), "节点 expand_window=false 必须覆盖库级 true");
        verify(knowledgeBaseService, times(1)).getOrThrow(KB_UUID);
    }

    // ---------- 继承库级 ----------

    @Test
    void inheritsKbLevelConfigWhenOverridesAreNull() {
        // 库级：rerank 关 / 扩窗开；节点未配置（null）→ 原样继承
        stubKb(false, true);

        AdiEmbeddingStoreContentRetriever retriever = embeddingRag.createRetriever(RetrieverCreateParam.builder()
                .kbUuid(KB_UUID)
                .build());

        RetrieverContext ctx = contextOf(retriever);
        assertFalse(ctx.isRerankEnabled(), "未覆盖时必须继承库级 rerank 开关");
        assertTrue(ctx.isNeighborEnabled(), "未覆盖时必须继承库级扩窗开关");
        assertEquals(KB_UUID, ctx.getKbUuid());
    }

    @Test
    void overrideStillAppliesToExplicitlyPassedContext() {
        // 显式带 context 的入口（QA/角色聊天 KB 通道） override 也必须生效，且不回读库级
        RetrieverContext explicit = RetrieverContext.none();

        AdiEmbeddingStoreContentRetriever retriever = embeddingRag.createRetriever(RetrieverCreateParam.builder()
                .context(explicit)
                .rerankEnabledOverride(true)
                .build());

        RetrieverContext ctx = contextOf(retriever);
        assertTrue(ctx.isRerankEnabled(), "显式 context 上 override 同样生效");
        assertFalse(ctx.isNeighborEnabled());
        verify(knowledgeBaseService, never()).getOrThrow(anyString());
    }

    // ---------- 降级 ----------

    @Test
    void fallsBackToNoneWhenKbUuidIsBlank() {
        AdiEmbeddingStoreContentRetriever retriever = embeddingRag.createRetriever(RetrieverCreateParam.builder().build());

        RetrieverContext ctx = contextOf(retriever);
        assertFalse(ctx.isRerankEnabled());
        assertFalse(ctx.isNeighborEnabled());
        verify(knowledgeBaseService, never()).getOrThrow(anyString());
    }

    @Test
    void fallsBackToNoneWhenKbLookupFails() {
        when(knowledgeBaseService.getOrThrow(KB_UUID)).thenThrow(new IllegalStateException("db down"));

        AdiEmbeddingStoreContentRetriever retriever = embeddingRag.createRetriever(RetrieverCreateParam.builder()
                .kbUuid(KB_UUID)
                .build());

        RetrieverContext ctx = contextOf(retriever);
        assertFalse(ctx.isRerankEnabled(), "库级读取失败只能降级为 none，不能把检索打挂");
        assertFalse(ctx.isNeighborEnabled());
    }

    // ---------- 三态映射（nodeConfig → config 对象） ----------

    @Test
    void threeStateRerankMapping() {
        KnowledgeRetrievalNodeConfig enabled = config("{\"rerank\":\"true\",\"expand_window\":\"false\"}");
        assertEquals(Boolean.TRUE, enabled.getRerank());
        assertEquals(Boolean.FALSE, enabled.getExpandWindow());
    }

    @Test
    void emptyStringMeansInherit() {
        // '' = 继承库级 → 必须反序列化为 null（而不是 false，否则继承语义会被静默改成强制关）
        KnowledgeRetrievalNodeConfig inherit = config("{\"rerank\":\"\",\"expand_window\":\"\"}");
        assertNull(inherit.getRerank(), "'' 必须映射为 null（继承库级）");
        assertNull(inherit.getExpandWindow());
    }

    @Test
    void missingKeysDefaultToNull() {
        KnowledgeRetrievalNodeConfig missing = config("{\"top_n\":3}");
        assertNull(missing.getRerank());
        assertNull(missing.getExpandWindow());
        assertEquals(3, missing.getTopN());
    }

    // ---------- helpers ----------

    private void stubKb(boolean rerankEnabled, boolean neighborEnabled) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(KB_UUID);
        kb.setRetrieveRerankEnabled(rerankEnabled);
        kb.setRetrieveNeighborEnabled(neighborEnabled);
        when(knowledgeBaseService.getOrThrow(KB_UUID)).thenReturn(kb);
    }

    private static RetrieverContext contextOf(AdiEmbeddingStoreContentRetriever retriever) {
        Object ctx = ReflectionTestUtils.getField(retriever, "context");
        assertNotNull(ctx, "知识库链路的 retriever 必须携带 RetrieverContext");
        return (RetrieverContext) ctx;
    }

    private static KnowledgeRetrievalNodeConfig config(String json) {
        ObjectNode node = JsonUtil.fromJson(json, ObjectNode.class);
        return JsonUtil.fromJson(node, KnowledgeRetrievalNodeConfig.class);
    }
}
