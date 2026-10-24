package com.moyz.adi.common.config.embeddingstore;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.service.embedding.EmbeddingProviderInfo;
import com.moyz.adi.common.service.embedding.EmbeddingProviderService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-05：embedding provider 启动自检单测（不一致检出 / 空值跳过 / 失败不拖住启动）。
 */
class EmbeddingProviderGuardTest {

    private EmbeddingProviderGuard guard;
    private EmbeddingProviderService providerService;
    private KnowledgeBaseService knowledgeBaseService;
    private KnowledgeBaseMapper knowledgeBaseMapper;
    private Logger guardLogger;
    private ListAppender<ILoggingEvent> logAppender;

    private static final EmbeddingProviderInfo CURRENT =
            new EmbeddingProviderInfo("bge-m3", "xinference/bge-m3@1024", 1024, false, "xinference", "bge-m3");

    @BeforeEach
    void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelperInitializer.init(configuration);
        guard = new EmbeddingProviderGuard();
        providerService = mock(EmbeddingProviderService.class);
        knowledgeBaseService = mock(KnowledgeBaseService.class);
        knowledgeBaseMapper = mock(KnowledgeBaseMapper.class);
        when(knowledgeBaseService.lambdaQuery())
                .thenReturn(new LambdaQueryChainWrapper<KnowledgeBase>(knowledgeBaseMapper, KnowledgeBase.class));
        ReflectionTestUtils.setField(guard, "embeddingProviderService", providerService);
        ReflectionTestUtils.setField(guard, "knowledgeBaseService", knowledgeBaseService);
        guardLogger = (Logger) LoggerFactory.getLogger(EmbeddingProviderGuard.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        guardLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        guardLogger.detachAppender(logAppender);
    }

    @Test
    void compareVersionsFlagsMismatchAndSkipsUnknown() {
        EmbeddingProviderGuard.ProviderCheckResult result = EmbeddingProviderGuard.compareVersions(
                List.of(kb("kb-new", "已重建库", CURRENT.version()),
                        kb("kb-old", "旧模型库", "bge-small-zh-v1.5@512"),
                        kb("kb-legacy", "存量库", null)),
                CURRENT);

        assertEquals(1, result.mismatched().size());
        assertTrue(result.mismatched().get(0).contains("旧模型库(kb-old"));
        assertTrue(result.mismatched().get(0).contains("built by bge-small-zh-v1.5@512"));
        // 空版本 = 未知（存量），跳过比对并只记 INFO，绝不判为不一致
        assertEquals(1, result.unknown());
    }

    @Test
    void compareVersionsKeepsQuietWhenAllUnknown() {
        // 升级后第一启动：全部存量库都无版本记录，不能刷成一片告警
        EmbeddingProviderGuard.ProviderCheckResult result = EmbeddingProviderGuard.compareVersions(
                List.of(kb("kb-a", "库A", ""), kb("kb-b", "库B", null)), CURRENT);

        assertTrue(result.mismatched().isEmpty());
        assertEquals(2, result.unknown());
    }

    @Test
    void checkWarnsOnMismatchAndLogsUnknownCount() {
        when(providerService.currentProvider()).thenReturn(CURRENT);
        when(knowledgeBaseMapper.selectList(any())).thenReturn(
                List.of(kb("kb-new", "已重建库", CURRENT.version()),
                        kb("kb-old", "旧模型库", "bge-small-zh-v1.5@512"),
                        kb("kb-legacy", "存量库", null)));

        assertDoesNotThrow(guard::check);

        verify(knowledgeBaseMapper).selectList(any());
        assertTrue(logAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN
                        && event.getFormattedMessage().contains("EMBEDDING PROVIDER MISMATCH")
                        && event.getFormattedMessage().contains("旧模型库(kb-old")));
        assertTrue(logAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.INFO && event.getFormattedMessage().contains("1 KB(s) have no recorded")));
    }

    @Test
    void checkStaysQuietWhenEverythingMatches() {
        when(providerService.currentProvider()).thenReturn(CURRENT);
        when(knowledgeBaseMapper.selectList(any())).thenReturn(List.of(kb("kb-1", "库一", CURRENT.version())));

        assertDoesNotThrow(guard::check);

        assertFalse(logAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN && event.getFormattedMessage().contains("EMBEDDING PROVIDER MISMATCH")));
    }

    @Test
    void checkSkipsWhenActiveProviderUnresolvable() {
        when(providerService.currentProvider()).thenThrow(new IllegalArgumentException("dimension missing"));

        assertDoesNotThrow(guard::check);

        // provider 解析失败时直接退场，不查库（BeanConfig/EmbeddingModelGuard 会给出更贴近现场的报错）
        verify(knowledgeBaseMapper, never()).selectList(any());
        assertTrue(logAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN && event.getFormattedMessage().contains("check skipped")));
    }

    @Test
    void checkToleratesDataAccessFailure() {
        // 自检失败不拖住启动
        when(providerService.currentProvider()).thenReturn(CURRENT);
        when(knowledgeBaseMapper.selectList(any())).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(guard::check);

        assertTrue(logAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.WARN && event.getFormattedMessage().contains("consistency check failed")));
    }

    private static KnowledgeBase kb(String uuid, String title, String providerVersion) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid(uuid);
        kb.setTitle(title);
        kb.setEmbeddingProviderVersion(providerVersion);
        kb.setIsDeleted(false);
        return kb;
    }

    /** 与 RagQualityDashboardTest 同一口径：LambdaQueryChainWrapper 需要 TableInfo 缓存才能解析列名 */
    private static final class TableInfoHelperInitializer {
        static void init(MybatisConfiguration configuration) {
            com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                    new MapperBuilderAssistant(configuration, ""), KnowledgeBase.class);
        }
    }
}
