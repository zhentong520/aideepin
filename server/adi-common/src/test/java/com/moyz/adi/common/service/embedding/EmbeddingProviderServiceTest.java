package com.moyz.adi.common.service.embedding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.util.LocalCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-05：embedding provider 选择逻辑单测（纯函数口径，不起 Spring）。
 * <p>
 * 覆盖任务书要求：provider 选择逻辑（本地/远端/bge-m3/显式失败）。
 */
class EmbeddingProviderServiceTest {

    private EmbeddingProviderService embeddingProviderService;
    private AdiProperties adiProperties;

    @BeforeEach
    void setUp() {
        embeddingProviderService = new EmbeddingProviderService();
        adiProperties = new AdiProperties();
        ReflectionTestUtils.setField(embeddingProviderService, "adiProperties", adiProperties);
        LocalCache.MODEL_ID_TO_OBJ.clear();
    }

    @AfterEach
    void tearDown() {
        LocalCache.MODEL_ID_TO_OBJ.clear();
    }

    @Test
    void localAllMinilmResolvesWith384() {
        EmbeddingProviderInfo info = embeddingProviderService.resolve(AdiConstant.EmbeddingModel.ALL_MINILM_L6);

        assertEquals("all-minilm-l6-v2", info.name());
        assertEquals("all-minilm-l6-v2@384", info.version());
        assertEquals(384, info.dimension());
        assertTrue(info.local());
        assertFalse(info.isUnknown());
    }

    @Test
    void localBgeSmallZhResolvesWith512() {
        EmbeddingProviderInfo info = embeddingProviderService.resolve(AdiConstant.EmbeddingModel.BGE_SMALL_ZH_V15);

        assertEquals("bge-small-zh-v1.5", info.name());
        assertEquals("bge-small-zh-v1.5@512", info.version());
        assertEquals(AdiConstant.EmbeddingModel.BGE_SMALL_ZH_V15_DIMENSION, info.dimension());
        assertTrue(info.local());
    }

    @Test
    void blankConfigurationFailsLoudly() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> embeddingProviderService.resolve("  "));
        assertTrue(exception.getMessage().contains("adi.embedding-model"));
    }

    @Test
    void malformedRemoteConfigurationFailsLoudly() {
        // 缺冒号 / 冒号在末尾 / 冒号在开头，三类格式错误都必须显式失败，绝不猜默认值
        assertThrows(IllegalArgumentException.class, () -> embeddingProviderService.resolve("bge-m3"));
        assertThrows(IllegalArgumentException.class, () -> embeddingProviderService.resolve("xinference:"));
        assertThrows(IllegalArgumentException.class, () -> embeddingProviderService.resolve(":bge-m3"));
    }

    @Test
    void remoteBgeM3ResolvesThroughOpenAiCompatibleProtocol() {
        registerRemoteModel("xinference", "bge-m3", 1024);

        EmbeddingProviderInfo info = embeddingProviderService.resolve("xinference:bge-m3");

        assertEquals("bge-m3", info.name());
        assertEquals("xinference/bge-m3@1024", info.version());
        assertEquals(1024, info.dimension());
        assertFalse(info.local());
        assertEquals("xinference", info.platform());
        assertEquals("bge-m3", info.modelName());
        assertTrue(EmbeddingProviderService.isBgeM3("BAAI/bge-m3"));
        assertFalse(EmbeddingProviderService.isBgeM3("bge-small-zh-v1.5"));
    }

    @Test
    void remoteModelWithoutDimensionFailsLoudly() {
        // 远端模型缺 dimension 与 AdiPropertiesUtil 同一口径：显式抛错，绝不猜默认值让向量静默写错表
        registerRemoteModel("xinference", "text-embedding-3-small", null);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> embeddingProviderService.resolve("xinference:text-embedding-3-small"));
        assertTrue(exception.getMessage().contains("dimension"));
    }

    @Test
    void remoteModelNotFoundFailsLoudly() {
        registerRemoteModel("xinference", "bge-m3", 1024);

        assertThrows(IllegalArgumentException.class,
                () -> embeddingProviderService.resolve("openai:bge-m3"));
    }

    @Test
    void bgeM3WithUnexpectedDimensionStillResolvesButWarns() {
        // MRL 截断（768/512）是运维的有意选择，放行但留痕；1536 之类基本是复制错了配置，同样不阻断
        registerRemoteModel("xinference", "bge-m3", 1536);

        EmbeddingProviderInfo info = embeddingProviderService.resolve("xinference:bge-m3");

        assertEquals(1536, info.dimension());
        assertEquals("xinference/bge-m3@1536", info.version());
    }

    @Test
    void currentProviderFollowsConfiguredModel() {
        registerRemoteModel("xinference", "bge-m3", 1024);
        adiProperties.setEmbeddingModel("xinference:bge-m3");

        EmbeddingProviderInfo info = embeddingProviderService.currentProvider();

        assertEquals("xinference/bge-m3@1024", info.version());
    }

    @Test
    void unknownSentinelIsSkippedByConsumers() {
        EmbeddingProviderInfo unknown = EmbeddingProviderInfo.unknown();
        assertTrue(unknown.isUnknown());
        assertEquals("", unknown.version());
        assertEquals(0, unknown.dimension());
    }

    @Test
    void currentProviderWithoutConfigurationThrows() {
        adiProperties.setEmbeddingModel(null);
        assertThrows(IllegalArgumentException.class, () -> embeddingProviderService.currentProvider());
    }

    @Test
    void negativeRemoteDimensionIsRejected() {
        registerRemoteModel("xinference", "bge-m3", -1);
        assertThrows(IllegalArgumentException.class,
                () -> embeddingProviderService.resolve("xinference:bge-m3"));
    }

    @Test
    void remoteResolutionIsRepeatable() {
        registerRemoteModel("xinference", "bge-m3", 1024);
        EmbeddingProviderInfo first = embeddingProviderService.resolve("xinference:bge-m3");
        EmbeddingProviderInfo second = embeddingProviderService.resolve("xinference:bge-m3");
        // 同一配置必须解析出同一版本串（版本串是回滚判据与审计关键字，不容许抖动）
        assertEquals(first.version(), second.version());
    }

    @Test
    void emptyRemoteModelCacheFailsLoudly() {
        assertDoesNotThrow(() -> LocalCache.MODEL_ID_TO_OBJ.clear());
        assertThrows(IllegalArgumentException.class,
                () -> embeddingProviderService.resolve("xinference:bge-m3"));
    }

    private void registerRemoteModel(String platform, String name, Integer dimension) {
        AiModel aiModel = new AiModel();
        aiModel.setId(System.nanoTime());
        aiModel.setPlatform(platform);
        aiModel.setName(name);
        if (null != dimension) {
            aiModel.setProperties(new ObjectMapper().createObjectNode().put("dimension", dimension));
        }
        Map<Long, AiModel> cache = LocalCache.MODEL_ID_TO_OBJ;
        cache.put(aiModel.getId(), aiModel);
    }
}
