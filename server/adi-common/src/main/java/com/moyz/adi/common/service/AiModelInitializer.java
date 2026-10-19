package com.moyz.adi.common.service;

import com.google.common.base.Joiner;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.AsrModelContext;
import com.moyz.adi.common.helper.ImageModelContext;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.helper.RerankModelContext;
import com.moyz.adi.common.helper.TtsModelContext;
import com.moyz.adi.common.languagemodel.*;
import com.moyz.adi.common.searchengine.GoogleSearchEngineService;
import com.moyz.adi.common.searchengine.SearchEngineServiceContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.moyz.adi.common.cosntant.AdiConstant.ModelType.*;
import static com.moyz.adi.common.util.LocalCache.MODEL_ID_TO_OBJ;

@Slf4j
@Service
public class AiModelInitializer {

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private ModelPlatformService modelPlatformService;

    private InetSocketAddress proxyAddress;

    private List<AiModel> all = new ArrayList<>();

    /**
     * 模型及其配置初始化
     *
     * @param allModels
     */
    public void init(List<AiModel> allModels) {
        this.all = allModels;
        for (AiModel model : all) {
            if (Boolean.TRUE.equals(model.getIsEnable())) {
                MODEL_ID_TO_OBJ.put(model.getId(), model);
            } else {
                MODEL_ID_TO_OBJ.remove(model.getId());
            }
        }
        if (adiProperties.getProxy().isEnable()) {
            proxyAddress = new InetSocketAddress(adiProperties.getProxy().getHost(), adiProperties.getProxy().getHttpPort());
        } else {
            proxyAddress = null;
        }

        Map<String, ModelPlatform> nameToPlatform = modelPlatformService.listAll().stream().collect(Collectors.toMap(ModelPlatform::getName, Function.identity(), (v1, v2) -> v1));
        initLLMServiceList(nameToPlatform, TEXT);
        initLLMServiceList(nameToPlatform, VISION);
        initImageModelServiceList(nameToPlatform);
        initAsrModelServiceList(nameToPlatform);
        initTtsModelServiceList(nameToPlatform);
        initRerankModelServiceList(nameToPlatform);
    }

    /**
     * 初始化大语言模型列表
     *
     * @param nameToPlatform 模型平台名称与模型平台详情映射
     * @param modelType      模型类型：text | vision
     */
    private synchronized void initLLMServiceList(Map<String, ModelPlatform> nameToPlatform, String modelType) {

// OpenAI API compatible model
        // OpenAi api 兼容模型
        initOpenAiCompatibleService(nameToPlatform, (model, modelPlatformName) -> new OpenAiCompatibleLLMService(model, nameToPlatform.get(modelPlatformName)).setProxyAddress(proxyAddress));

        //deepseek
        initLLMService(AdiConstant.ModelPlatform.DEEPSEEK, modelType, model -> new DeepSeekLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DEEPSEEK)).setProxyAddress(proxyAddress));

        //openai
        initLLMService(AdiConstant.ModelPlatform.OPENAI, modelType, model -> new OpenAiLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OPENAI)).setProxyAddress(proxyAddress));

        //dashscope
        initLLMService(AdiConstant.ModelPlatform.DASHSCOPE, modelType, model -> new DashScopeLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)).setProxyAddress(proxyAddress));

        //ollama
        initLLMService(AdiConstant.ModelPlatform.OLLAMA, modelType, model -> new OllamaLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OLLAMA)));

        // 硅基流动
        initLLMService(AdiConstant.ModelPlatform.SILICONFLOW, modelType, model -> new SiliconflowLLMService(model, nameToPlatform.get(AdiConstant.ModelPlatform.SILICONFLOW)));
    }

    /**
     * 初始化图片服务、搜索服务
     *
     * @param nameToPlatform 模型平台名称与模型平台详情映射
     */
    private synchronized void initImageModelServiceList(Map<String, ModelPlatform> nameToPlatform) {

        initImageModelService(AdiConstant.ModelPlatform.OPENAI, model -> new OpenAiImageService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OPENAI)).setProxyAddress(proxyAddress));
        initImageModelService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeWanxService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)));
        initImageModelService(AdiConstant.ModelPlatform.SILICONFLOW, model -> new SiliconflowImageModelService(model, nameToPlatform.get(AdiConstant.ModelPlatform.SILICONFLOW)));

        //search engine
        SearchEngineServiceContext.addWebSearcher(AdiConstant.SearchEngineName.GOOGLE, new GoogleSearchEngineService(proxyAddress));
    }

    /**
     * 初始化语音识别服务
     *
     * @param nameToPlatform 模型平台名称与模型平台详情映射
     */
    private synchronized void initAsrModelServiceList(Map<String, ModelPlatform> nameToPlatform) {
        initAsrModelService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeAsrService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)));
        initAsrModelService(AdiConstant.ModelPlatform.SILICONFLOW, model -> new SiliconflowAsrService(model, nameToPlatform.get(AdiConstant.ModelPlatform.SILICONFLOW)));
        initAsrModelService(AdiConstant.ModelPlatform.OPENAI, model -> new OpenAiAsrService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OPENAI)).setProxyAddress(proxyAddress));
    }

    /**
     * 初始化语音合成服务
     *
     * @param nameToPlatform 模型平台名称与模型平台详情映射
     */
    private synchronized void initTtsModelServiceList(Map<String, ModelPlatform> nameToPlatform) {
        initTtsModelService(AdiConstant.ModelPlatform.DASHSCOPE, model -> new DashScopeTtsService(model, nameToPlatform.get(AdiConstant.ModelPlatform.DASHSCOPE)));
        initTtsModelService(AdiConstant.ModelPlatform.SILICONFLOW, model -> new SiliconflowTtsService(model, nameToPlatform.get(AdiConstant.ModelPlatform.SILICONFLOW)));
        initTtsModelService(AdiConstant.ModelPlatform.OPENAI, model -> new OpenAiTtsService(model, nameToPlatform.get(AdiConstant.ModelPlatform.OPENAI)).setProxyAddress(proxyAddress));
    }

    /**
     * 初始化重排模型服务（二开新增，2026-09-15）
     * <p>
     * 与 LLM/ASR 不同，rerank 采用「通用 OpenAI 兼容 /rerank 端点」一种实现对接所有平台：
     * 硅基流动、阿里 DashScope、自建 Xinference / vLLM / Infinity 均遵循同一协议，
     * 区别只在平台上的 baseUrl 与 apiKey，无需为每家写一套实现。
     */
    private synchronized void initRerankModelServiceList(Map<String, ModelPlatform> nameToPlatform) {
        RerankModelContext.clearAll();
        List<AiModel> models = all.stream()
                .filter(item -> RERANK.equalsIgnoreCase(item.getType()))
                .filter(item -> Boolean.TRUE.equals(item.getIsEnable()))
                .toList();
        if (CollectionUtils.isEmpty(models)) {
            log.info("no enabled rerank model configured,hybrid rerank stage will be skipped");
            return;
        }
        for (AiModel model : models) {
            ModelPlatform platform = nameToPlatform.get(model.getPlatform());
            if (platform == null) {
                log.warn("rerank model {} skipped,platform {} not found", model.getName(), model.getPlatform());
                continue;
            }
            log.info("add rerank model,name:{},platform:{}", model.getName(), model.getPlatform());
            RerankModelContext.addService(new OpenAiCompatibleScoringModel(model, platform));
        }
    }

    private void initLLMService(String platform, String modelType, Function<AiModel, AbstractLLMService> function) {
        // 只注册 isEnable=true 的模型，与 init() 里 MODEL_ID_TO_OBJ 的口径保持一致：
        // 禁用的模型不应进入 LLMContext，否则会出现在 /model/llms 里，
        // 用户端「聊天下拉框」就会列出根本无法使用的模型。
        // Register only enabled models, matching MODEL_ID_TO_OBJ in init(): a disabled model
        // must not enter LLMContext, otherwise it shows up in /model/llms and pollutes the
        // user-facing chat model selector with models that cannot be used at all.
        List<AiModel> models = all.stream()
                .filter(item -> item.getType().equals(modelType) && item.getPlatform().equals(platform))
                .filter(item -> Boolean.TRUE.equals(item.getIsEnable()))
                .toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        LLMContext.clearByPlatform(platform, modelType);
        for (AiModel model : models) {
            log.info("add llm model,model:{}", model);
            LLMContext.addLLMService(function.apply(model));
        }
    }

    private void initOpenAiCompatibleService(Map<String, ModelPlatform> nameToPlatform, BiFunction<AiModel, String, AbstractLLMService> function) {
        log.info("init openai api compatible llm model");
        List<String> compatiblePlatforms = nameToPlatform.values().stream().filter(ModelPlatform::getIsOpenaiApiCompatible).map(ModelPlatform::getName).toList();
        for (String platform : compatiblePlatforms) {
            List<AiModel> models = all.stream()
                    .filter(item -> item.getType().equals(TEXT) && item.getPlatform().equals(platform))
                    .filter(item -> Boolean.TRUE.equals(item.getIsEnable()))
                    .toList();
            if (CollectionUtils.isEmpty(models)) {
                log.warn("{} service is disabled", Joiner.on(",").join(compatiblePlatforms));
            }
            // 必须与 initLLMService 一样先清空本平台缓存：本方法被 initLLMServiceList 以
            // TEXT、VISION 各调用一次，缺少 clear 会把同一批 TEXT 模型注册两遍，
            // /model/llms 里就会出现重复条目（同一个 modelId 出现两次）。
            // Must clear this platform's cache first, exactly like initLLMService: this method is
            // invoked once per initLLMServiceList(TEXT|VISION) call, so without the clear the same
            // TEXT models get registered twice and /model/llms returns duplicate entries.
            LLMContext.clearByPlatform(platform, TEXT);
            for (AiModel model : models) {
                log.info("add openai api compatible llm model,model:{}", model);
                LLMContext.addLLMService(function.apply(model, platform));
            }
        }
    }

    private void initImageModelService(String platform, Function<AiModel, AbstractImageModelService> function) {
        List<AiModel> models = all.stream()
                .filter(item -> item.getType().equals(IMAGE) && item.getPlatform().equals(platform))
                .filter(item -> Boolean.TRUE.equals(item.getIsEnable()))
                .toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        ImageModelContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add image model,model:{}", model);
            ImageModelContext.addImageModelService(function.apply(model));
        }

    }

    private void initAsrModelService(String platform, Function<AiModel, AbstractAsrModelService> function) {
        List<AiModel> models = all.stream()
                .filter(item -> item.getType().equals(AdiConstant.ModelType.ASR) && item.getPlatform().equals(platform))
                .filter(item -> Boolean.TRUE.equals(item.getIsEnable()))
                .toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        AsrModelContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add asr model,model:{}", model);
            AsrModelContext.addService(function.apply(model));
        }
    }

    private void initTtsModelService(String platform, Function<AiModel, AbstractTtsModelService> function) {
        List<AiModel> models = all.stream()
                .filter(item -> item.getType().equals(AdiConstant.ModelType.TTS) && item.getPlatform().equals(platform))
                .filter(item -> Boolean.TRUE.equals(item.getIsEnable()))
                .toList();
        if (CollectionUtils.isEmpty(models)) {
            log.warn("{} service is disabled", platform);
        }
        TtsModelContext.clearByPlatform(platform);
        for (AiModel model : models) {
            log.info("add tts model,model:{}", model);
            TtsModelContext.addService(function.apply(model));
        }
    }


    public void delete(AiModel aiModel) {
        LLMContext.remove(aiModel.getPlatform(), aiModel.getName());
        ImageModelContext.remove(aiModel.getName());
        if (RERANK.equalsIgnoreCase(aiModel.getType())) {
            RerankModelContext.remove(aiModel.getName());
        }
        MODEL_ID_TO_OBJ.remove(aiModel.getId());
    }

    public void addOrUpdate(AiModel aiModel) {
        AiModel existOne = all.stream().filter(item -> item.getId().equals(aiModel.getId())).findFirst().orElse(null);
        if (null == existOne) {
            all.add(aiModel);
        } else {
            BeanUtils.copyProperties(aiModel, existOne);
        }
        Map<String, ModelPlatform> nameToPlatform = modelPlatformService.listAll().stream().collect(Collectors.toMap(ModelPlatform::getName, Function.identity(), (v1, v2) -> v1));
        String modelType = aiModel.getType();
        if (TEXT.equalsIgnoreCase(modelType) || VISION.equalsIgnoreCase(modelType)) {
            initLLMServiceList(nameToPlatform, aiModel.getType());
        } else if (IMAGE.equalsIgnoreCase(modelType)) {
            initImageModelServiceList(nameToPlatform);
        } else if (ASR.equalsIgnoreCase(modelType)) {
            initAsrModelServiceList(nameToPlatform);
        } else if (TTS.equalsIgnoreCase(modelType)) {
            initTtsModelServiceList(nameToPlatform);
        } else if (RERANK.equalsIgnoreCase(modelType)) {
            initRerankModelServiceList(nameToPlatform);
        } else {
            throw new BaseException(ErrorEnum.A_MODEL_NOT_FOUND);
        }
        // 与 init() 口径一致：禁用模型不进 MODEL_ID_TO_OBJ（否则「禁用」只是列表上的装饰，
        // 按 id 取模型时仍会把已禁用的模型取出来）。
        // Same rule as init(): a disabled model must not stay in MODEL_ID_TO_OBJ, otherwise
        // "disabled" only decorates the admin list while id-based lookups still return it.
        if (Boolean.TRUE.equals(aiModel.getIsEnable())) {
            MODEL_ID_TO_OBJ.put(aiModel.getId(), aiModel);
        } else {
            MODEL_ID_TO_OBJ.remove(aiModel.getId());
        }
    }
}
