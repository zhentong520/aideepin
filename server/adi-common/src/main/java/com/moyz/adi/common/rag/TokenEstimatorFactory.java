package com.moyz.adi.common.rag;

import com.moyz.adi.common.constant.AdiConstant;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.onnx.HuggingFaceTokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Token 估算器工厂（OPT-15 批 3 下沉 core）。
 * <p>
 * 原实现的 QWEN 分支直接引 languagemodel.AbstractLLMService（经 LLMContext 查找
 * dashscope 平台的 tokenizer），把本类锁死在 model 域、造成 core→biz 反向依赖。
 * 改为<b>可插拔 resolver</b>：model 域启动时调用 {@link #setQwenResolver} 注册
 * 「按当前模型清单解析 QWEN tokenizer」的回调；未注册或解析为空时回退默认
 * OpenAiTokenizer（与原"找不到 qwen 模型"分支一致）。core 对 model 的依赖方向
 * 由此保持为零。
 */
@Slf4j
public class TokenEstimatorFactory {

    private static volatile Supplier<Optional<TokenCountEstimator>> qwenResolver;

    private TokenEstimatorFactory() {
    }

    /**
     * 由 model 域（AbstractLLMService 静态初始化）注册 QWEN tokenizer 解析器。
     * 重复注册以后者为准；传 null 清除。
     */
    public static void setQwenResolver(Supplier<Optional<TokenCountEstimator>> resolver) {
        qwenResolver = resolver;
    }

    public static TokenCountEstimator create(String tokenEstimator) {
        if (StringUtils.isBlank(tokenEstimator)) {
            return new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);
        }
        if (AdiConstant.TokenEstimator.OPENAI.equals(tokenEstimator)) {
            return new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);
        } else if (AdiConstant.TokenEstimator.HUGGING_FACE.equals(tokenEstimator)) {
            return new HuggingFaceTokenCountEstimator();
        } else if (AdiConstant.TokenEstimator.QWEN.equals(tokenEstimator)) {
            Supplier<Optional<TokenCountEstimator>> resolver = qwenResolver;
            if (null != resolver) {
                Optional<TokenCountEstimator> resolved = resolver.get();
                if (resolved.isPresent()) {
                    return resolved.get();
                }
            }
            log.warn("Qwen model tokenizer not found (no resolver or no dashscope text model), using default OpenAiTokenizer");
            return new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);
        }
        return new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);
    }

}
