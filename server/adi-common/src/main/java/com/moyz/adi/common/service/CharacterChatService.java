package com.moyz.adi.common.service;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.dto.AskReq;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.dto.RefEmbeddingDto;
import com.moyz.adi.common.dto.RefGraphDto;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.CharacterMessage;
import com.moyz.adi.common.entity.LLMCallRecord;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ChatMessageRoleEnum;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.enums.LLMCallRecordSourceType;
import com.moyz.adi.common.enums.MemoryType;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.file.LocalFileUtil;
import com.moyz.adi.common.helper.AsrModelContext;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.helper.QuotaHelper;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.languagemodel.data.LLMResponseContent;
import com.moyz.adi.common.memory.longterm.LongTermMemoryService;
import com.moyz.adi.common.memory.shortterm.MapDBChatMemoryStore;
import com.moyz.adi.common.memory.vo.MemoryAddParam;
import com.moyz.adi.common.rag.AdiEmbeddingStoreContentRetriever;
import com.moyz.adi.common.rag.GraphStoreContentRetriever;
import com.moyz.adi.common.service.DocumentSegmentService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.service.embedding.ICharacterMemoryEmbeddingService;
import com.moyz.adi.common.service.embedding.IEpisodicMemoryEmbeddingService;
import com.moyz.adi.common.util.*;
import com.moyz.adi.common.util.NumberUtil;
import com.moyz.adi.common.vo.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import ws.schild.jave.info.MultimediaInfo;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.moyz.adi.common.cosntant.AdiConstant.*;
import static com.moyz.adi.common.enums.ErrorEnum.A_CHARACTER_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_USER_NOT_AUTH;
import static com.moyz.adi.common.enums.ErrorEnum.B_MESSAGE_NOT_FOUND;

/**
 * Chat orchestration service: orchestrates the full SSE/blocking chat pipeline.
 *
 * <p>Agent = Character in execution. This service coordinates:
 * AgentService (LLM pipeline), CharacterService (Character data), CharacterMessageService (message CRUD).</p>
 *
 * <p>Dependency direction: CharacterChatService → AgentService → CharacterService → CharacterMessageService</p>
 */
@Slf4j
@Service
public class CharacterChatService {

    @Lazy
    @Resource
    private CharacterChatService self;

    @Resource
    private CharacterService characterService;

    @Resource
    private CharacterMessageService characterMessageService;

    @Resource
    private AgentService agentService;

    @Resource
    private QuotaHelper quotaHelper;

    @Resource
    private UserDayCostService userDayCostService;

    @Resource
    private SseManager sseManager;

    @Resource
    private FileService fileService;

    @Resource
    private AsyncTaskExecutor mainExecutor;

    @Resource
    private LongTermMemoryService longTermMemoryService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LLMCallRecordService llmCallRecordService;

    @Resource
    private ICharacterMemoryEmbeddingService characterMemoryEmbeddingService;

    @Resource
    private IEpisodicMemoryEmbeddingService episodicMemoryEmbeddingService;

    @Resource
    private DocumentSegmentService documentSegmentService;

    @Resource
    private KbDocumentService kbDocumentService;

    public SseEmitter sseAsk(AskReq askReq) {
        String sseUuid = UuidUtil.createShort();
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT);
        User user = ThreadContext.getCurrentUser();
        if (!sseManager.checkOrComplete(user, sseUuid, sseEmitter)) {
            return sseEmitter;
        }
        sseManager.startSse(user, sseUuid, sseEmitter, null);
        self.asyncCheckAndChat(sseUuid, user, askReq);
        return sseEmitter;
    }

    /**
     * Blocking chat: calls AgentService for the core pipeline, then persists messages to DB.
     */
    public Map<String, Object> blockingAsk(AskReq askReq) {
        User user = ThreadContext.getExistCurrentUser();

        // Validate character exists
        Character character = characterService.lambdaQuery()
                .eq(Character::getUuid, askReq.getCharacterUuid())
                .eq(Character::getIsDeleted, false)
                .oneOpt()
                .orElseThrow(() -> new BaseException(A_CHARACTER_NOT_FOUND));

        // Quota check
        AiModel aiModel = LLMContext.getAiModel(askReq.getModelPlatform(), askReq.getModelName());
        if (null != aiModel && !aiModel.getIsFree()) {
            ErrorEnum quotaError = quotaHelper.checkTextQuota(user);
            if (null != quotaError) {
                throw new BaseException(quotaError);
            }
        }

        // Build generic request and invoke via AgentService
        String questionUuid = UuidUtil.createShort();
        AgentRequest request = AgentRequest.builder()
                .characterUuid(askReq.getCharacterUuid())
                .modelPlatform(askReq.getModelPlatform())
                .modelName(askReq.getModelName())
                .inputText(askReq.getPrompt())
                .enableRag(true)
                .enableMcp(true)
                .enableWebSearch(true)
                .build();
        long llmStartTime = System.currentTimeMillis();
        AgentResult result = agentService.invoke(request, user, questionUuid);
        int llmDuration = NumberUtil.saturatedCastToInt(System.currentTimeMillis() - llmStartTime);

        // Persist messages to DB
        PromptMeta questionMeta = new PromptMeta(
                result.getInputTokens() != null ? result.getInputTokens() : 0, questionUuid);
        AnswerMeta answerMeta = AnswerMeta.builder()
                .inputTokens(result.getInputTokens() != null ? result.getInputTokens() : 0)
                .outputTokens(result.getOutputTokens() != null ? result.getOutputTokens() : 0)
                .duration(llmDuration)
                .uuid(UuidUtil.createShort())
                .build();
        LLMResponseContent responseContent = new LLMResponseContent(
                result.getThinking(), result.getAnswer(), null);
        self.saveAfterAiResponse(user, askReq, new ArrayList<>(), responseContent, questionMeta, answerMeta, null);

        // Build response
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", questionUuid);
        data.put("answer", result.getAnswer());
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("prompt_tokens", result.getInputTokens() != null ? result.getInputTokens() : 0);
        usage.put("completion_tokens", result.getOutputTokens() != null ? result.getOutputTokens() : 0);
        usage.put("total_tokens", (result.getInputTokens() != null ? result.getInputTokens() : 0)
                + (result.getOutputTokens() != null ? result.getOutputTokens() : 0));
        data.put("usage", usage);
        return data;
    }

    private boolean checkCharacter(String sseUuid, User user, AskReq askReq) {
        try {

            //check 1: the character has been deleted
            Character delConv = characterService.lambdaQuery()
                    .eq(Character::getUuid, askReq.getCharacterUuid())
                    .eq(Character::getIsDeleted, true)
                    .one();
            if (null != delConv) {
                sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage("A_CHARACTER_DELETED"));
                return false;
            }

            //check 1b: 会话归属。会话 uuid 不存在是合法的（首条消息会创建会话），
            //但一旦存在就必须属于当前用户——否则任何人都能拿别人的会话 uuid 发起对话，
            //按对方会话关联的知识库/记忆去检索并拿到回答。
            // Conversation ownership: a missing uuid is legitimate (the first message creates the
            // conversation), but an existing one must belong to the caller — otherwise anyone can
            // start a chat against someone else's conversation and have it retrieve against that
            // conversation's knowledge bases and memory.
            Character existConv = characterService.lambdaQuery()
                    .eq(Character::getUuid, askReq.getCharacterUuid())
                    .eq(Character::getIsDeleted, false)
                    .oneOpt()
                    .orElse(null);
            if (null != existConv
                    && !existConv.getUserId().equals(user.getId())
                    && !Boolean.TRUE.equals(user.getIsAdmin())) {
                sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage(A_USER_NOT_AUTH.getInfo()));
                return false;
            }

            //check 2: character quota
            Long convsCount = characterService.lambdaQuery()
                    .eq(Character::getUserId, user.getId())
                    .eq(Character::getIsDeleted, false)
                    .count();
            long convsMax = Integer.parseInt(LocalCache.CONFIGS.get(AdiConstant.SysConfigKey.CHARACTER_MAX_NUM));
            if (convsCount >= convsMax) {
                sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage("A_CHARACTER_MAX_LIMIT", String.valueOf(convsMax)));
                return false;
            }

            //check 3: current user's quota
            AiModel aiModel = LLMContext.getAiModel(askReq.getModelPlatform(), askReq.getModelName());
            if (null != aiModel && !aiModel.getIsFree()) {
                ErrorEnum errorMsg = quotaHelper.checkTextQuota(user);
                if (null != errorMsg) {
                    sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage(errorMsg.getInfo()));
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("error", e);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, e.getMessage());
            return false;
        }
        return true;
    }

    @Async
    public void asyncCheckAndChat(String sseUuid, User user, AskReq askReq) {
        // 整个方法体都必须被兜住。本方法是 @Async，方法内抛出的异常不会回灌到 SSE 流：
        // 客户端既收不到 [ERROR] 也收不到 [DONE]（前端状态永远停在「回复中」），
        // 且 unregister 不会被调用，Redis 并发计数 user:active-sse:{id} 就此泄漏；
        // Web 端并发上限默认 1，于是该用户之后每一次发消息都被判「正在回复中...」，
        // 表现为「聊天突然整体不通」，直到 emitter 超时才恢复。
        // The whole body must be guarded: this is an @Async method, so an exception thrown inside
        // never reaches the SSE stream — the client sees neither [ERROR] nor [DONE] (its state stays
        // stuck on "replying"), and unregister never runs, leaking Redis counter user:active-sse:{id}.
        // With the web concurrency limit of 1, every later message from that user is then rejected
        // with "SSE_RESPONDING" until the emitter times out.
        try {
            doAsyncCheckAndChat(sseUuid, user, askReq);
        } catch (Exception e) {
            log.error("asyncCheckAndChat failed, userId:{}, sseUuid:{}", user.getId(), sseUuid, e);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid,
                    StringUtils.defaultIfBlank(e.getMessage(),
                            SpringUtil.getMessage(ErrorEnum.B_UNCAUGHT_ERROR.getInfo())));
        }
    }

    private void doAsyncCheckAndChat(String sseUuid, User user, AskReq askReq) {
        log.info("asyncCheckAndChat,userId:{}", user.getId());
        //check business rules
        if (!checkCharacter(sseUuid, user, askReq)) {
            return;
        }
        // 契约：prompt 为空且带 regenerateQuestionUuid 时，用原问题重放。
        // 早期实现没有这一步，直接把 null prompt 交给 PromptUtil.createPrompt，
        // 抛出 B_MESSAGE_NOT_FOUND（用户问题不存在），「重新生成」因此从不返回结果。
        // Contract: when prompt is blank but regenerateQuestionUuid is present, replay the original
        // question. The earlier implementation skipped this and handed a null prompt to
        // PromptUtil.createPrompt, which threw B_MESSAGE_NOT_FOUND — regenerate never produced output.
        resolveRegeneratePrompt(user, askReq);
        //questions
        //system message
        Character character = characterService.lambdaQuery()
                .eq(Character::getUuid, askReq.getCharacterUuid())
                .oneOpt()
                .orElse(null);
        if (null == character) {
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage(A_CHARACTER_NOT_FOUND.getInfo()));
            return;
        }

        //Send analysing question event to client
        SseManager.sendPartial(sseUuid, SSEEventName.STATE_CHANGED, SSEEventData.STATE_QUESTION_ANALYSING);
//If voice input, convert audio to text
        //如果是语音输入，将音频转成文本
        if (StringUtils.isNotBlank(askReq.getAudioUuid())) {
            String path = fileService.getImagePath(askReq.getAudioUuid());
            String audioText = new AsrModelContext().audioToText(path);
            if (StringUtils.isBlank(audioText)) {
                sseManager.sendErrorAndComplete(user.getId(), sseUuid, SpringUtil.getMessage("B_ASR_PARSE_FAIL"));
                return;
            }
            askReq.setPrompt(audioText);
        }

        AbstractLLMService llmService = LLMContext.getServiceOrDefault(askReq.getModelPlatform(), askReq.getModelName());

//If knowledge bases are associated, filter valid ones for subsequent queries
        //如果关联了知识库，筛选出有效的知识库以待后续查询，同时发送搜索知识库事件给前端用户
        List<KbInfoResp> filteredKb = new ArrayList<>();
        if (StringUtils.isNotBlank(character.getKbIds())) {
            List<Long> kbIds = Arrays.stream(character.getKbIds().split(",")).map(Long::parseLong).toList();
            // 二开（2026-09-21）：is_retrieval_enabled=false 的库不参与对话自动检索（文档仍可在管理端维护；
            // ext API 显式指定 kbUuid 的调用不受此开关影响）。Boolean.FALSE.equals 兼容历史行的 null/true。
            filteredKb.addAll(characterService.filterEnableKb(user, kbIds).stream()
                    .filter(kb -> !Boolean.FALSE.equals(kb.getIsRetrievalEnabled()))
                    .toList());

            //Send searching knowledge event to user
            SseManager.sendPartial(sseUuid, SSEEventName.STATE_CHANGED, SSEEventData.STATE_KNOWLEDGE_SEARCHING);
        }
        //Retrieve contents from knowledge base and character memory
        List<RetrieverWrapper> retrieverWrappers = CharacterChatHelper.retrieve(character.getId(), filteredKb, llmService, askReq.getPrompt(), mainExecutor);

        // Process prompt with retrieved contents and audio settings
        int answerContentType = getAnswerContentType(character, askReq);
        boolean answerToAudio = TtsUtil.needTts(llmService.getTtsSetting(), answerContentType);
        String effectiveLocale = StringUtils.isNotBlank(user.getLocale())
                ? user.getLocale()
                : Objects.toString(SysConfigService.getByKey(AdiConstant.SysConfigKey.DEFAULT_LOCALE), "zh-CN");
        Pair<String, String> memoryAndKnowledge = CharacterChatHelper.buildMemoryAndKnowledge(retrieverWrappers);
        String audioExtra = answerToAudio ? (effectiveLocale.startsWith("zh") ? PROMPT_EXTRA_AUDIO : PROMPT_EXTRA_AUDIO_EN) : "";
        String processedPrompt = PromptUtil.createPrompt(askReq.getPrompt(), memoryAndKnowledge.getLeft(), memoryAndKnowledge.getRight(), audioExtra, effectiveLocale);
        if (!Objects.equals(askReq.getPrompt(), processedPrompt)) {
            askReq.setProcessedPrompt(processedPrompt);
        }

        String questionUuid = StringUtils.isNotBlank(askReq.getRegenerateQuestionUuid()) ? askReq.getRegenerateQuestionUuid() : UuidUtil.createShort();
        SseAskParam sseAskParam = new SseAskParam();
        sseAskParam.setUser(user);
        sseAskParam.setUuid(questionUuid);
        sseAskParam.setModelName(askReq.getModelName());
        sseAskParam.setSseUuid(sseUuid);
        sseAskParam.setRegenerateQuestionUuid(askReq.getRegenerateQuestionUuid());
        sseAskParam.setAnswerContentType(answerContentType);
        if (null != character.getAudioConfig() && null != character.getAudioConfig().getVoice()) {
//If character has voice configured, use character voice settings
            //如果对话配置了语音，则使用对话的语音配置
            sseAskParam.setVoice(character.getAudioConfig().getVoice().getParamName());
        }

        ChatModelRequest chatRequestParams = CharacterChatHelper.buildChatRequestParams(character, askReq.getProcessedPrompt() != null ? askReq.getProcessedPrompt() : askReq.getPrompt(), user, llmService, true, Boolean.TRUE.equals(character.getIsEnableWebSearch()), askReq.getImageUrls());
        sseAskParam.setHttpRequestParams(chatRequestParams);

        sseAskParam.setModelProperties(
                ChatModelBuilderProperties.builder()
                        .temperature(character.getLlmTemperature())
                        .returnThinking(chatRequestParams.getReturnThinking())
                        .build()
        );
        try {
            long llmStartTime = System.currentTimeMillis();
            sseManager.call(sseAskParam, (response, questionMeta, answerMeta) -> {
                answerMeta.setDuration(NumberUtil.saturatedCastToInt(System.currentTimeMillis() - llmStartTime));

                AudioInfo audioInfo = null;
                if (StringUtils.isNotBlank(response.getAudioPath())) {

                    audioInfo = new AudioInfo();
                    MultimediaInfo multimediaInfo = LocalFileUtil.getAudioFileInfo(response.getAudioPath());
                    if (null != multimediaInfo) {
                        audioInfo.setDuration((int) multimediaInfo.getDuration() / 1000);
                    }
                    audioInfo.setPath(response.getAudioPath());
                    //存储到数据库并返回真实的URL
                    AdiFile adiFile = fileService.saveFromPath(user, response.getAudioPath());
                    audioInfo.setUuid(adiFile.getUuid());
                    audioInfo.setUrl(FileOperatorContext.getFileUrl(adiFile));
                }
                boolean isRefEmbedding = false;
                boolean isRefGraph = false;
                boolean isRefMemoryEmbedding = false;
                List<KbRefItem> kbRefs = new ArrayList<>();
                for (RetrieverWrapper wrapper : retrieverWrappers) {
                    if (RetrieveContentFrom.KNOWLEDGE_BASE.equals(wrapper.getContentFrom())) {
                        if (wrapper.getRetriever() instanceof AdiEmbeddingStoreContentRetriever embeddingStoreContentRetriever) {
                            isRefEmbedding = !embeddingStoreContentRetriever.getRetrievedEmbeddingToScore().isEmpty();
                        } else if (wrapper.getRetriever() instanceof GraphStoreContentRetriever graphStoreContentRetriever) {
                            RefGraphDto graphDto = graphStoreContentRetriever.getGraphRef();
                            isRefGraph = !graphDto.getVertices().isEmpty() || !graphDto.getEdges().isEmpty();
                        }
                    } else if (RetrieveContentFrom.CHARACTER_MEMORY.equals(wrapper.getContentFrom())
                            || RetrieveContentFrom.CHARACTER_MEMORY_EPISODIC.equals(wrapper.getContentFrom())) {
                        //目前记忆相关内容只使用向量存储，后续如果增加了其他类型的记忆存储，也可以在这里增加判断
                        //semantic 和 episodic 都聚合到 isRefMemoryEmbedding 这个总开关下
                        //<p>
                        //Both semantic and episodic memory retrievers feed into the unified isRefMemoryEmbedding flag.
                        if (wrapper.getRetriever() instanceof AdiEmbeddingStoreContentRetriever embeddingStoreContentRetriever) {
                            isRefMemoryEmbedding = isRefMemoryEmbedding
                                    || !embeddingStoreContentRetriever.getRetrievedEmbeddingToScore().isEmpty();
                        }
                    }
                }
                // 二开（2026-09-21）：把知识库命中明细随 [META] 下发，前端渲染"来源"区块
                if (isRefEmbedding || isRefGraph) {
                    try {
                        kbRefs = buildKbRefs(retrieverWrappers, filteredKb);
                    } catch (Exception e) {
                        // 引用明细是锦上添花，任何失败都不应影响对话完成
                        log.warn("buildKbRefs failed, characterUuid:{}", askReq.getCharacterUuid(), e);
                    }
                }
                answerMeta.setIsRefEmbedding(isRefEmbedding);
                answerMeta.setIsRefGraph(isRefGraph);
                answerMeta.setIsRefMemoryEmbedding(isRefMemoryEmbedding);
                answerMeta.setRefs(kbRefs);
                sseManager.sendComplete(user.getId(), sseUuid, questionMeta, answerMeta, audioInfo);
                self.saveAfterAiResponse(user, askReq, retrieverWrappers, response, questionMeta, answerMeta, audioInfo);
            });
        } catch (Exception e) {
            log.error("Chat pipeline failed, userId:{}, sseUuid:{}", user.getId(), sseUuid, e);
            sseManager.sendErrorAndComplete(user.getId(), sseUuid, e.getMessage());
        }
    }

    /**
     * 二开新增（2026-09-21）：组装知识库引用明细，随 [META] 下发给前端。
     * 向量路：按命中 embeddingId 反查段内容（含 qa 问题→答案 / 子块→父段传导），附文档与知识库名称；
     * 图谱路：取顶点名称及其元数据中的来源文档。仅取 top5，内容截断，纯展示用途不影响主流程。
     */
    private List<KbRefItem> buildKbRefs(List<RetrieverWrapper> retrieverWrappers, List<KbInfoResp> filteredKb) {
        List<KbRefItem> refs = new ArrayList<>();
        Map<String, Double> mergedScores = new HashMap<>();
        List<RefGraphDto> graphDtos = new ArrayList<>();
        for (RetrieverWrapper wrapper : retrieverWrappers) {
            if (!RetrieveContentFrom.KNOWLEDGE_BASE.equals(wrapper.getContentFrom())) {
                continue;
            }
            if (wrapper.getRetriever() instanceof AdiEmbeddingStoreContentRetriever embeddingRetriever) {
                embeddingRetriever.getRetrievedEmbeddingToScore().forEach((id, score) ->
                        mergedScores.merge(id, score, Math::max));
            } else if (wrapper.getRetriever() instanceof GraphStoreContentRetriever graphRetriever) {
                graphDtos.add(graphRetriever.getGraphRef());
            }
        }
        if (!mergedScores.isEmpty()) {
            List<String> topIds = mergedScores.entrySet().stream()
                    .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .toList();
            Map<String, Double> topScores = topIds.stream()
                    .collect(Collectors.toMap(Function.identity(), mergedScores::get, (a, b) -> a));
            List<RefEmbeddingDto> dtos = documentSegmentService.listRefTextsByEmbeddingIds(topIds);
            Set<String> docUuids = dtos.stream()
                    .map(RefEmbeddingDto::getDocUuid)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            Map<String, String> docTitles = docUuids.isEmpty() ? Map.of()
                    : kbDocumentService.lambdaQuery()
                    .in(KbDocument::getUuid, docUuids)
                    .eq(KbDocument::getIsDeleted, false)
                    .list().stream()
                    .collect(Collectors.toMap(KbDocument::getUuid, KbDocument::getTitle, (a, b) -> a));
            Map<String, String> kbTitles = filteredKb.stream()
                    .collect(Collectors.toMap(KbInfoResp::getUuid, KbInfoResp::getTitle, (a, b) -> a));
            for (RefEmbeddingDto dto : dtos) {
                KbRefItem ref = new KbRefItem();
                ref.setSource("embedding");
                ref.setScore(topScores.get(dto.getEmbeddingId()));
                // 展示优先：qa 模式给命中的问题文本，parent_child 给命中的子块，text 就是段本身
                String display = StringUtils.defaultIfBlank(dto.getMatchedText(), dto.getText());
                ref.setSegmentContent(abbreviate(display, 200));
                ref.setDocTitle(docTitles.getOrDefault(dto.getDocUuid(), ""));
                ref.setKbTitle(kbTitles.getOrDefault(dto.getKbUuid(), ""));
                refs.add(ref);
            }
        }
        for (RefGraphDto graphDto : graphDtos) {
            if (null == graphDto || CollectionUtils.isEmpty(graphDto.getVertices())) {
                continue;
            }
            for (GraphVertex vertex : graphDto.getVertices()) {
                KbRefItem ref = new KbRefItem();
                ref.setSource("graph");
                ref.setDocTitle(abbreviate(vertex.getName(), 100));
                ref.setSegmentContent(abbreviate(Objects.toString(vertex.getDescription(), ""), 200));
                refs.add(ref);
                if (refs.size() >= 8) {
                    return refs;
                }
            }
        }
        return refs;
    }

    private static String abbreviate(String text, int max) {
        if (null == text) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    @Transactional
    public void saveAfterAiResponse(User user, AskReq askReq, List<RetrieverWrapper> retrievers, LLMResponseContent response, PromptMeta questionMeta, AnswerMeta answerMeta, AudioInfo audioInfo) {
        Character character;
        String prompt = askReq.getPrompt();
        String characterUuid = askReq.getCharacterUuid();
        String modelPlatform = askReq.getModelPlatform();
        String modelName = askReq.getModelName();
        character = characterService.lambdaQuery()
                .eq(Character::getUuid, characterUuid)
                .eq(Character::getUserId, user.getId())
                .oneOpt()
                .orElseGet(() -> characterService.createByFirstMessage(user.getId(), characterUuid, prompt));
        AiModel aiModel = LLMContext.getAiModel(modelPlatform, modelName);

        //Check if regenerate question
        CharacterMessage promptMsg;
        if (StringUtils.isNotBlank(askReq.getRegenerateQuestionUuid())) {
            promptMsg = getPromptMsgByQuestionUuid(askReq.getRegenerateQuestionUuid());
        } else {
            //Save new question message
            CharacterMessage question = new CharacterMessage();
            question.setUserId(user.getId());
            question.setUuid(questionMeta.getUuid());
            question.setCharacterId(character.getId());
            question.setCharacterUuid(characterUuid);
            question.setMessageRole(ChatMessageRoleEnum.USER.getValue());
            question.setRemark(prompt);
            question.setProcessedRemark(askReq.getProcessedPrompt());
            question.setAiModelId(aiModel.getId());
            question.setAudioUuid(askReq.getAudioUuid());
            question.setAudioDuration(askReq.getAudioDuration());
            question.setAttachments(String.join(",", askReq.getImageUrls()));
            characterMessageService.save(question);

            promptMsg = characterMessageService.lambdaQuery().eq(CharacterMessage::getUuid, questionMeta.getUuid()).one();

        }

        //save response message
        CharacterMessage aiAnswer = new CharacterMessage();
        aiAnswer.setUserId(user.getId());
        aiAnswer.setUuid(answerMeta.getUuid());
        aiAnswer.setCharacterId(character.getId());
        aiAnswer.setCharacterUuid(characterUuid);
        aiAnswer.setMessageRole(ChatMessageRoleEnum.ASSISTANT.getValue());
        aiAnswer.setThinkingContent(Objects.toString(response.getThinkingContent(), ""));
        aiAnswer.setRemark(response.getContent());
        //TODO: If AI response content is non-compliant, store filtered version in processedRemark; frontend should prefer processedRemark over remark
        //aiAnswer.setProcessedRemark(filteredContent);
        aiAnswer.setAudioUuid(null == audioInfo ? "" : Objects.toString(audioInfo.getUuid(), ""));
        aiAnswer.setAudioDuration(null == audioInfo ? 0 : audioInfo.getDuration());
        aiAnswer.setParentMessageId(promptMsg.getId());
        aiAnswer.setAiModelId(aiModel.getId());
        aiAnswer.setIsRefEmbedding(answerMeta.getIsRefEmbedding());
        aiAnswer.setIsRefGraph(answerMeta.getIsRefGraph());
        aiAnswer.setIsRefMemoryEmbedding(answerMeta.getIsRefMemoryEmbedding());
        int answerContentType = getAnswerContentType(character, askReq);
        aiAnswer.setContentType(answerContentType);
        characterMessageService.save(aiAnswer);

        //Aggregate token usage across all LLM calls in this request (streaming chat may
        //recurse through multiple tool calls, each cached under the same uuid in Redis).
        Pair<Integer, Integer> tokenCost = LLMTokenUtil.calAllTokenCostByUuid(stringRedisTemplate, questionMeta.getUuid());
        int totalInputTokens = tokenCost.getLeft();
        int totalOutputTokens = tokenCost.getRight();

        //Save LLM call record
        LLMCallRecord callRecord = new LLMCallRecord();
        callRecord.setUuid(UuidUtil.createShort());
        callRecord.setSourceType(LLMCallRecordSourceType.CHARACTER_CHAT.getValue());
        callRecord.setSourceId(aiAnswer.getId());
        callRecord.setUserId(user.getId());
        callRecord.setModelPlatform(modelPlatform);
        callRecord.setModelName(modelName);
        callRecord.setInputTokens(totalInputTokens);
        callRecord.setOutputTokens(totalOutputTokens);
        callRecord.setDuration(answerMeta.getDuration());
        llmCallRecordService.saveAsync(callRecord);

        createRef(retrievers, user, aiAnswer.getId());

        calcTodayCost(user, totalInputTokens + totalOutputTokens, aiModel.getIsFree());

        //Short-term memory
        if (Boolean.TRUE.equals(character.getUnderstandContextEnable())) {
            MapDBChatMemoryStore mapDBChatMemoryStore = MapDBChatMemoryStore.getSingleton();
            List<ChatMessage> messages = mapDBChatMemoryStore.getMessages(askReq.getCharacterUuid());
            List<ChatMessage> newMessages = new ArrayList<>(messages);
            // reasoning_content is stored in AiMessage.thinking() via returnThinking(true),
            // and will be sent back to DeepSeek API via sendThinking(true) during multi-turn tool-call scenarios.
            // <p>
            // reasoning_content 通过 returnThinking(true) 存入 AiMessage.thinking()，
            // 并在多轮工具调用场景中通过 sendThinking(true) 自动回传 DeepSeek API。
            newMessages.add(AiMessage.builder().text(response.getContent()).thinking(response.getThinkingContent()).build());
            mapDBChatMemoryStore.updateMessages(askReq.getCharacterUuid(), newMessages);
        }

        // Prefer the current model for long-term memory; fall back to getFirstEnableAndFree()
        // if the current model does not support JSON structured output (e.g., vision models).
        // <p>
        // 长期记忆优先使用当前模型；如果当前模型不支持 JSON 结构化输出（如视觉模型），则降级到 getFirstEnableAndFree()。
        String memoryPlatform = modelPlatform;
        String memoryModelName = modelName;
        boolean memoryIsFreeToken = aiModel.getIsFree();
        boolean supportsJsonOutput = aiModel.getResponseFormatTypes() != null
                && aiModel.getResponseFormatTypes().contains("json_object");
        if (!supportsJsonOutput) {
            Optional<AbstractLLMService> fallback = LLMContext.getFirstEnableAndFree();
            if (fallback.isEmpty()) {
                log.warn("No available model supports JSON output, skipping long-term memory for characterId:{}", character.getId());
            } else {
                memoryPlatform = fallback.get().getPlatform().getName();
                memoryModelName = fallback.get().getAiModel().getName();
                memoryIsFreeToken = fallback.get().getAiModel().getIsFree();
                log.info("Current model {} does not support JSON output, using fallback model {} for long-term memory", modelName, memoryModelName);
                longTermMemoryService.asyncAdd(MemoryAddParam.builder()
                        .characterId(character.getId())
                        .modelPlatform(memoryPlatform)
                        .modelName(memoryModelName)
                        .userMessage(askReq.getPrompt())
                        .assistantMessage(response.getContent())
                        .user(user)
                        .isFreeToken(memoryIsFreeToken)
                        .sourceMsgId(aiAnswer.getId())
                        .build());
            }
        } else {
            longTermMemoryService.asyncAdd(MemoryAddParam.builder()
                    .characterId(character.getId())
                    .modelPlatform(memoryPlatform)
                    .modelName(memoryModelName)
                    .userMessage(askReq.getPrompt())
                    .assistantMessage(response.getContent())
                    .user(user)
                    .isFreeToken(memoryIsFreeToken)
                    .sourceMsgId(aiAnswer.getId())
                    .build());
        }
    }

    private void calcTodayCost(User user, int todayTokenCost, boolean isFreeToken) {
        try {
            // 用户级 token 累计；按 character 维度的 token 总量可在需要时从 llm_call_record 聚合得到。
            // <p>
            // User-level token accumulation. Character-level totals can be aggregated from
            // llm_call_record on demand.
            userDayCostService.appendCostToUser(user, todayTokenCost, isFreeToken);
        } catch (Exception e) {
            log.error("calcTodayCost error", e);
        }
    }

    private CharacterMessage getPromptMsgByQuestionUuid(String questionUuid) {
        return characterMessageService.lambdaQuery().eq(CharacterMessage::getUuid, questionUuid).oneOpt().orElseThrow(() -> new BaseException(B_MESSAGE_NOT_FOUND));
    }

    /**
     * 「重新生成」时把原问题的文本回填到 {@code askReq.prompt}。
     * <p>
     * 契约（{@link AskReq#getRegenerateQuestionUuid()}）：该字段非空时 {@code prompt} 被忽略，
     * 由后端按该 uuid 取回原始问题重放。缺失这一步会让后续的
     * {@code PromptUtil.createPrompt(null, ...)} 直接抛 B_MESSAGE_NOT_FOUND。
     * 这里同时校验消息归属，避免用他人的问题 uuid 触发重新生成。
     * </p>
     * <p>
     * Backfill the original question text when regenerating. Per the {@link AskReq} contract, a
     * non-blank {@code regenerateQuestionUuid} makes {@code prompt} irrelevant and the original
     * question must be reloaded by uuid; skipping that made {@code PromptUtil.createPrompt(null, ..)}
     * throw B_MESSAGE_NOT_FOUND. Ownership is validated here so another user's question uuid
     * cannot be replayed.
     * </p>
     */
    private void resolveRegeneratePrompt(User user, AskReq askReq) {
        if (StringUtils.isNotBlank(askReq.getPrompt()) || StringUtils.isBlank(askReq.getRegenerateQuestionUuid())) {
            return;
        }
        CharacterMessage origin = getPromptMsgByQuestionUuid(askReq.getRegenerateQuestionUuid());
        if (!origin.getUserId().equals(user.getId()) && !Boolean.TRUE.equals(user.getIsAdmin())) {
            throw new BaseException(A_USER_NOT_AUTH);
        }
        askReq.setPrompt(origin.getRemark());
    }

    private void createRef(List<RetrieverWrapper> wrappers, User user, Long msgId) {
        if (CollectionUtils.isEmpty(wrappers)) {
            return;
        }
        for (RetrieverWrapper wrapper : wrappers) {
            if (RetrieveContentFrom.KNOWLEDGE_BASE.equals(wrapper.getContentFrom())) {
                if (wrapper.getRetriever() instanceof AdiEmbeddingStoreContentRetriever embeddingRetriever) {
                    characterMessageService.createEmbeddingRefs(user, msgId, embeddingRetriever.getRetrievedEmbeddingToScore());
                } else if (wrapper.getRetriever() instanceof GraphStoreContentRetriever graphRetriever) {
                    characterMessageService.createGraphRefs(user, msgId, graphRetriever.getGraphRef());
                }
            } else if (RetrieveContentFrom.CHARACTER_MEMORY.equals(wrapper.getContentFrom())) {
                // Semantic memory channel: route writes through memory_type=SEMANTIC so
                // the reverse-lookup at read time can hit the right physical store in one shot.
                // <p>
                // 语义记忆通道：写入时附带 memory_type=SEMANTIC，反查时按 code 一次定位向量库。
                if (wrapper.getRetriever() instanceof AdiEmbeddingStoreContentRetriever memoryRetriever) {
                    Map<String, Double> embeddingToScore = memoryRetriever.getRetrievedEmbeddingToScore();
                    characterMessageService.createMemoryRefs(user, msgId, embeddingToScore, MemoryType.SEMANTIC);
                    // Increment segment-level hit count for admin observability
                    characterMemoryEmbeddingService.incrementHitCount(new ArrayList<>(embeddingToScore.keySet()));
                }
            } else if (RetrieveContentFrom.CHARACTER_MEMORY_EPISODIC.equals(wrapper.getContentFrom())) {
                // Episodic memory channel: same ref table, distinct memory_type code.
                // 情景记忆通道：共用 ref 表，写入时用不同的 memory_type 码。
                if (wrapper.getRetriever() instanceof AdiEmbeddingStoreContentRetriever memoryRetriever) {
                    Map<String, Double> embeddingToScore = memoryRetriever.getRetrievedEmbeddingToScore();
                    characterMessageService.createMemoryRefs(user, msgId, embeddingToScore, MemoryType.EPISODIC);
                    // Increment segment-level hit count for admin observability
                    episodicMemoryEmbeddingService.incrementHitCount(new ArrayList<>(embeddingToScore.keySet()));
                }
            }

        }
    }

    /**
     * 获取响应内容类型
     *
     * @param character 对话
     * @param askReq    请求参数
     * @return 响应内容类型
     */
    private int getAnswerContentType(Character character, AskReq askReq) {
        int answerContentType = character.getAnswerContentType();
        //If response content type is auto and user input is audio, set response content type to audio
        //如果设置了响应内容类型为自动，并且用户输入是音频，则响应内容类型设置为音频
        if (answerContentType == AdiConstant.CharacterConstant.ANSWER_CONTENT_TYPE_AUTO && StringUtils.isNotBlank(askReq.getAudioUuid())) {
            answerContentType = AdiConstant.CharacterConstant.ANSWER_CONTENT_TYPE_AUDIO;
        }
        return answerContentType;
    }
}
