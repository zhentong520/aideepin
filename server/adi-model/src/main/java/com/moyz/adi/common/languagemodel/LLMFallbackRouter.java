package com.moyz.adi.common.languagemodel;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.helper.LLMContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * LLM 降级路由器（T6-09）：按 AdiProperties 配置的降级链（adi.llm.fallback-models，
 * 顺序即优先级）解析可用的备用模型服务。
 * <p>
 * 规则：
 * - 开关 adi.llm.fallback-enabled（默认 true）关闭时返回 null（热回滚位）；
 * - 跳过与主模型同名的条目；跳过未注册 / 未启用的备用模型；
 * - 跳过调用方已尝试过的模型（triedModelNames，含主模型与已失败的备用模型）；
 * - PERMANENT 错误的调用方根本不会走到这里（分类后直接报错）。
 * <p>
 * LLM fallback router (T6-09): picks the next usable fallback model from the configured
 * chain, skipping the primary model itself, already-tried models and disabled services.
 */
@Slf4j
public final class LLMFallbackRouter {

    /** 启动期推送（ModelStaticConfigInitializer）；null 时按默认值兜底，与旧 catch 行为一致 */
    private static volatile AdiProperties configProps;

    private LLMFallbackRouter() {
    }

    public static void initConfig(AdiProperties props) {
        configProps = props;
    }

    public static boolean fallbackEnabled() {
        AdiProperties props = configProps;
        if (null == props) {
            // 无 Spring 上下文（单测/启动早期）：与默认值 true 一致，但链为空则无备用可选
            return true;
        }
        return props.getLlm().isFallbackEnabled();
    }

    /**
     * 从降级链中选出下一个可用的备用模型：按配置顺序遍历，返回第一个
     * 「非主模型、未尝试过、已注册且启用」的服务；找不到返回 null（链耗尽）。
     *
     * @param primary          主模型服务
     * @param triedModelNames  本次请求已经尝试过（失败）的模型名集合
     */
    public static AbstractLLMService nextFallback(AbstractLLMService primary, Set<String> triedModelNames) {
        if (null == primary || !fallbackEnabled()) {
            return null;
        }
        List<String> chain = effectiveChain(primary);
        for (String modelName : chain) {
            if (triedModelNames != null && triedModelNames.stream().anyMatch(t -> t.equalsIgnoreCase(modelName))) {
                continue;
            }
            AbstractLLMService candidate = LLMContext.getServiceByPlatformAndModel(null, modelName, false);
            if (null != candidate && candidate.isEnabled()) {
                return candidate;
            }
            log.warn("llm-fallback candidate unavailable, model:{}", modelName);
        }
        return null;
    }

    /**
     * 配置顺序的降级链（已剔除空白项与主模型同名项）。供测试与 resolveChain 用。
     */
    public static List<String> effectiveChain(AbstractLLMService primary) {
        List<String> result = new ArrayList<>();
        AdiProperties props = configProps;
        if (null == props) {
            return result;
        }
        List<String> chain = props.getLlm().getFallbackModels();
        if (null == chain) {
            return result;
        }
        String primaryName = null == primary ? "" : primary.getAiModel().getName();
        for (String modelName : chain) {
            if (StringUtils.isBlank(modelName)) {
                continue;
            }
            if (null != primary && modelName.trim().equalsIgnoreCase(primaryName)) {
                continue;
            }
            result.add(modelName.trim());
        }
        return result;
    }
}
