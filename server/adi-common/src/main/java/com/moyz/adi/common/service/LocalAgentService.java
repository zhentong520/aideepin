package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.entity.AgentTraceSpan;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.LLMCallRecord;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.util.CharacterChatHelper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.PromptUtil;
import com.moyz.adi.common.util.TraceUtil;
import com.moyz.adi.common.util.UuidUtil;
import com.moyz.adi.common.vo.*;

import dev.langchain4j.model.chat.response.ChatResponse;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.moyz.adi.common.enums.ErrorEnum.A_CHARACTER_NOT_FOUND;

/**
 * Local in-process implementation of {@link AgentService}.
 *
 * <p>Loads a Character's configuration and executes the full agent pipeline:
 * RAG retrieval → prompt enhancement → LLM call.</p>
 *
 * <p>Responsibilities end at LLM response — does NOT handle:
 * message persistence, quota deduction, memory storage, or audio/TTS.</p>
 *
 * <p>T6-13/T6-14（2026-10-20）：本方法是 Agent 渠道统一入口（AgentNode 与用户端 Agent 对话都走到这），
 * 在此打全链路 trace（entry → rag → tools → llm）、补 AgentMetrics 指标、
 * 写 Agent 渠道 LLM 调用记录（受 sys_config「llm_cost_record_enabled」开关控制，缺省开）。</p>
 */
@Slf4j
@Service
public class LocalAgentService implements AgentService {

    @Resource
    private CharacterService characterService;

    @Resource
    private AsyncTaskExecutor mainExecutor;

    @Resource
    private AgentTraceSpanService agentTraceSpanService;

    @Resource
    private LLMCallRecordService llmCallRecordService;

    @Override
    public AgentResult invoke(AgentRequest request, User user, String uuid) {
        long invokeStart = System.currentTimeMillis();
        LocalDateTime requestTime = LocalDateTime.now();
        boolean traceCreated = StringUtils.isBlank(TraceUtil.getTraceId());
        String traceId = TraceUtil.ensureTraceId();
        List<AgentTraceSpan> spans = new ArrayList<>();

        try {
            // 1. Load Character
            Character character = characterService.lambdaQuery()
                    .eq(Character::getUuid, request.getCharacterUuid())
                    .eq(Character::getIsDeleted, false)
                    .oneOpt()
                    .orElseThrow(() -> new BaseException(A_CHARACTER_NOT_FOUND));

            // 2. Resolve LLM service (use config override or system default)
            AbstractLLMService llmService = LLMContext.getServiceOrDefault(
                    request.getModelPlatform(), request.getModelName());

            // 3. RAG retrieval (if enabled)
            List<RetrieverWrapper> retrieverWrappers = new ArrayList<>();
            int retrievalCount = 0;
            if (request.isEnableRag()) {
                long ragStart = System.currentTimeMillis();
                List<KbInfoResp> filteredKb = new ArrayList<>();
                if (StringUtils.isNotBlank(character.getKbIds())) {
                    List<Long> kbIds = Arrays.stream(character.getKbIds().split(","))
                            .map(Long::parseLong).toList();
                    filteredKb = characterService.filterEnableKb(user, kbIds);
                }
                retrieverWrappers = CharacterChatHelper.retrieve(
                        character.getId(), filteredKb, llmService, request.getInputText(), mainExecutor);
                retrievalCount = retrieverWrappers.stream()
                        .mapToInt(w -> w.getResponse() != null ? w.getResponse().size() : 0).sum();
                spans.add(buildSpan(traceId, "", "rag.retrieve", "retrieval", ragStart,
                        detailOf().put("retrievalCount", retrievalCount)
                                .put("kbCount", filteredKb.size())));
            }

            // 4. Prompt enhancement
            Pair<String, String> memoryAndKnowledge = CharacterChatHelper.buildMemoryAndKnowledge(retrieverWrappers);
            String effectiveLocale = StringUtils.isNotBlank(user.getLocale())
                    ? user.getLocale()
                    : Objects.toString(SysConfigService.getByKey(AdiConstant.SysConfigKey.DEFAULT_LOCALE), "zh-CN");
            String processedPrompt = PromptUtil.createPrompt(
                    request.getInputText(), memoryAndKnowledge.getLeft(), memoryAndKnowledge.getRight(), "", effectiveLocale);

            // 5. Build request params (system message + memory + MCP tools + thinking + web search)
            ChatModelRequest chatRequestParams = CharacterChatHelper.buildChatRequestParams(
                    character, processedPrompt, user, llmService, request.isEnableMcp(), request.isEnableWebSearch(), null);
            boolean toolsAttached = request.isEnableMcp() || request.isEnableWebSearch();
            if (toolsAttached) {
                spans.add(buildSpan(traceId, "", "tools.attach", "tool", System.currentTimeMillis(),
                        detailOf().put("mcpEnabled", request.isEnableMcp())
                                .put("webSearchEnabled", request.isEnableWebSearch())));
            }

            // 6. Blocking LLM call
            SseAskParam sseAskParam = new SseAskParam();
            sseAskParam.setUser(user);
            sseAskParam.setUuid(uuid);
            sseAskParam.setModelName(llmService.getAiModel().getName());
            sseAskParam.setHttpRequestParams(chatRequestParams);
            sseAskParam.setModelProperties(
                    ChatModelBuilderProperties.builder()
                            .temperature(character.getLlmTemperature())
                            .returnThinking(chatRequestParams.getReturnThinking())
                            .build()
            );

            long llmStart = System.currentTimeMillis();
            ChatResponse chatResponse = llmService.chat(sseAskParam);
            long llmDuration = System.currentTimeMillis() - llmStart;

            // 7. Build result
            Integer inputTokens = null;
            Integer outputTokens = null;
            if (chatResponse.metadata() != null && chatResponse.metadata().tokenUsage() != null) {
                inputTokens = chatResponse.metadata().tokenUsage().inputTokenCount();
                outputTokens = chatResponse.metadata().tokenUsage().outputTokenCount();
            }

            spans.add(buildSpan(traceId, "", "llm.chat", "llm", llmStart,
                    detailOf().put("modelName", llmService.getAiModel().getName())
                            .put("inputTokens", inputTokens == null ? 0 : inputTokens)
                            .put("outputTokens", outputTokens == null ? 0 : outputTokens)
                            .put("durationMs", llmDuration)));

            spans.add(buildSpan(traceId, "", "agent.reply", "other", llmStart + llmDuration,
                    detailOf().put("answerLength",
                            chatResponse.aiMessage() == null || chatResponse.aiMessage().text() == null
                                    ? 0 : chatResponse.aiMessage().text().length())));

            // 8. T6-14：补全 AgentMetrics 指标（由 AgentNode 等调用方兜底消费）
            int totalTokens = (inputTokens == null ? 0 : inputTokens) + (outputTokens == null ? 0 : outputTokens);
            recordAgentCall(character, llmService, inputTokens, outputTokens, totalTokens,
                    (int) (System.currentTimeMillis() - invokeStart), requestTime, user, traceId);

            return AgentResult.builder()
                    .answer(chatResponse.aiMessage().text())
                    .thinking(chatResponse.aiMessage().thinking())
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .retrievalCount(retrievalCount)
                    .llmCallCount(1)
                    .totalTokens(totalTokens)
                    .toolCallCount(toolsAttached ? 1 : 0)
                    .llmDurationMs(llmDuration)
                    .degradedCount(0)
                    .budgetAbortCount(0)
                    .build();
        } finally {
            // entry span 首个插入，时间线上排在最前
            spans.add(0, buildSpan(traceId, "", "agent.invoke", "entry", invokeStart,
                    detailOf().put("characterUuid", request.getCharacterUuid())
                            .put("durationMs", System.currentTimeMillis() - invokeStart)));
            agentTraceSpanService.saveAsyncAll(spans);
            TraceUtil.clearIfCreated(traceCreated);
        }
    }

    /**
     * T6-13：写 Agent 渠道 LLM 调用记录（sourceType=agent，sourceAgent=角色名）。
     * 受 sys_config「llm_cost_record_enabled」开关控制（铁律 10，缺省开，可热回滚）；
     * 失败只 warn，不影响主流程。
     */
    private void recordAgentCall(Character character, AbstractLLMService llmService,
                                 Integer inputTokens, Integer outputTokens, int totalTokens,
                                 int durationMs, LocalDateTime requestTime, User user, String traceId) {
        try {
            String enabled = SysConfigService.getByKey(AdiConstant.SysConfigKey.LLM_COST_RECORD_ENABLED);
            if ("false".equalsIgnoreCase(StringUtils.trimToEmpty(enabled))) {
                return;
            }
            LLMCallRecord record = new LLMCallRecord();
            record.setUuid(UuidUtil.createShort());
            record.setSourceType(com.moyz.adi.common.enums.LLMCallRecordSourceType.AGENT.getValue());
            record.setSourceId(character.getId());
            record.setSourceAgent(StringUtils.defaultIfBlank(character.getTitle(), character.getUuid()));
            record.setTraceId(traceId);
            record.setUserId(user.getId());
            record.setModelPlatform(llmService.getAiModel().getPlatform());
            record.setModelName(llmService.getAiModel().getName());
            record.setInputTokens(inputTokens == null ? 0 : inputTokens);
            record.setOutputTokens(outputTokens == null ? 0 : outputTokens);
            record.setDuration(durationMs);
            record.setRequestTime(requestTime);
            llmCallRecordService.saveAsync(record);
            log.debug("agent LLM call recorded, character:{}, totalTokens:{}", character.getUuid(), totalTokens);
        } catch (Exception e) {
            log.warn("recordAgentCall failed, character:{}, error:{}", character.getUuid(), e.getMessage());
        }
    }

    private static ObjectNode detailOf() {
        return JsonUtil.getObjectMapper().createObjectNode();
    }

    private static AgentTraceSpan buildSpan(String traceId, String parentSpanId, String name, String type,
                                            long startMs, ObjectNode detail) {
        AgentTraceSpan span = new AgentTraceSpan();
        span.setTraceId(traceId);
        span.setSpanId(UuidUtil.createShort());
        span.setParentSpanId(StringUtils.defaultString(parentSpanId));
        span.setName(name);
        span.setSpanType(type);
        span.setStatus("OK");
        span.setStartTime(new java.sql.Timestamp(startMs).toLocalDateTime());
        span.setEndTime(new java.sql.Timestamp(System.currentTimeMillis()).toLocalDateTime());
        span.setDurationMs(Math.max(0, System.currentTimeMillis() - startMs));
        span.setDetail(detail);
        return span;
    }
}
