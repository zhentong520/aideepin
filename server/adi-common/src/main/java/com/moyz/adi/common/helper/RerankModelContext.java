package com.moyz.adi.common.helper;

import com.moyz.adi.common.languagemodel.OpenAiCompatibleScoringModel;
import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rerank 模型注册表（二开新增，2026-09-15），与 {@link AsrModelContext} 同构：
 * 由 {@code AiModelInitializer} 在启动/增改模型时填充，支持热加载与按平台清理。
 */
@Slf4j
public class RerankModelContext {

    /**
     * 模型名 → 重排服务
     */
    private static final Map<String, OpenAiCompatibleScoringModel> NAME_TO_SERVICE = new HashMap<>();

    private RerankModelContext() {
    }

    public static void addService(OpenAiCompatibleScoringModel service) {
        NAME_TO_SERVICE.put(service.getAiModel().getName(), service);
    }

    public static void remove(String modelName) {
        if (modelName != null) {
            NAME_TO_SERVICE.remove(modelName);
        }
    }

    public static void clearByPlatform(String platform) {
        if (platform == null) {
            return;
        }
        List<String> toDelete = NAME_TO_SERVICE.values().stream()
                .filter(item -> platform.equalsIgnoreCase(item.getModelPlatform().getName()))
                .map(item -> item.getAiModel().getName())
                .toList();
        for (String key : toDelete) {
            log.info("delete rerank model service,modelName:{}", key);
            NAME_TO_SERVICE.remove(key);
        }
    }

    /**
     * 按模型 id 取重排服务；id 为空或 0 时返回第一个（按模型名排序，保证结果稳定）
     *
     * @param modelId adi_ai_model.id
     * @return 重排服务，未配置任何 rerank 模型时返回 null
     */
    public static OpenAiCompatibleScoringModel getById(Long modelId) {
        if (NAME_TO_SERVICE.isEmpty()) {
            return null;
        }
        if (modelId != null && modelId > 0) {
            return NAME_TO_SERVICE.values().stream()
                    .filter(item -> modelId.equals(item.getAiModel().getId()))
                    .findFirst()
                    .orElse(null);
        }
        return NAME_TO_SERVICE.values().stream()
                .min(Comparator.comparing(item -> String.valueOf(item.getAiModel().getName())))
                .orElse(null);
    }

    /**
     * 全量清理，用于模型变更时重建
     */
    public static void clearAll() {
        NAME_TO_SERVICE.clear();
    }

    public static boolean hasAny() {
        return !NAME_TO_SERVICE.isEmpty();
    }
}
