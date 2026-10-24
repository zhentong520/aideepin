package com.moyz.adi.common.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.util.LocalCache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * Embedding provider 解析（T7-05）：把 {@code adi.embedding-model} 配置解析成
 * 可记录、可比对、可回滚的 {@link EmbeddingProviderInfo}。
 * <p>
 * 设计约束：
 * <ul>
 *   <li><b>单一事实来源</b>：provider 选择仍然只有 {@code adi.embedding-model} 一个配置项
 *       （与 BeanConfig#initEmbeddingModel 同一口径），本服务不引入第二个开关，避免两处漂移；</li>
 *   <li><b>默认保持现状</b>：不配置时走既有本地模型分支，行为与历史版本完全一致；</li>
 *   <li><b>bge-m3 接入点</b>：bge-m3 经 OpenAI 协议兼容端点接入（{@code platform:bge-m3}），
 *       维度默认 1024（MRL 可截断 768/512），配其他维度只告警不阻断——截断维度可以是
 *       运维的有意选择，但必须留痕；</li>
 *   <li><b>显式失败</b>：远端模型缺 dimension 配置直接抛错（与 AdiPropertiesUtil 一致），
 *       绝不猜一个默认值让向量静默写错表。</li>
 * </ul>
 */
@Slf4j
@Service
public class EmbeddingProviderService {

    @Resource
    private AdiProperties adiProperties;

    /**
     * 当前配置生效的 provider。启动期与重建链路共用这一份解析结果。
     */
    public EmbeddingProviderInfo currentProvider() {
        return resolve(adiProperties.getEmbeddingModel());
    }

    /**
     * 解析任意 embedding-model 配置值为 provider 描述（纯函数，便于单测）。
     *
     * @param embeddingModel {@code local:all-minilm-l6-v2} / {@code local:bge-small-zh-v1.5}
     *                       / {@code <platform>:<model>}（如 {@code xinference:bge-m3}）
     * @throws IllegalArgumentException 配置为空、格式错误、远端模型缺失或未配维度时
     */
    public EmbeddingProviderInfo resolve(String embeddingModel) {
        if (StringUtils.isBlank(embeddingModel)) {
            throw new IllegalArgumentException("adi.embedding-model 未配置，无法确定 embedding provider");
        }
        if (AdiConstant.EmbeddingModel.ALL_MINILM_L6.equals(embeddingModel)) {
            return new EmbeddingProviderInfo("all-minilm-l6-v2", "all-minilm-l6-v2@384",
                    384, true, "", "");
        }
        if (AdiConstant.EmbeddingModel.BGE_SMALL_ZH_V15.equals(embeddingModel)) {
            return new EmbeddingProviderInfo("bge-small-zh-v1.5", "bge-small-zh-v1.5@512",
                    AdiConstant.EmbeddingModel.BGE_SMALL_ZH_V15_DIMENSION, true, "", "");
        }
        return resolveRemote(embeddingModel);
    }

    /**
     * 远端（OpenAI 协议兼容）provider：{@code platform:model}，维度取自 AiModel.properties.dimension。
     * 与 {@code AdiPropertiesUtil#getSuffixAndDimension} 同一口径——那里决定物理表名与列维度，
     * 这里决定记录与比对的版本串，两边必须永远一致。
     */
    private EmbeddingProviderInfo resolveRemote(String embeddingModel) {
        int sep = embeddingModel.indexOf(':');
        if (sep <= 0 || sep == embeddingModel.length() - 1) {
            throw new IllegalArgumentException("embedding-model 格式应为 local:xxx 或 platform:model，当前: " + embeddingModel);
        }
        String platform = embeddingModel.substring(0, sep);
        String modelName = embeddingModel.substring(sep + 1);
        AiModel aiModel = LocalCache.MODEL_ID_TO_OBJ.values().stream()
                .filter(item -> platform.equals(item.getPlatform()) && modelName.equals(item.getName()))
                .findFirst()
                .orElse(null);
        if (null == aiModel) {
            throw new IllegalArgumentException("Model not found or disabled, platform:" + platform + ", name:" + modelName);
        }
        JsonNode dimensionNode = aiModel.getProperties() == null ? null : aiModel.getProperties().get("dimension");
        if (null == dimensionNode) {
            throw new IllegalArgumentException("model dimension is not configured, model name:" + modelName);
        }
        int dimension = dimensionNode.asInt();
        if (dimension <= 0) {
            throw new IllegalArgumentException("model dimension must be positive, model name:" + modelName + ", dimension:" + dimension);
        }
        if (isBgeM3(modelName) && !AdiConstant.EmbeddingModel.BGE_M3_ALLOWED_DIMENSIONS.contains(dimension)) {
            // 截断维度（768/512）与全精度（1024）的向量语义不同、不可混用；配成 1536/2048 之类
            // 基本是复制了别的模型的配置，放行但必须留痕
            log.warn("bge-m3 expected dimension {} (MRL truncation allows {}), configured {} for model {} — "
                            + "make sure this is intentional: mixed dimensions cannot share a vector table",
                    AdiConstant.EmbeddingModel.BGE_M3_DIMENSION,
                    AdiConstant.EmbeddingModel.BGE_M3_ALLOWED_DIMENSIONS, dimension, modelName);
        }
        return new EmbeddingProviderInfo(modelName,
                platform + "/" + modelName + "@" + dimension,
                dimension, false, platform, modelName);
    }

    /**
     * bge-m3 识别：兼容 {@code bge-m3} 与 {@code BAAI/bge-m3} 等带命名空间的写法。
     */
    public static boolean isBgeM3(String modelName) {
        return StringUtils.isNotBlank(modelName)
                && modelName.toLowerCase().contains(AdiConstant.EmbeddingModel.BGE_M3);
    }
}
