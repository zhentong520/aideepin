package com.moyz.adi.common.util;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.MemoryType;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.rag.AdiEmbeddingStoreContentRetriever;
import com.moyz.adi.common.rag.CompositeRag;
import com.moyz.adi.common.rag.EmbeddingRag;
import com.moyz.adi.common.rag.EmbeddingRagContext;
import com.moyz.adi.common.rag.KbAclInfo;
import com.moyz.adi.common.service.SkillService;
import com.moyz.adi.common.service.UserMcpService;
import com.moyz.adi.common.interfaces.BizToolSpi;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import com.moyz.adi.common.util.AdiStringUtil;
import com.moyz.adi.common.vo.ChatModelBuilderProperties;
import com.moyz.adi.common.vo.ChatModelRequest;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import com.moyz.adi.common.vo.RetrieverContext;
import com.moyz.adi.common.vo.RetrieverWrapper;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.core.task.AsyncTaskExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;

import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.CHARACTER_ID;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.KB_UUID;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.MEMORY_TYPE;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.USER_ID;
import static com.moyz.adi.common.constant.KbRagConstant.RAG_RETRIEVE_MIN_SCORE_DEFAULT;

/**
 * Character 聊天辅助类
 * <p>
 * 从 {@link com.moyz.adi.common.service.CharacterMessageService} 提取的核心逻辑，
 * 供 Agent 节点和 Character 聊天服务共用。
 * </p>
 * <p>
 * Character chat helper — core logic extracted from CharacterMessageService,
 * shared between Agent node and Character chat service.
 * </p>
 */
@Slf4j
public class CharacterChatHelper {

    /** 启动期推送（ChatCoreStaticConfigInitializer）；null 时用户维度关闭（与旧行为一致的安全缺省） */
    private static volatile AdiProperties adiProperties;

    private static volatile UserMcpService userMcpService;

    private static volatile SkillToolSpi skillToolService;

    private static volatile SkillService skillService;

    /** T9-M2（2026-10-28）：业务工具插件（拔插式业务能力，如商城导购）；未启用插件时 prepare 返回空包 */
    private static volatile BizToolSpi bizToolService;

    /** ChatCoreStaticConfigInitializer 启动期调用 */
    public static void initConfig(AdiProperties props, UserMcpService userMcpSvc,
                                  SkillToolSpi skillToolSvc, SkillService skillSvc,
                                  BizToolSpi bizToolSvc) {
        adiProperties = props;
        userMcpService = userMcpSvc;
        skillToolService = skillToolSvc;
        skillService = skillSvc;
        bizToolService = bizToolSvc;
    }

    private CharacterChatHelper() {
    }

    /**
     * jsonb 可见组数组 → List&lt;String&gt;（组装 KbAclInfo 用，null/非数组安全）。
     */
    /**
     * Semantic memory recall filter (T7-07): {@code character_id + memory_type!=episodic}
     * plus the user-dimension leg when enabled. Pure function so the isolation
     * conditions are unit-testable without a Spring context.
     * <p>
     * 语义记忆召回过滤（T7-07）：character_id + memory_type!=episodic，
     * 开启用户维度时叠加 user_id 条件。纯函数便于无 Spring 上下文单测隔离条件。
     */
    static Filter memorySemanticFilter(Long characterId, Long userId, Filter semanticLeg) {
        Filter base = new And(new IsEqualTo(CHARACTER_ID, characterId), semanticLeg);
        if (null == userId) {
            return base;
        }
        return new And(base, new IsEqualTo(USER_ID, String.valueOf(userId)));
    }

    /**
     * Episodic memory recall filter (T7-07): {@code character_id} plus the
     * user-dimension leg when enabled.
     * <p>
     * 情景记忆召回过滤（T7-07）：character_id，开启用户维度时叠加 user_id 条件。
     */
    static Filter memoryCharacterFilter(Long characterId, Long userId) {
        if (null == userId) {
            return new IsEqualTo(CHARACTER_ID, characterId);
        }
        return new And(new IsEqualTo(CHARACTER_ID, characterId),
                new IsEqualTo(USER_ID, String.valueOf(userId)));
    }

    private static List<String> toGroupList(JsonNode groups) {
        List<String> result = new ArrayList<>();
        if (null != groups && groups.isArray()) {
            groups.forEach(node -> {
                String g = node.asText("");
                if (!g.isBlank()) {
                    result.add(g);
                }
            });
        }
        return result;
    }

    /**
     * 多知识库搜索、记忆搜索
     * <p>
     * Concurrent RAG retrieval from knowledge bases and character memory.
     * </p>
     *
     * @param characterId 角色ID / Character ID
     * @param filteredKb  有效的已关联的知识库 / Filtered enabled knowledge bases
     * @param llmService  大模型服务 / LLM service
     * @param queryText   查询文本 / Query text
     * @param mainExecutor 异步执行器 / Async task executor
     * @return 检索结果列表 / Retrieval result list
     */
    public static List<RetrieverWrapper> retrieve(Long characterId, List<KbInfoResp> filteredKb,
                                                  AbstractLLMService llmService, String queryText,
                                                  AsyncTaskExecutor mainExecutor) {
        ChatModel chatModel = llmService.buildChatLLM(
                ChatModelBuilderProperties.builder()
                        .temperature(AdiConstant.LLM_TEMPERATURE_DEFAULT)
                        .build());

        // 二开（T7-07）：记忆用户维度。检索主体必须在同步阶段捕获（下游检索跑在
        // mainExecutor 线程，ThreadContext 不可用）；开关关闭或匿名时保持历史行为
        // （仅 character_id 单条件）。隔离条件组装见 memorySemanticFilter/memoryCharacterFilter。
        User memoryUser = ThreadContext.getCurrentUser();
        boolean userDim = memoryUser != null && adiProperties != null
                && adiProperties.getMemory().isUserDimensionEnabled();
        Long memoryUserId = userDim ? memoryUser.getId() : null;

        //Create memory retriever for SEMANTIC channel.
        //Excludes records explicitly tagged as episodic. Old records without the
        //memory_type metadata (created before this feature) are treated as semantic.
        //<p>
        //创建 SEMANTIC 通道的记忆 retriever。
        //排除显式标记为 episodic 的记录；缺失 memory_type 字段的历史记录视为 semantic（pgvector 的
        //IsNotEqualTo 在键缺失时返回 true，符合预期）。
        Filter semanticFilter = memorySemanticFilter(characterId, memoryUserId,
                new IsNotEqualTo(MEMORY_TYPE, MemoryType.EPISODIC.getDesc()));
        RetrieverCreateParam memoryRetrieveParam = RetrieverCreateParam.builder()
                .chatModel(chatModel)
                .filter(semanticFilter)
                .maxResults(3)
                .minScore(RAG_RETRIEVE_MIN_SCORE_DEFAULT)
                .breakIfSearchMissed(false)
                .build();
        List<RetrieverWrapper> retrieverWrappers = new CompositeRag(AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY).createRetriever(memoryRetrieveParam);

        //Create episodic memory retriever (its own physically isolated vector store).
        //Episodic memories are retrieved alongside semantic ones and presented as a
        //timeline-structured "past events" section in the prompt.
        //<p>
        //创建情景记忆 retriever（独立向量库，与语义记忆物理隔离）。
        //episodic 记忆与 semantic 并行检索，在 prompt 中以时间轴结构的"过去事件"段落呈现。
        EmbeddingRag episodicRag = EmbeddingRagContext.get(AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY_EPISODIC);
        if (episodicRag != null) {
            RetrieverCreateParam episodicRetrieveParam = RetrieverCreateParam.builder()
                    .filter(memoryCharacterFilter(characterId, memoryUserId))
                    .maxResults(3)
                    .minScore(RAG_RETRIEVE_MIN_SCORE_DEFAULT)
                    .breakIfSearchMissed(false)
                    .build();
            ContentRetriever episodicRetriever = episodicRag.createRetriever(episodicRetrieveParam);
            retrieverWrappers.add(RetrieverWrapper.builder()
                    .contentFrom(AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY_EPISODIC)
                    .retriever(episodicRetriever)
                    .response(new ArrayList<>())
                    .build());
        }

        //Create knowledge base retriever
        if (!filteredKb.isEmpty()) {
            List<String> kbUuids = filteredKb.stream().map(KbInfoResp::getUuid).toList();
            log.info("Preparing to search related knowledge bases, kbUuids:{}, question:{}", String.join(",", kbUuids), queryText);
            // 二开（T1-05，2026-10-16）：构建库级 ACL 快照并捕获检索主体。
            // 检索本体在 mainExecutor 线程执行，ThreadContext 不可用，必须在这里（同步阶段）捕获；
            // KbInfoResp 自带 ownerUuid/isPublic/defaultSensitivity/defaultVisibilityGroups（T1-03 可读回字段）。
            RetrieverContext aclContext = RetrieverContext.none();
            User ctxUser = ThreadContext.getCurrentUser();
            if (null != ctxUser) {
                aclContext.setUserUuid(ctxUser.getUuid());
                aclContext.setUserIsAdmin(Boolean.TRUE.equals(ctxUser.getIsAdmin()));
            }
            aclContext.setKbUuids(kbUuids);
            Map<String, KbAclInfo> aclInfos = new HashMap<>();
            for (KbInfoResp kb : filteredKb) {
                aclInfos.put(kb.getUuid(), KbAclInfo.of(kb.getUuid(), kb.getOwnerUuid(), kb.getIsPublic(),
                        kb.getDefaultSensitivity(), toGroupList(kb.getDefaultVisibilityGroups())));
            }
            aclContext.setAclInfos(aclInfos);
            RetrieverCreateParam kbRetrieveParam = RetrieverCreateParam.builder()
                    .chatModel(chatModel)
                    .filter(new IsIn(KB_UUID, kbUuids))
                    .maxResults(3)
                    .minScore(RAG_RETRIEVE_MIN_SCORE_DEFAULT)
                    .breakIfSearchMissed(false)
                    .context(aclContext)
                    .build();
            List<RetrieverWrapper> kbRetrievers = new CompositeRag(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE).createRetriever(kbRetrieveParam);
            retrieverWrappers.addAll(kbRetrievers);
        }

        //Retrieve contents concurrently
        if (!retrieverWrappers.isEmpty()) {
            CountDownLatch countDownLatch = new CountDownLatch(retrieverWrappers.size());
            for (RetrieverWrapper retriever : retrieverWrappers) {
                mainExecutor.execute(() -> {
                    try {
                        List<Content> contents = retriever.getRetriever().retrieve(Query.from(queryText));
                        retriever.setResponse(contents);
                    } catch (Exception e) {
                        log.error("Retrieve content error", e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }
            try {
                boolean awaitRet = countDownLatch.await(1, TimeUnit.MINUTES);
                if (!awaitRet) {
                    log.warn("retrieveContents CountDownLatch await timeout");
                }
            } catch (InterruptedException e) {
                log.error("retrieveContents CountDownLatch await error", e);
                Thread.currentThread().interrupt();
            }
        }
        return retrieverWrappers;
    }

    /**
     * 将检索结果分离为记忆文本和知识文本
     * <p>
     * Separate retrieved content into memory text and knowledge text. Memory text
     * combines two channels: stable semantic knowledge and a timeline-flavored
     * "past events" section for episodic memories. Episodic items are formatted
     * with their content from the vector segment as-is (timestamps/importance/event_type
     * already live in the segment metadata and could be surfaced here later if needed).
     * <p>
     * 将检索结果分离为记忆文本和知识文本。记忆文本融合两个通道：稳定的语义知识 +
     * "过往事件"段（episodic）。
     * </p>
     */
    public static Pair<String, String> buildMemoryAndKnowledge(List<RetrieverWrapper> wrappers) {
        StringBuilder semanticMemory = new StringBuilder();
        StringBuilder episodicMemory = new StringBuilder();
        StringBuilder knowledge = new StringBuilder();
        wrappers.forEach(item -> {
            String retrieveType = item.getContentFrom();
            List<Content> response = item.getResponse();
            if (response == null) {
                return;
            }
            if (AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY.equals(retrieveType)) {
                for (Content content : response) {
                    // T6-05：检索内容（含记忆）进 prompt 前做注入检测 + 隔离包装
                    semanticMemory.append("- ").append(PromptInjectionDefense.isolateRetrieval(content.textSegment().text())).append("\n");
                }
            } else if (AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY_EPISODIC.equals(retrieveType)) {
                for (Content content : response) {
                    // T6-05：同上
                    episodicMemory.append("- ").append(PromptInjectionDefense.isolateRetrieval(content.textSegment().text())).append("\n");
                }
            } else if (AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE.equals(retrieveType)) {
                for (Content content : response) {
                    // T6-05：知识库检索内容是注入风险最高的通道（外部文档），强制隔离包装
                    knowledge.append(PromptInjectionDefense.isolateRetrieval(content.textSegment().text())).append("\n");
                }
            }
        });

        StringBuilder memory = new StringBuilder();
        if (semanticMemory.isEmpty() && episodicMemory.isEmpty()) {
            memory.append("None\n");
        } else {
            if (!semanticMemory.isEmpty()) {
                memory.append("[Stable knowledge about the user]\n").append(semanticMemory).append("\n");
            }
            if (!episodicMemory.isEmpty()) {
                memory.append("[Past events you recall]\n").append(episodicMemory).append("\n");
            }
        }
        if (knowledge.isEmpty()) {
            knowledge.append("None\n");
        } else {
            knowledge.append("\n");
        }
        return Pair.of(memory.toString(), knowledge.toString());
    }

    /**
     * 根据 Character 配置构建聊天请求参数
     * <p>
     * Build ChatModelRequest from Character configuration.
     * </p>
     *
     * @param character       角色 / Character
     * @param userPrompt      处理后的用户提示词 / Processed user prompt
     * @param user            用户 / User
     * @param llmService      LLM 服务 / LLM service
     * @param enableMcp       是否启用 MCP 工具 / Enable MCP tools
     * @param enableWebSearch 是否启用联网搜索 / Enable web search
     * @return 聊天请求参数 / Chat request params
     */
    public static ChatModelRequest buildChatRequestParams(Character character, String userPrompt,
                                                                User user, AbstractLLMService llmService,
                                                                boolean enableMcp, boolean enableWebSearch,
                                                                List<String> imageUrls) {
        ChatModelRequest.ChatModelRequestBuilder builder = ChatModelRequest.builder();

        //System message
        //系统提示词末尾强制追加身份护栏，防止模型自曝底层模型名（护栏永远在最末尾，优先级最高）
        //Append the identity guard last so it takes precedence over any user-authored persona text.
        String baseSystemMessage = AssistantIdentityGuard.append(
                character.getAiSystemMessage(), character.getTitle());
        if (StringUtils.isNotBlank(baseSystemMessage)) {
            builder.systemMessage(baseSystemMessage);
        }

        //Memory (for context understanding)
        if (Boolean.TRUE.equals(character.getUnderstandContextEnable())) {
            builder.memoryId(character.getUuid());
        }

        //User message
        // T6-05：用户输入侧做注入检测（只记日志+告警计数，不改写用户语义）
        PromptInjectionDefense.detectAndReportUserInput(userPrompt);
        builder.userMessage(userPrompt);

        //Image URLs (multimodal)
        if (imageUrls != null && !imageUrls.isEmpty()) {
            builder.imageUrls(imageUrls);
        }

        //MCP tools
        if (enableMcp && StringUtils.isNotBlank(character.getMcpIds())) {
            List<Long> mcpIds = AdiStringUtil.stringToList(character.getMcpIds(), ",", Long::parseLong);
            List<McpClient> mcpClients = userMcpService.createMcpClients(character.getUserId(), mcpIds);
            builder.mcpClients(mcpClients);
        }

        //Agent Skills：清单注入 system prompt + 内置工具（read_skill/read_skill_file/run_skill_script）
        String skillManifest = "";
        //二开（2026-09-21）：create_skill 常驻工具需要角色与用户上下文
        builder.characterUuid(character.getUuid());
        builder.chatUserId(character.getUserId());
        if (StringUtils.isNotBlank(character.getSkillIds())) {
            List<Long> skillIds = AdiStringUtil.stringToList(character.getSkillIds(), ",", Long::parseLong);
            List<Skill> skills = skillService.listEnabledByIds(skillIds);
            // T6-07：技能描述按当前任务（userPrompt）相关性按需注入，不再全量进 prompt
            SkillToolSpi.SkillBundle bundle = skillToolService.prepare(skills, userPrompt);
            if (StringUtils.isNotBlank(bundle.manifest())) {
                skillManifest = bundle.manifest();
                builder.skillBundle(bundle);
            }
        }

        //T9-M2（2026-10-28）：业务工具插件（拔插式业务能力）——按角色 plugin_ids 生成
        //工具包（manifest + 工具规格）；未配置或配置了未知插件码时安全跳过
        String bizManifest = "";
        if (StringUtils.isNotBlank(character.getPluginIds()) && null != bizToolService) {
            List<String> pluginCodes = AdiStringUtil.stringToList(character.getPluginIds(), ",", String::trim);
            BizToolSpi.ToolBundle bizBundle = bizToolService.prepare(pluginCodes);
            if (!bizBundle.toolSpecifications().isEmpty()) {
                bizManifest = bizBundle.manifest();
                builder.bizToolBundle(bizBundle);
            }
        }

        //Thinking
        AiModel aiModel = llmService.getAiModel();
        Boolean returnThinking = checkIfReturnThinking(aiModel, character);
        builder.returnThinking(returnThinking);

        //Web search — drop the flag if the resolved model does not support it,
        //so a stale config or capability change cannot cause a confusing LLM error.
        boolean effectiveWebSearch = enableWebSearch;
        if (effectiveWebSearch && !Boolean.TRUE.equals(aiModel.getIsSupportWebSearch())) {
            log.warn("Web search requested but model {}/{} does not support it; ignoring flag",
                    aiModel.getPlatform(), aiModel.getName());
            effectiveWebSearch = false;
        }
        builder.enableWebSearch(effectiveWebSearch);

        //技能清单追加到 system prompt（角色原有设定在前）
        if (StringUtils.isNotBlank(skillManifest)) {
            String base = builder.build().getSystemMessage();
            builder.systemMessage(StringUtils.isBlank(base) ? skillManifest : base + "\n\n" + skillManifest);
        }

        //T9-M2：业务插件能力说明追加到 system prompt（在技能清单之后）
        if (StringUtils.isNotBlank(bizManifest)) {
            String base = builder.build().getSystemMessage();
            builder.systemMessage(StringUtils.isBlank(base) ? bizManifest : base + "\n\n" + bizManifest);
        }

        return builder.build();
    }

    /**
     * 判断是否需要返回推理过程
     * <p>
     * Check if thinking/reasoning should be returned.
     * </p>
     *
     * @param aiModel   模型 / AI model
     * @param character 角色 / Character
     * @return 是否返回推理过程 / Whether to return thinking
     */
    public static Boolean checkIfReturnThinking(AiModel aiModel, Character character) {
        if (!aiModel.getIsReasoner()) {
            return null;
        }
        return Boolean.FALSE.equals(aiModel.getIsThinkingClosable()) || Boolean.TRUE.equals(character.getIsEnableThinking());
    }
}
