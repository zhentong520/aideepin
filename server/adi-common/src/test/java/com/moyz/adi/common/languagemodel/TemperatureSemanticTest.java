package com.moyz.adi.common.languagemodel;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.util.LocalCache;
import com.moyz.adi.common.vo.ChatModelBuilderProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-11 temperature 语义修复单测：断言「设置的 temperature」确实进入
 * OpenAI 请求参数（defaultRequestParameters().temperature() 即请求体 temperature 字段来源）。
 * <p>
 * 修复点：AbstractLLMService.buildChatLLM 原判断 `<= 0` 会把 temperature=0 静默覆盖为 0.7，
 * 导致同步链路（chat()）与流式链路（getTemperatureWithDefault 允许 0）行为不一致——
 * 配置 0 实际采样 0.7。修复后两条链路对 0 语义一致。
 */
class TemperatureSemanticTest {

    @BeforeAll
    static void setUp() {
        // AbstractLLMService 构造需要 TTS 配置存在（缺失会抛 B_TTS_SETTING_NOT_FOUND）
        LocalCache.CONFIGS.put(AdiConstant.SysConfigKey.TTS_SETTING, "{}");
    }

    private OpenAiLLMService newService() {
        AiModel aiModel = new AiModel();
        aiModel.setName("gpt-4o");
        aiModel.setPlatform(AdiConstant.ModelPlatform.OPENAI);
        aiModel.setIsEnable(true);
        ModelPlatform platform = new ModelPlatform();
        platform.setName(AdiConstant.ModelPlatform.OPENAI);
        platform.setApiKey("sk-test");
        return new OpenAiLLMService(aiModel, platform);
    }

    private double buildAndCaptureTemperature(Double temperature) {
        ChatModel model = newService().buildChatLLM(
                ChatModelBuilderProperties.builder().temperature(temperature).build());
        Double t = ((OpenAiChatModel) model).defaultRequestParameters().temperature();
        assertNotNull(t, "temperature 必须写入请求参数，不能为 null");
        return t;
    }

    @Test
    void temperatureZeroMustBePreserved() {
        // ⚠️ 修复目标：配置 0（确定性输出）不能再被静默覆盖为 0.7
        assertEquals(0.0, buildAndCaptureTemperature(0.0));
    }

    @Test
    void configuredTemperatureGoesToRequestBody() {
        assertEquals(0.35, buildAndCaptureTemperature(0.35), 1e-9);
        assertEquals(1.0, buildAndCaptureTemperature(1.0), 1e-9);
    }

    @Test
    void nullFallsBackToDefault() {
        assertEquals(0.7, buildAndCaptureTemperature(null), 1e-9);
    }

    @Test
    void outOfRangeFallsBackToDefault() {
        assertEquals(0.7, buildAndCaptureTemperature(-0.5), 1e-9);
        assertEquals(0.7, buildAndCaptureTemperature(1.5), 1e-9);
    }

    @Test
    void streamingPathAlsoPreservesZero() {
        // 流式链路（getTemperatureWithDefault）本来就允许 0——与同步链路对齐后两侧一致
        Double t = newService().buildStreamingChatModel(
                        ChatModelBuilderProperties.builder().temperature(0.0).build())
                .defaultRequestParameters().temperature();
        assertNotNull(t);
        assertEquals(0.0, t, 1e-9);
    }
}
