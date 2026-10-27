package com.moyz.adi.common.languagemodel;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.ModelPlatform;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.helper.TtsModelContext;
import com.moyz.adi.common.interfaces.TriConsumer;
import com.moyz.adi.common.languagemodel.data.InnerStreamChatParam;
import com.moyz.adi.common.languagemodel.data.ToolLoopBudget;
import com.moyz.adi.common.languagemodel.data.LLMException;
import com.moyz.adi.common.languagemodel.data.LLMResponseContent;
import com.moyz.adi.common.memory.shortterm.MapDBChatMemoryStore;
import com.moyz.adi.common.interfaces.DelegateAgentSpi;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import com.moyz.adi.common.multiagent.AgentCallChain;
import com.moyz.adi.common.rag.TokenEstimatorFactory;
import com.moyz.adi.common.rag.TokenEstimatorThreadLocal;
import com.moyz.adi.common.service.ModelHealthService;
import com.moyz.adi.common.util.*;
import com.moyz.adi.common.vo.*;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolServiceContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.moyz.adi.common.constant.ModelConstant.CustomChatRequestParameterKeys.ENABLE_WEB_SEARCH;
import static com.moyz.adi.common.constant.ModelConstant.CustomChatRequestParameterKeys.ENABLE_THINKING;
import static com.moyz.adi.common.constant.ModelConstant.LLM_MAX_INPUT_TOKENS_DEFAULT;
import static com.moyz.adi.common.constant.ModelConstant.RESPONSE_FORMAT_TYPE_JSON_OBJECT;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;
import static com.moyz.adi.common.enums.ErrorEnum.B_LLM_SERVICE_DISABLED;

@Slf4j
public abstract class AbstractLLMService extends CommonModelService {

    static {
        // OPT-15 批 3：TokenEstimatorFactory 下沉 core 后，QWEN tokenizer 的解析能力
        // 由本类（model 域）经可插拔 resolver 回注——core 依赖方向保持为零。
        TokenEstimatorFactory.setQwenResolver(() -> LLMContext.getAllServices()
                .stream()
                .filter(item -> {
                    AiModel aiModel = item.getAiModel();
                    return aiModel.getPlatform().equals(AdiConstant.ModelPlatform.DASHSCOPE)
                            && aiModel.getType().equals(AdiConstant.ModelType.TEXT);
                })
                .findFirst()
                .map(AbstractLLMService::getTokenEstimator));
    }

    protected StringRedisTemplate stringRedisTemplate;

    /**
     * S1-02：streaming 同模型重试次数由启动期推送（ModelStaticConfigInitializer），
     * 替代调用路径里 SpringUtil.getBean(AdiProperties) 的服务定位器写法。
     * 默认 1 与旧代码异常兜底值一致；实例由 AiModelInitializer 手动 new（非 Spring bean），
     * 无法字段注入。
     */
    private static volatile int streamSameModelRetriesDefault = 1;

    public static void applyStreamConfig(int sameModelRetries) {
        streamSameModelRetriesDefault = sameModelRetries;
    }

    /**
     * S1-02：实例由 AiModelInitializer 手动 new（非 Spring bean），无法字段注入。
     * 启动期由 ModelStaticConfigInitializer 把依赖推给静态字段，替代调用路径里
     * SpringUtil.getBean 的服务定位器写法。null 时按各自调用点的旧兜底行为处理。
     */
    private static volatile StringRedisTemplate staticStringRedisTemplate;

    private static volatile ModelHealthService staticModelHealthService;

    private static volatile SkillToolSpi staticSkillToolService;

    private static volatile DelegateAgentSpi staticDelegateAgentTool;

    /** ModelStaticConfigInitializer 启动期调用 */
    public static void initDependencies(StringRedisTemplate stringRedisTemplate,
                                        ModelHealthService modelHealthService,
                                        SkillToolSpi skillToolService,
                                        DelegateAgentSpi delegateAgentTool) {
        staticStringRedisTemplate = stringRedisTemplate;
        staticModelHealthService = modelHealthService;
        staticSkillToolService = skillToolService;
        staticDelegateAgentTool = delegateAgentTool;
    }

    //User#uuid => ttsJobInfo
    private final Cache<String, TtsJobInfo> ttsJobCache;

    @Getter
    private final TtsSetting ttsSetting;

    protected AbstractLLMService(AiModel aiModel, ModelPlatform modelPlatform) {
        super(aiModel, modelPlatform);

        initMaxInputTokens();
        ttsSetting = JsonUtil.fromJson(LocalCache.CONFIGS.get(AdiConstant.SysConfigKey.TTS_SETTING), TtsSetting.class);
        if (null == ttsSetting) {
            log.error("TTS configuration not found, please check adi_sys_config for tts_setting entry");
            throw new BaseException(ErrorEnum.B_TTS_SETTING_NOT_FOUND);
        }

        ttsJobCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build();
    }

    private void initMaxInputTokens() {
        // max_input_tokens 列允许 null：拆箱前必须判空，否则整个模型服务初始化失败
        if (null == this.aiModel.getMaxInputTokens() || this.aiModel.getMaxInputTokens() < 1) {
            this.aiModel.setMaxInputTokens(LLM_MAX_INPUT_TOKENS_DEFAULT);
        }
    }

    public StringRedisTemplate getStringRedisTemplate() {
        if (null == this.stringRedisTemplate) {
            this.stringRedisTemplate = staticStringRedisTemplate;
        }
        return this.stringRedisTemplate;
    }

    public AbstractLLMService setProxyAddress(InetSocketAddress proxyAddress) {
        this.proxyAddress = proxyAddress;
        return this;
    }

    /**
     * 检测该service是否可用（不可用的情况通常是没有配置key）
     * Check if this service is enabled (usually disabled when API key is not configured)
     *
     * @return
     */
    public abstract boolean isEnabled();

    protected boolean checkBeforeChat(SseAskParam params) {
        return true;
    }

    public ChatModel buildChatLLM(ChatModelBuilderProperties properties) {
        ChatModelBuilderProperties tmpProperties = properties;
        if (null == properties) {
            tmpProperties = new ChatModelBuilderProperties();
            tmpProperties.setTemperature(0.7);
            log.info("llmBuilderProperties is null, set default temperature:{}", tmpProperties.getTemperature());
        }
        // 二开（T6-11，2026-10-20）语义修复：temperature == 0 是合法且常用的取值（确定性输出），
        // 原判断 `<= 0` 会把 0 静默覆盖成 0.7，导致同步链路（Agent 节点/QA 等 chat() 路径）
        // 与流式链路（getTemperatureWithDefault 允许 0）行为不一致——配置 0 实际采样 0.7。
        // 修复后：仅 null / <0 / >1 视为非法，两条链路对 0 的语义一致。
        if (null == tmpProperties.getTemperature() || tmpProperties.getTemperature() < 0 || tmpProperties.getTemperature() > 1) {
            tmpProperties.setTemperature(0.7);
            log.info("llmBuilderProperties temperature is invalid, set default temperature:{}", tmpProperties.getTemperature());
        }
        return doBuildChatModel(tmpProperties);
    }

    /**
     * Health probe: send a minimal request to verify the model is reachable.
     * Exceptions propagate so the caller can classify permanent errors
     * (auth failure / model disabled — mark immediately) from transient ones (count failures).
     *
     * @return true if the model responded successfully
     */
    public boolean performHealthCheck() {
        ChatModel chatModel = buildChatLLM(ChatModelBuilderProperties.builder().build());
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(UserMessage.from("hi")))
                .parameters(ChatRequestParameters.builder().maxOutputTokens(1).build())
                .build();
        chatModel.chat(request);
        return true;
    }

    /**
     * Record a user-facing LLM invocation failure so that the model can be
     * marked UNHEALTHY after consecutive failures.
     */
    private void recordInvocationFailure(Throwable error) {
        try {
            ModelHealthService healthService = staticModelHealthService;
            if (null == healthService) {
                // ModelHealthService may not be available (e.g. during startup)
                return;
            }
            healthService.recordFailure(aiModel.getName(), error);
        } catch (Exception ignored) {
            // ModelHealthService may not be available (e.g. during startup)
        }
    }

    protected abstract ChatModel doBuildChatModel(ChatModelBuilderProperties properties);

    public abstract StreamingChatModel buildStreamingChatModel(ChatModelBuilderProperties properties);

    protected abstract LLMException parseError(Object error);

    public abstract TokenCountEstimator getTokenEstimator();

    /**
     * 普通聊天，将原始的用户问题及历史消息发送给AI
     * Normal chat, send raw user question and history messages to AI
     *
     * @param params   请求参数 / Request parameters
     * @param consumer 响应结果回调 / Response result callback
     */
    public void streamingChat(SseAskParam params, TriConsumer<LLMResponseContent, PromptMeta, AnswerMeta> consumer) {
        if (!isEnabled()) {
            log.error("llm service is disabled");
            throw new BaseException(B_LLM_SERVICE_DISABLED);
        }
        if (!checkBeforeChat(params)) {
            log.error("Chat parameter validation failed");
            throw new BaseException(A_PARAMS_ERROR);
        }
        ChatModelRequest httpRequestParams = params.getHttpRequestParams();
        ChatModelBuilderProperties modelProperties = params.getModelProperties();
        log.info("sseChat,messageId:{}", httpRequestParams.getMemoryId());

        ChatRequest chatRequest = createChatRequest(httpRequestParams);
        StreamingChatModel streamingChatModel = buildStreamingChatModel(modelProperties);
        InnerStreamChatParam innerStreamChatParam = InnerStreamChatParam.builder()
                .uuid(params.getUuid())
                .user(params.getUser())
                .streamingChatModel(streamingChatModel)
                .chatRequest(chatRequest)
                .sseUuid(params.getSseUuid())
                .mcpClients(httpRequestParams.getMcpClients())
                .skillBundle(httpRequestParams.getSkillBundle())
                .characterUuid(httpRequestParams.getCharacterUuid())
                // T7-02：透传调用链给工具回调线程（delegate_agent 深度限制/防环）
                .agentCallChain(httpRequestParams.getAgentCallChain())
                .answerContentType(params.getAnswerContentType())
                .consumer(consumer)
                // T6-08/T6-09：降级切换时需要用同一份 modelProperties 重建备用模型的 StreamingChatModel
                .modelProperties(modelProperties)
                // T6-10：三重预算（深度+墙钟+token），InnerStreamChatParam 字段默认 fromConfig()
                .build();
        try {

//Day
            //如果系统设置的语音合成器类型是后端合成，并且当前聊天设置的返回内容是音频，则初始化tts任务并注册回调函数
            // If the system TTS synthesizer is server-side and the current chat is set to return audio, initialize TTS job and register callback
            if (TtsUtil.needTts(ttsSetting, params.getAnswerContentType())) {
                String ttsJobId = UuidUtil.createShort();
                TtsJobInfo jobInfo = new TtsJobInfo();
                TtsModelContext ttsModelContext = new TtsModelContext();
                jobInfo.setJobId(ttsJobId);
                jobInfo.setTtsModelContext(ttsModelContext);
                ttsJobCache.put(params.getUser().getUuid(), jobInfo);
                ttsModelContext.startTtsJob(ttsJobId, params.getVoice(), (ByteBuffer audioFrame) -> {
                    byte[] frameBytes = new byte[audioFrame.remaining()];
                    audioFrame.get(frameBytes);
                    String base64Audio = Base64.getEncoder().encodeToString(frameBytes);
                    SseManager.sendAudio(params.getSseUuid(), base64Audio);
                }, jobInfo::setFilePath, (String errorMsg) -> log.error("tts error: {}", errorMsg));
            }

            //不管是不是需要返回音频文件，都需要innerStreamingChat()
            // Regardless of whether an audio file needs to be returned, innerStreamingChat() is always needed
            innerStreamingChat(innerStreamChatParam);
        } catch (Exception e) {
            ttsJobCache.invalidate(params.getUser().getUuid());
            if (params.getHttpRequestParams() != null) {
                closeMcpClients(params.getHttpRequestParams().getMcpClients());
            }
            throw e;
        }

    }

    /**
     * 内部流式聊天方法，处理工具调用等复杂逻辑
     * Internal streaming chat method, handling complex logic such as tool calls
     *
     * @param params 参数对象，包含流式聊天所需的所有信息 / Parameter object containing all info needed for streaming chat
     */
    private void innerStreamingChat(InnerStreamChatParam params) {
        // T6-10：工具循环三重预算（深度 + 墙钟时间 + token 累计），任一超限中止。
        // 原 MAX_TOOL_CALL_DEPTH=5 硬编码由 budget.maxDepth（adi.agent.tool-loop-max-depth，默认 5）接管。
        ToolLoopBudget budget = params.getToolLoopBudget();
        if (!budget.consumeDepth()) {
            String reason = budget.checkExceeded().orElse(ToolLoopBudget.REASON_DEPTH);
            log.error("Tool call loop aborted by budget, reason:{}, depthUsed:{}, tokensUsed:{}",
                    reason, budget.getDepthUsed(), budget.getTokensUsed());
            SseManager.errorAndShutdown(new RuntimeException("Tool call loop aborted: budget exceeded (" + reason + ")"), params.getSseUuid());
            closeMcpClients(params.getMcpClients());
            return;
        }
        String exceeded = budget.checkExceeded().orElse(null);
        if (null != exceeded) {
            log.error("Tool call loop aborted by budget, reason:{}, tokensUsed:{}, depthUsed:{}",
                    exceeded, budget.getTokensUsed(), budget.getDepthUsed());
            SseManager.errorAndShutdown(new RuntimeException("Tool call loop aborted: budget exceeded (" + exceeded + ")"), params.getSseUuid());
            closeMcpClients(params.getMcpClients());
            return;
        }
        Map<ToolSpecification, ToolExecutor> toolExecutorMap = getRequestTools(params.getMcpClients(), params.getSkillBundle(),
                null == params.getUser() ? null : params.getUser().getId(), params.getCharacterUuid(), params.getAgentCallChain());
        dispatchStreamingChat(params, toolExecutorMap);
    }

    /**
     * T6-08/T6-09：发起一次 streaming 调用。错误时由 {@link #handleStreamingError}
     * 决策：首 token 前可重试（同模型 → 降级链），首 token 后只中止不重试。
     */
    private void dispatchStreamingChat(InnerStreamChatParam params, Map<ToolSpecification, ToolExecutor> toolExecutorMap) {
        params.getStreamingChatModel().chat(params.getChatRequest(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // T6-08：首 token 已输出，此后错误只中止不重试（防重复输出）
                params.markFirstToken();
                SseManager.parseAndSendPartialMsg(params.getSseUuid(), partialResponse);
                ttsOnPartialMessage(params, partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                // T6-08：thinking 片段同样对客户端可见，视为"已输出首 token"
                params.markFirstToken();
                SseManager.sendThinking(params.getSseUuid(), partialThinking.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                // T6-10：累计本步 LLM 调用的 token（含中间工具调用步骤），供 token 预算判定
                if (null != response.metadata() && null != response.metadata().tokenUsage()) {
                    TokenUsage usage = response.metadata().tokenUsage();
                    long in = null == usage.inputTokenCount() ? 0 : usage.inputTokenCount();
                    long out = null == usage.outputTokenCount() ? 0 : usage.outputTokenCount();
                    params.getToolLoopBudget().accumulateTokens(in + out);
                }
                AiMessage responseAiMessage = response.aiMessage();
                if (responseAiMessage.hasToolExecutionRequests()) {
                    // 如果有工具执行请求
                    // If there are tool execution requests
                    List<ToolExecutionResultMessage> toolExecutionMessages = createToolExecutionMessages(responseAiMessage, toolExecutorMap, params.getSseUuid());

                    //Cache this intermediate (tool-call) step's tokens so the request total in Redis
                    //covers every LLM call, not just the final response (mirrors innerChatWithDepth).
                    cacheTokenUsage(params.getUuid(), response);

                    //mcp调用消息格式参考：https://docs.langchain4j.dev/tutorials/tools/
                    AiMessage aiMessage = AiMessage.aiMessage(responseAiMessage.toolExecutionRequests());
                    List<ChatMessage> messages = new ArrayList<>(params.getChatRequest().messages());
                    messages.add(aiMessage);
                    messages.addAll(toolExecutionMessages);
                    params.setChatRequest(ChatRequest.builder()
                            .messages(messages)
                            .parameters(params.getChatRequest().parameters())
                            .build());
                    params.setToolCallDepth(params.getToolCallDepth() + 1);
                    // recursive call now with tool calling results
                    innerStreamingChat(params);
                } else {
                    TtsJobInfo jobInfo = ttsOnComplete(params);
                    String filePath = null != jobInfo ? jobInfo.getFilePath() : null;
                    //结束整个对话任务
                    Pair<PromptMeta, AnswerMeta> pair = SseManager.calculateToken(response, params.getUuid());
                    params.getConsumer().accept(new LLMResponseContent(response.aiMessage().thinking(), response.aiMessage().text(), filePath), pair.getLeft(), pair.getRight());
                    closeMcpClients(params.getMcpClients());
                }
            }

            @Override
            public void onError(Throwable error) {
                recordInvocationFailure(error);
                handleStreamingError(params, toolExecutorMap, error);
            }
        });
    }

    /**
     * T6-08/T6-09：streaming 错误处理——决策重试 / 降级 / 中止。
     * ⚠️ firstTokenSent 后一律 ABORT（重试会产生重复输出）；
     * PERMANENT 错误（鉴权/参数/配置）直接报错不浪费额度；
     * RETRIABLE 且未出首 token：先同模型重试（adi.llm.stream-same-model-retries，默认 1 次），
     * 再按 adi.llm.fallback-models 降级链逐个尝试。
     */
    private void handleStreamingError(InnerStreamChatParam params, Map<ToolSpecification, ToolExecutor> toolExecutorMap, Throwable error) {
        LLMErrorType type = LLMErrorClassifier.classify(error);
        LLMFallbackMetrics.get().recordDetected(type, aiModel.getName(), error.getMessage());

        int sameModelRetries = streamSameModelRetriesDefault;
        int fallbackCount = LLMFallbackRouter.effectiveChain(this).size();
        StreamingRetryPolicy.StreamingDecision decision = StreamingRetryPolicy.decide(
                type, params.isFirstTokenSent(), params.getStreamAttempt() + 1, sameModelRetries, fallbackCount);

        switch (decision) {
            case RETRY_SAME -> {
                LLMFallbackMetrics.get().recordSameModelRetry(aiModel.getName(), params.getStreamAttempt() + 1);
                params.setStreamAttempt(params.getStreamAttempt() + 1);
                dispatchStreamingChat(params, toolExecutorMap);
            }
            case RETRY_FALLBACK -> {
                AbstractLLMService fallback = LLMFallbackRouter.nextFallback(this, params.getTriedModels());
                if (null == fallback) {
                    LLMFallbackMetrics.get().recordFallbackExhausted(aiModel.getName(), error.getMessage());
                    SseManager.errorAndShutdown(error, params.getSseUuid());
                    closeMcpClients(params.getMcpClients());
                    return;
                }
                params.setStreamAttempt(params.getStreamAttempt() + 1);
                params.getTriedModels().add(fallback.getAiModel().getName());
                LLMFallbackMetrics.get().recordFallbackAttempt(aiModel.getName(), fallback.getAiModel().getName());
                try {
                    params.setStreamingChatModel(fallback.buildStreamingChatModel(params.getModelProperties()));
                } catch (Exception e) {
                    log.error("build fallback streaming model failed, model:{}", fallback.getAiModel().getName(), e);
                    SseManager.errorAndShutdown(error, params.getSseUuid());
                    closeMcpClients(params.getMcpClients());
                    return;
                }
                // 备用模型也要重建工具执行器（MCP/Skill executor 与模型无关，可复用；此处保守重建）
                dispatchStreamingChat(params, toolExecutorMap);
            }
            case ABORT -> {
                if (params.isFirstTokenSent()) {
                    log.warn("streaming aborted after first token (no retry to avoid duplicate output), model:{}, error:{}",
                            aiModel.getName(), error.getMessage());
                }
                SseManager.errorAndShutdown(error, params.getSseUuid());
                closeMcpClients(params.getMcpClients());
            }
        }
    }

    public ChatResponse chat(SseAskParam params) {
        if (!isEnabled()) {
            log.error("llm service is disabled");
            throw new BaseException(B_LLM_SERVICE_DISABLED);
        }
        if (!checkBeforeChat(params)) {
            log.error("Chat parameter validation failed");
            throw new BaseException(A_PARAMS_ERROR);
        }

        ChatModelRequest chatModelRequest = params.getHttpRequestParams();
        ChatModelBuilderProperties modelProperties = params.getModelProperties();
        ChatModel chatModel = buildChatLLM(modelProperties);
        ChatRequest chatRequest = createChatRequest(chatModelRequest);

        try {
            ChatResponse chatResponse = chatModel.chat(chatRequest);
            if (chatResponse.aiMessage().hasToolExecutionRequests()) {
                return innerChat(params.getUuid(), chatModel, chatModelRequest, chatRequest);
            }

            cacheTokenUsage(params.getUuid(), chatResponse);
            return chatResponse;
        } catch (Exception e) {
            recordInvocationFailure(e);
            // T6-09：同步链路模型降级路由（PERMANENT 直接抛出；RETRIABLE 按降级链重试备用模型）
            return chatWithFallback(params, e);
        }
    }

    /**
     * T6-09（2026-10-20）：同步 chat 的模型降级路由。
     * <p>
     * LLMErrorClassifier 把错误分为 RETRIABLE（网络/超时/429/5xx）与 PERMANENT（401/403/400/配置错）：
     * - PERMANENT：直接抛出——降级也没用，不浪费备用模型额度；
     * - RETRIABLE：按 adi.llm.fallback-models 降级链逐个尝试备用模型（开关 adi.llm.fallback-enabled，默认 true）；
     * - 降级链耗尽：抛出最后一个错误并记 metrics（fallbackExhausted）；
     * - 防级联：params.fallbackTried 守卫，备用模型的 chat() 不再二次降级（A→B 后 B 失败不会 C→D→…）。
     */
    private ChatResponse chatWithFallback(SseAskParam params, Exception primaryError) {
        LLMErrorType type = LLMErrorClassifier.classify(primaryError);
        LLMFallbackMetrics.get().recordDetected(type, aiModel.getName(), primaryError.getMessage());
        if (type == LLMErrorType.PERMANENT || params.isFallbackTried() || !LLMFallbackRouter.fallbackEnabled()) {
            throw (primaryError instanceof RuntimeException re) ? re : new RuntimeException(primaryError);
        }
        Exception lastError = primaryError;
        java.util.Set<String> tried = new java.util.LinkedHashSet<>();
        tried.add(aiModel.getName());
        while (true) {
            AbstractLLMService fallback = LLMFallbackRouter.nextFallback(this, tried);
            if (null == fallback) {
                LLMFallbackMetrics.get().recordFallbackExhausted(aiModel.getName(), lastError.getMessage());
                throw (lastError instanceof RuntimeException re) ? re : new RuntimeException(lastError);
            }
            tried.add(fallback.getAiModel().getName());
            LLMFallbackMetrics.get().recordFallbackAttempt(aiModel.getName(), fallback.getAiModel().getName());
            try {
                params.setFallbackTried(true);
                ChatResponse resp = fallback.chat(params);
                LLMFallbackMetrics.get().recordFallbackSuccess(fallback.getAiModel().getName());
                return resp;
            } catch (Exception e) {
                log.warn("llm-fallback attempt failed, model:{}, error:{}", fallback.getAiModel().getName(), e.getMessage());
                lastError = e;
            }
        }
    }

    /**
     * 程序内部调用的聊天方法，通常用于处理工具调用等复杂逻辑
     * Internal chat method, typically used for handling complex logic such as tool calls
     *
     * @param uuid                   唯一标识 / Unique identifier
     * @param chatModel              聊天模型 / Chat model
     * @param chatModelRequest 聊天模型参数 / Chat model parameters
     * @param chatRequest            聊天请求 / Chat request
     * @return ChatResponse 聊天响应 / Chat response
     */
    private ChatResponse innerChat(String uuid, ChatModel chatModel, ChatModelRequest chatModelRequest, ChatRequest chatRequest) {
        // T6-10：同步工具循环的三重预算（深度+墙钟+token），一次请求一个预算实例
        return innerChatWithDepth(uuid, chatModel, chatModelRequest, chatRequest, 0, ToolLoopBudget.fromConfig());
    }

    private ChatResponse innerChatWithDepth(String uuid, ChatModel chatModel, ChatModelRequest chatModelRequest, ChatRequest chatRequest, int depth, ToolLoopBudget budget) {
        // T6-10：三重预算替代 MAX_TOOL_CALL_DEPTH 硬编码；中止异常信息带具体超限原因
        if (!budget.consumeDepth()) {
            String reason = budget.checkExceeded().orElse(ToolLoopBudget.REASON_DEPTH);
            log.error("Tool call loop aborted by budget, reason:{}, depthUsed:{}, tokensUsed:{}", reason, budget.getDepthUsed(), budget.getTokensUsed());
            throw new BaseException(ErrorEnum.B_LLM_SERVICE_DISABLED.getCode(), "Tool call loop aborted: budget exceeded (" + reason + ")");
        }
        String exceeded = budget.checkExceeded().orElse(null);
        if (null != exceeded) {
            log.error("Tool call loop aborted by budget, reason:{}, tokensUsed:{}, depthUsed:{}", exceeded, budget.getTokensUsed(), budget.getDepthUsed());
            throw new BaseException(ErrorEnum.B_LLM_SERVICE_DISABLED.getCode(), "Tool call loop aborted: budget exceeded (" + exceeded + ")");
        }
        try {
            ChatResponse chatResponse = chatModel.chat(chatRequest);
            // T6-10：累计本步 LLM 调用 token（input+output，含中间工具调用步骤）
            if (null != chatResponse.metadata() && null != chatResponse.metadata().tokenUsage()) {
                TokenUsage usage = chatResponse.metadata().tokenUsage();
                long in = null == usage.inputTokenCount() ? 0 : usage.inputTokenCount();
                long out = null == usage.outputTokenCount() ? 0 : usage.outputTokenCount();
                budget.accumulateTokens(in + out);
            }
            AiMessage responseAiMessage = chatResponse.aiMessage();
            if (responseAiMessage.hasToolExecutionRequests()) {
                Map<ToolSpecification, ToolExecutor> toolExecutorMap = getRequestTools(chatModelRequest.getMcpClients(), chatModelRequest.getSkillBundle(),
                        chatModelRequest.getChatUserId(), chatModelRequest.getCharacterUuid(), chatModelRequest.getAgentCallChain());
                List<ToolExecutionResultMessage> toolExecutionMessages = createToolExecutionMessages(responseAiMessage, toolExecutorMap, null);

                AiMessage aiMessage = AiMessage.aiMessage(responseAiMessage.toolExecutionRequests());
                List<ChatMessage> messages = new ArrayList<>(chatRequest.messages());
                messages.add(aiMessage);
                messages.addAll(toolExecutionMessages);

                cacheTokenUsage(uuid, chatResponse);

                return innerChatWithDepth(uuid, chatModel, chatModelRequest, ChatRequest.builder()
                        .messages(messages)
                        .parameters(chatRequest.parameters())
                        .build(), depth + 1, budget);
            }
            cacheTokenUsage(uuid, chatResponse);
            return chatResponse;
        } finally {
            if (depth == 0) {
                closeMcpClients(chatModelRequest.getMcpClients());
            }
        }
    }

    /**
     * 缓存token使用情况
     * Cache token usage
     *
     * @param uuid         唯一标识 / Unique identifier
     * @param chatResponse 聊天响应 / Chat response
     */
    private void cacheTokenUsage(String uuid, ChatResponse chatResponse) {
        if (chatResponse.metadata() == null || chatResponse.metadata().tokenUsage() == null) {
            return;
        }
        TokenUsage tokenUsage = chatResponse.metadata().tokenUsage();
        log.info("ChatModel token cost,uuid:{},inputTokenCount:{},outputTokenCount:{}", uuid, tokenUsage.inputTokenCount(), tokenUsage.outputTokenCount());
        LLMTokenUtil.cacheTokenUsage(getStringRedisTemplate(), uuid, tokenUsage);
    }


    private List<ChatMessage> createChatMessages(ChatModelRequest chatModelRequest) {
        String memoryId = chatModelRequest.getMemoryId();
        List<Content> userContents = new ArrayList<>();
        userContents.add(TextContent.from(chatModelRequest.getUserMessage()));
        List<ChatMessage> chatMessages = new ArrayList<>();
        if (StringUtils.isNotBlank(memoryId)) {

            TokenCountEstimator tokenCountEstimator;
            String tokenEstimatorName = TokenEstimatorThreadLocal.getTokenEstimator();
            if (StringUtils.isBlank(tokenEstimatorName) && null != getTokenEstimator()) {
                tokenCountEstimator = getTokenEstimator();
            } else {
                tokenCountEstimator = TokenEstimatorFactory.create(tokenEstimatorName);
            }

            //滑动窗口算法限制消息长度
            // Sliding window algorithm to limit message length
            TokenWindowChatMemory memory = TokenWindowChatMemory.builder()
                    .chatMemoryStore(MapDBChatMemoryStore.getSingleton())
                    .id(memoryId)
                    //二开（2026-10-05）：保持系统提示词在首位，与 CompositeRag 的记忆窗口保持一致。
                    //二开：keep the system message first, consistent with CompositeRag.
                    .alwaysKeepSystemMessageFirst(true)
                    .maxTokens(aiModel.getMaxInputTokens(), tokenCountEstimator)
                    .build();
            //系统提示词不放进短期记忆，改为在最终组装时前置（见下方 normalizeSystemMessage）。
            //原因：TokenWindowChatMemory 的 alwaysKeepSystemMessageFirst 默认 false，新 SystemMessage
            //会被追加到列表末尾；再叠加 MapDBChatMemoryStore.updateMessages 只保留 index 0 的
            //SystemMessage，结果是系统提示词位置错乱、且每轮都被存储层丢弃（身份护栏因此完全失效）。
            //Do NOT put the system message into short-term memory; it is prepended during assembly.
            //先把历史里那份可能已过期的系统提示词刷新成本轮的版本。
            MapDBChatMemoryStore.getSingleton().refreshSystemMessage(memoryId, chatModelRequest.getSystemMessage());

            //处理重复的UserMessage
            // Handle duplicate UserMessage
            if (!memory.messages().isEmpty()) {
                ChatMessage lastMessage = memory.messages().get(memory.messages().size() - 1);
                if (lastMessage instanceof UserMessage) {
                    List<ChatMessage> list = memory.messages().subList(0, memory.messages().size() - 1);
                    memory.clear();
                    list.forEach(memory::add);
                }
            }

            memory.add(UserMessage.from(userContents));

            //得到截断后符合maxTokens的文本消息
            // Get truncated text messages that fit within maxTokens
            chatMessages.addAll(memory.messages());
            //把系统提示词统一归位：剔除历史里残留的 SystemMessage，再把本次的放到最前面。
            //保证①提示词始终在首位 ②每轮都生效（改人设/追加护栏对老会话立即生效）③不污染历史记忆。
            normalizeSystemMessage(chatMessages, chatModelRequest.getSystemMessage());

            // 二开（T6-06，2026-10-20）：长对话摘要压缩。历史消息估算 token 超过阈值
            // （adi.chat.summarize-token-threshold，默认 8000）时自动摘要压缩。
            // ⚠️ 必须在 normalizeSystemMessage 之后调用：系统提示词（index 0）永远保留原样，
            // 只压缩历史轮次（identity-guard 教训：压缩绝不能吃掉系统提示词）；
            // 压缩产物以 [CONVERSATION_SUMMARY] 标记为 SystemMessage 插在 index 1。
            // 压缩请求走现有 LLM 通道（buildChatLLM），低温 0.3 保证摘要稳定。
            chatMessages = ConversationCompressor.compressWithConfig(chatMessages,
                    buildChatLLM(ChatModelBuilderProperties.builder().temperature(0.3).build()),
                    tokenCountEstimator);

            //AI services currently do not support multimodality, use the low-level API for this. https://docs.langchain4j.dev/tutorials/ai-services#multimodality
            //重新组装用户消息及追加图片消息到chatMessage
            // Reassemble user message and append image messages to chatMessage
            List<Content> imageContents = ImageUtil.urlsToImageContent(chatModelRequest.getImageUrls());
            if (CollectionUtils.isNotEmpty(imageContents)) {
                int lastIndex = chatMessages.size() - 1;
                UserMessage lastMessage = (UserMessage) chatMessages.get(lastIndex);
                chatMessages.remove(lastIndex);
                List<Content> userMessage = new ArrayList<>();
                userMessage.addAll(lastMessage.contents());
                userMessage.addAll(imageContents);
                chatMessages.add(UserMessage.from(userMessage));
            }
            return chatMessages;
        } else {
            List<Content> imageContents = ImageUtil.urlsToImageContent(chatModelRequest.getImageUrls());
            if (CollectionUtils.isNotEmpty(imageContents)) {
                userContents.addAll(imageContents);
            }
            chatMessages.add(UserMessage.from(userContents));
            normalizeSystemMessage(chatMessages, chatModelRequest.getSystemMessage());
        }
        return chatMessages;
    }

    /**
     * 系统提示词归位：剔除历史里残留的所有 SystemMessage，再把本次的系统提示词放到列表首位。
     * <p>
     * Normalize the system message: drop every stale {@code SystemMessage} left over from short-term
     * memory, then prepend the system message of the current request.
     * </p>
     * <p>
     * 必要性：短期记忆（MapDBChatMemoryStore）会持久化首次写入的 SystemMessage，而
     * TokenWindowChatMemory 默认不会把新的 SystemMessage 放首位，导致系统提示词位置错乱、
     * 内容陈旧。这里统一以「本次请求」为准，保证人设修改与身份护栏立即生效且不被历史污染。
     * </p>
     */
    private static void normalizeSystemMessage(List<ChatMessage> chatMessages, String systemMessage) {
        chatMessages.removeIf(msg -> msg instanceof SystemMessage);
        if (StringUtils.isNotBlank(systemMessage)) {
            chatMessages.add(0, SystemMessage.from(systemMessage));
        }
    }

    /**
     * 收集本次对话可用的全部工具：MCP 工具 + Agent Skills 内置工具 + create_skill 常驻工具，
     * 统一映射到 ToolExecutor（MCP 包装 client.executeTool，Skill 委托 SkillToolSpi）。
     * 二开（2026-09-21）：新增 create_skill 常驻注册（对话中直接创建技能），userId/characterUuid
     * 由装配层显式传入——工具在 LLM 回调线程执行，ThreadContext 不保证可达。
     * 二开（T7-02，2026-10-20）：新增 delegate_agent 常驻注册（agent-as-tool），开关关闭时不注册；
     * 调用链随参数透传（嵌套深度限制/防环在门面收口）。
     */
    private Map<ToolSpecification, ToolExecutor> getRequestTools(List<McpClient> mcpClients, SkillToolSpi.SkillBundle skillBundle, Long chatUserId, String characterUuid, AgentCallChain agentCallChain) {
        Map<ToolSpecification, ToolExecutor> tools = new HashMap<>();
        if (!CollectionUtils.isEmpty(mcpClients)) {
            // MCP Tools
            for (McpClient mcpClient : mcpClients) {
                for (ToolSpecification toolSpecification : mcpClient.listTools()) {
                    tools.put(toolSpecification, (req, memoryId) -> mcpClient.executeTool(req).resultText());
                }
            }
        }
        // Agent Skills built-in tools
        if (null != skillBundle && !CollectionUtils.isEmpty(skillBundle.toolSpecifications())) {
            ToolExecutor skillExecutor = staticSkillToolService.executorFor(skillBundle.skills());
            for (ToolSpecification toolSpecification : skillBundle.toolSpecifications()) {
                tools.put(toolSpecification, skillExecutor);
            }
        }
        // create_skill：常驻注册，任何角色对话都可创建技能
        SkillToolSpi skillToolService = staticSkillToolService;
        ToolSpecification createSpec = skillToolService.buildCreateSkillSpecification();
        tools.put(createSpec, (req, memoryId) -> skillToolService.executeCreateSkill(req, chatUserId, characterUuid));
        // T7-02：delegate_agent（agent-as-tool）常驻注册，热开关关闭时不注入（模型看不到该工具）
        DelegateAgentSpi delegateAgentTool = staticDelegateAgentTool;
        if (delegateAgentTool.enabled()) {
            ToolSpecification delegateSpec = delegateAgentTool.buildSpecification();
            tools.put(delegateSpec, (req, memoryId) -> delegateAgentTool.invoke(req, chatUserId, characterUuid, agentCallChain));
        }
        return tools;
    }

    private ChatRequest createChatRequest(ChatModelRequest httpRequestParams) {

        log.info("sseChat,messageId:{}", httpRequestParams.getMemoryId());
        List<ChatMessage> chatMessages = createChatMessages(httpRequestParams);

        List<ToolSpecification> toolSpecifications = new ArrayList<>();
        List<McpClient> mcpClients = httpRequestParams.getMcpClients();
        if (!CollectionUtils.isEmpty(mcpClients)) {
            log.info("mcp clients configured, creating tool specs");
            ToolProvider toolProvider = McpToolProvider.builder()
                    .mcpClients(mcpClients)
                    .build();
            ToolService toolService = new ToolService();
            toolService.toolProvider(toolProvider);
            InvocationContext invocationContext = InvocationContext.builder()
                    .chatMemoryId(UuidUtil.createShort())
                    .timestampNow()
                    .build();
            ToolServiceContext toolServiceContext = toolService.createContext(invocationContext, (UserMessage) chatMessages.get(chatMessages.size() - 1), chatMessages);
            log.info("tool specs:{}", toolServiceContext.toolSpecifications());
            // ⚠️ langchain4j 返回的 toolSpecifications 是只读集合（Collections.unmodifiableList），
            // 直接赋值给局部变量后，下面 addAll/add Skill 工具会抛 UnsupportedOperationException，
            // 表现为「配了 MCP 的角色一发消息就 500，模型无任何响应」。必须拷进可变 ArrayList。
            toolSpecifications = new ArrayList<>(toolServiceContext.toolSpecifications());
        }
        // 二开（2026-09-21）修复：Agent Skills 工具（read_skill 等）与 create_skill 的
        // ToolSpecification 此前从未进入 ChatRequest，模型根本看不到这些工具，自然永远不会调用
        // （执行侧 getRequestTools 按 name 匹配 executor，name 一致即可安全互通）。
        SkillToolSpi skillToolService = staticSkillToolService;
        if (null != httpRequestParams.getSkillBundle()
                && !CollectionUtils.isEmpty(httpRequestParams.getSkillBundle().toolSpecifications())) {
            toolSpecifications.addAll(httpRequestParams.getSkillBundle().toolSpecifications());
        }
        toolSpecifications.add(skillToolService.buildCreateSkillSpecification());
        // T7-02：delegate_agent（agent-as-tool）工具清单注入——执行侧 getRequestTools 按 name
        // 匹配 executor，两处 name 一致即可安全互通；热开关关闭时不注入（模型看不到）
        DelegateAgentSpi delegateAgentTool = staticDelegateAgentTool;
        if (delegateAgentTool.enabled()) {
            toolSpecifications.add(delegateAgentTool.buildSpecification());
        }

        DefaultChatRequestParameters.Builder<?> builder = ChatRequestParameters.builder();
        builder.toolSpecifications(toolSpecifications);

        // Response format
        String responseFormat = httpRequestParams.getResponseFormat();
        log.info("Response format:{}", responseFormat);
        if (StringUtils.isNotBlank(responseFormat)) {
            if (aiModel.getResponseFormatTypes().contains(responseFormat)) {
                builder.responseFormat(RESPONSE_FORMAT_TYPE_JSON_OBJECT.equals(responseFormat) ? ResponseFormat.JSON : ResponseFormat.TEXT);
            } else {
                log.warn("Current model does not support JSON response format (most LLMs support JSON format, please check ai_model.response_format_types for json_object), model name: {}, supported formats: {}", aiModel.getName(), aiModel.getResponseFormatTypes());
            }
        }

        // Enable thinking
        Map<String, Object> customParameters = new HashMap<>();
        if (null != httpRequestParams.getReturnThinking()) {
            customParameters.put(ENABLE_THINKING, httpRequestParams.getReturnThinking());
        }
        if (null != httpRequestParams.getEnableWebSearch()) {
            customParameters.put(ENABLE_WEB_SEARCH, httpRequestParams.getEnableWebSearch());
        }
        ChatRequestParameters parameters = doCreateChatRequestParameters(builder.build(), customParameters);

        return ChatRequest.builder()
                .messages(chatMessages)
                .parameters(parameters)
                .build();
    }

    protected ChatRequestParameters doCreateChatRequestParameters(ChatRequestParameters defaultParameters, Map<String, Object> customParameters) {
        return defaultParameters;
    }

    private List<ToolExecutionResultMessage> createToolExecutionMessages(AiMessage aiMessage, Map<ToolSpecification, ToolExecutor> toolExecutorMap, String sseUuid) {
        List<ToolExecutionResultMessage> toolExecutionMessages = new ArrayList<>();
        aiMessage.toolExecutionRequests().forEach(req -> {
            // T6-12：工具入参可能含密钥（HTTP/MCP/DB 节点配置），日志统一脱敏+截断
            log.warn("tool exec request, name:{}, arguments:{}", req.name(), LogSanitizer.sanitize(req.arguments()));
            req = parseToolRequest(req);
            ToolExecutor selectedExecutor = null;
            for (Map.Entry<ToolSpecification, ToolExecutor> entry : toolExecutorMap.entrySet()) {
                if (entry.getKey().name().equals(req.name())) {
                    selectedExecutor = entry.getValue();
                }
            }
            if (null == selectedExecutor) {
                toolExecutionMessages.add(ToolExecutionResultMessage.from(req,
                        "No Tool executor found for this tool request"));
                return;
            }
            long toolStart = System.currentTimeMillis();
            try {
                final String result = selectedExecutor.execute(req, null);
                long toolDuration = System.currentTimeMillis() - toolStart;
                // T6-12：工具出参（页面抓取/脚本输出/DB 结果）同样脱敏+截断，日志体积可控
                log.info("tool execute result:{},duration:{}ms", LogSanitizer.sanitize(result), toolDuration);
                SseManager.sendToolCall(sseUuid, req.name(), toolDuration, true);
                toolExecutionMessages.add(ToolExecutionResultMessage.from(req, result));
            } catch (Exception e) {
                long toolDuration = System.currentTimeMillis() - toolStart;
                log.debug("Error executing tool {},duration:{}ms,req:{}", req.name(), toolDuration, LogSanitizer.sanitize(req.arguments()), e);
                SseManager.sendToolCall(sseUuid, req.name(), toolDuration, false);
                toolExecutionMessages.add(ToolExecutionResultMessage.from(req, e.getMessage()));
            }
        });
        return toolExecutionMessages;
    }

    /**
     * 将收到的内容转换成音频
     * 条件：系统设置了tts为服务端转换 && 答案类型为音频
     * Convert received content to audio
     * Condition: System TTS is set to server-side conversion && answer type is audio
     *
     * @param params          内部迭代方法入参 / Internal iteration method input
     * @param partialResponse 文本内容 / Text content
     */
    private void ttsOnPartialMessage(InnerStreamChatParam params, String partialResponse) {
        TtsJobInfo jobInfo = ttsJobCache.getIfPresent(params.getUser().getUuid());
        if (null != jobInfo && null != jobInfo.getTtsModelContext()
            && AdiConstant.TtsConstant.SYNTHESIZER_SERVER.equals(ttsSetting.getSynthesizerSide())
            && params.getAnswerContentType() == AdiConstant.CharacterConstant.ANSWER_CONTENT_TYPE_AUDIO) {
            jobInfo.getTtsModelContext().processPartialText(jobInfo.getJobId(), partialResponse);
        }
    }

    private TtsJobInfo ttsOnComplete(InnerStreamChatParam params) {
        TtsJobInfo jobInfo = ttsJobCache.getIfPresent(params.getUser().getUuid());
        if (null != jobInfo && null != jobInfo.getTtsModelContext()) {
            // complete() 内部会阻塞至 TTS 异步回调结束（最多 30s），保证 jobInfo.filePath 已被回填。
            // 详见 AbstractTtsModelService#complete 的模板方法实现。
            // <p>
            // complete() blocks until the TTS async callback finishes (up to 30s), guaranteeing that
            // jobInfo.filePath has been populated. See the template method in AbstractTtsModelService#complete.
            jobInfo.getTtsModelContext().complete(jobInfo.getJobId());
        }
        //Remove job info
        ttsJobCache.invalidate(params.getUser().getUuid());
        return jobInfo;
    }

    private void closeMcpClients(List<McpClient> mcpClients) {
        if (mcpClients == null) {
            return;
        }
        mcpClients.forEach(item -> {
            try {
                item.close();
            } catch (Exception e) {
                log.error("close mcp client error", e);
            }
        });
    }

    /**
     * 如果工具请求参数中没有包含id，则手动解析该参数以补充 id 和 name
     * 部分模型（如硅基流动）返回的工具请求可能没有id和name，需要手动解析，如 ToolExecutionRequest { id = "", name = "", arguments = "maps_weather {"city": "广州"}" }
     * If the tool request parameter does not contain an id, manually parse it to supplement id and name
     * Some models (e.g., SiliconFlow) may return tool requests without id and name, requiring manual parsing
     *
     * @param req 工具请求参数 / Tool request parameter
     */
    private ToolExecutionRequest parseToolRequest(ToolExecutionRequest req) {
        if (StringUtils.isBlank(req.id())) {
            String arguments = req.arguments();
            String name = req.name();
            if (StringUtils.isBlank(name) && StringUtils.isNotBlank(arguments) && !arguments.startsWith("{")) {
                String[] args = arguments.split(" ");
                if (args.length > 0) {
                    name = args[0];
                    arguments = arguments.substring(name.length()).trim();
                } else {
                    name = "name_" + UuidUtil.createShort();
                }
            }
            return ToolExecutionRequest.builder().id("id_" + UuidUtil.createShort()).name(name).arguments(arguments).build();
        }
        return req;
    }
}
