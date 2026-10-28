package com.moyz.adi.common.rag;

import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.interfaces.IStreamingChatAssistant;
import com.moyz.adi.common.interfaces.ITempStreamingChatAssistant;
import com.moyz.adi.common.interfaces.TriConsumer;
import com.moyz.adi.common.memory.shortterm.ChatMemoryStores;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.vo.*;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

import static com.moyz.adi.common.enums.ErrorEnum.B_BREAK_SEARCH;
import static com.moyz.adi.common.enums.ErrorEnum.B_LLM_SERVICE_DISABLED;

/**
 * 组合向量及图谱数据进行RAG
 */
@Slf4j
public class CompositeRag {

    /** 启动期推送（RagStaticConfigInitializer）：ragChat 需要注册 SSE 监听（非 bean 实例无法注入） */
    private static volatile SseManager staticSseManager;

    /** RagStaticConfigInitializer 启动期调用 */
    public static void initStatics(SseManager sseManager) {
        staticSseManager = sseManager;
    }

    private final EmbeddingRag embeddingRag;
    private final GraphRag graphRag;

    public CompositeRag(String retrieverName) {
        this.embeddingRag = EmbeddingRagContext.get(retrieverName);
        this.graphRag = GraphRagContext.get(retrieverName);
    }

    /**
     * 创建Retriever列表
     *
     * @param param 参数
     * @return ContentRetriever列表
     */
    public List<RetrieverWrapper> createRetriever(RetrieverCreateParam param) {
        List<RetrieverWrapper> retrievers = new ArrayList<>();
        if (null == embeddingRag && null == graphRag) {
            log.warn("No RAG configured");
            return retrievers;
        }
        if (null != embeddingRag) {
            ContentRetriever embeddingRetriever = embeddingRag.createRetriever(param);
            retrievers.add(RetrieverWrapper.builder().contentFrom(embeddingRag.getName()).retriever(embeddingRetriever).response(new ArrayList<>()).build());
        }
        if (null != graphRag) {
            //Most knowledge bases never build a graph index, yet the graph retriever always starts
            //with an LLM call that extracts entities from the question (~6s and one billable call)
            //before it touches the store — on an empty graph that money and latency buys nothing.
            //Probe first and skip the whole path when there is nothing to find.
            if (graphRag.isGraphEmpty(param)) {
                log.info("Skip graph retriever: no graph data for kb:{}", param.getKbUuid());
                return retrievers;
            }
            //Strict mode ("break if search missed") is enforced by the vector path only: the graph
            //is a supplementary source, and most knowledge bases never build a graph index. Leaving
            //the flag on here made every question on a strict KB abort with B_BREAK_SEARCH as soon
            //as the graph store had no vertices — discarding perfectly good vector hits. The graph
            //retriever only keeps the flag when it is the sole source available.
            RetrieverCreateParam graphParam = null != embeddingRag && param.isBreakIfSearchMissed()
                    ? param.toBuilder().breakIfSearchMissed(false).build()
                    : param;
            ContentRetriever graphRetriever = graphRag.createRetriever(graphParam);
            retrievers.add(RetrieverWrapper.builder().contentFrom(graphRag.getName()).retriever(graphRetriever).response(new ArrayList<>()).build());
        }
        return retrievers;
    }

    /**
     * 使用RAG处理提问
     *
     * @param retrievers   ContentRetriever列表
     * @param sseAskParam 请求参数
     * @param consumer     回调
     */
    public void ragChat(List<ContentRetriever> retrievers, SseAskParam sseAskParam, TriConsumer<String, PromptMeta, AnswerMeta> consumer) {
        SseManager sseManager = staticSseManager;
        User user = sseAskParam.getUser();
        sseManager.registerEventStreamListener(sseAskParam);
        try {
            query(retrievers, sseAskParam, (response, promptMeta, answerMeta) -> {
                try {
                    consumer.accept(response, promptMeta, answerMeta);
                } catch (Exception e) {
                    log.error("ragProcess error", e);
                }
            });
        } catch (Exception baseException) {
            if (baseException.getCause() instanceof BaseException && B_BREAK_SEARCH.getCode().equals(((BaseException) baseException.getCause()).getCode())) {
                sseManager.sendStartAndComplete(user.getId(), sseAskParam.getSseUuid(), "");
                consumer.accept("", PromptMeta.builder().inputTokens(0).build(), AnswerMeta.builder().outputTokens(0).build());
            } else {
                log.error("ragProcess error", baseException);
                sseManager.sendErrorAndComplete(user.getId(), sseAskParam.getSseUuid(), baseException.getMessage());
            }
        }

    }

    /**
     * RAG请求，对prompt进行各种增强后发给AI
     * ps: 挂载了知识库的请求才进行RAG增强
     * <p>
     * TODO...计算并截断超长的请求参数内容（历史记录+向量知识+图谱知识+用户问题+工具）
     *
     * @param retrievers 文档召回器（向量、图谱）
     * @param params     前端传过来的请求参数
     * @param consumer   LLM响应内容的消费者
     */
    private void query(List<ContentRetriever> retrievers, SseAskParam params, TriConsumer<String, PromptMeta, AnswerMeta> consumer) {
        AbstractLLMService llmService = LLMContext.getServiceOrDefault(params.getModelPlatform(), params.getModelName());
        if (!llmService.isEnabled()) {
            log.error("llm service is disabled");
            throw new BaseException(B_LLM_SERVICE_DISABLED);
        }

        QueryRouter queryRouter = new DefaultQueryRouter(retrievers);
        TokenStream tokenStream;
        ChatModelRequest chatModelRequest = params.getHttpRequestParams();
        if (StringUtils.isNotBlank(chatModelRequest.getMemoryId())) {
            ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(2)
                    //二开（2026-10-05）：必须保持系统提示词在首位。该开关默认 false 时，
                    //AiServices 传入的 SystemMessage 会被追加到窗口末尾，再叠加
                    //MapDBChatMemoryStore.updateMessages 只保留 index 0 的 SystemMessage，
                    //身份护栏会被挤出/丢弃，导致模型自曝底层模型名。
                    //二开：keep the system message first, otherwise the identity guard is dropped.
                    .alwaysKeepSystemMessageFirst(true)
                    .chatMemoryStore(ChatMemoryStores.get())
                    .build();
            QueryTransformer queryTransformer = new CompressingQueryTransformer(llmService.buildChatLLM(params.getModelProperties()));
            RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                    .queryTransformer(queryTransformer)
                    .queryRouter(queryRouter)
                    .build();
            IStreamingChatAssistant assistant = AiServices.builder(IStreamingChatAssistant.class)
                    .streamingChatModel(llmService.buildStreamingChatModel(params.getModelProperties()))
                    .retrievalAugmentor(retrievalAugmentor)
                    .chatMemoryProvider(chatMemoryProvider)
                    .build();
            if (StringUtils.isNotBlank(chatModelRequest.getSystemMessage())) {
                //二开（2026-10-05）：先刷新历史里已固化的系统提示词，再交给 AiServices 注入。
                //否则旧的那份（不含身份护栏）会一直留在首位，模型仍会自曝底层模型名。
                ChatMemoryStores.get().refreshSystemMessage(
                        chatModelRequest.getMemoryId(), chatModelRequest.getSystemMessage());
                tokenStream = assistant.chatWithSystem(chatModelRequest.getMemoryId(), chatModelRequest.getSystemMessage(), chatModelRequest.getUserMessage(), new ArrayList<>());
            } else {
                tokenStream = assistant.chat(chatModelRequest.getMemoryId(), chatModelRequest.getUserMessage(), new ArrayList<>());
            }
        } else {
            ITempStreamingChatAssistant assistant = AiServices.builder(ITempStreamingChatAssistant.class)
                    .streamingChatModel(llmService.buildStreamingChatModel(params.getModelProperties()))
                    .retrievalAugmentor(DefaultRetrievalAugmentor.builder().queryRouter(queryRouter).build())
                    .build();
            if (StringUtils.isNotBlank(chatModelRequest.getSystemMessage())) {
                tokenStream = assistant.chatWithSystem(chatModelRequest.getSystemMessage(), chatModelRequest.getUserMessage(), new ArrayList<>());
            } else {
                tokenStream = assistant.chatSimple(chatModelRequest.getUserMessage(), new ArrayList<>());
            }
        }
        tokenStream
                .onPartialResponse(content -> SseManager.parseAndSendPartialMsg(params.getSseUuid(), content))
                .onCompleteResponse(response -> {
                    Pair<PromptMeta, AnswerMeta> pair = SseManager.calculateToken(response, params.getUuid());
                    consumer.accept(response.aiMessage().text(), pair.getLeft(), pair.getRight());
                })
                .onError(error -> SseManager.errorAndShutdown(error, params.getSseUuid()))
                .start();
    }

}
