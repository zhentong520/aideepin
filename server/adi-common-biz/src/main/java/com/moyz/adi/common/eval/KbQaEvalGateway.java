package com.moyz.adi.common.eval;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.rag.CompositeRag;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.util.PromptUtil;
import com.moyz.adi.common.vo.ChatModelBuilderProperties;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 真实链路评测网关（T6-01）：与阻塞式知识库问答同一条检索链
 * （CompositeRag → EmbeddingRag，含混合检索/RRF/rerank/左右扩窗后置处理器）。
 * <p>
 * 刻意用 {@code adi.eval.enabled} 显式开关门控（默认 false）：评测跑分会真实消耗嵌入与
 * 生成额度，绝不能被普通请求意外触发。开启前先确认：
 * <ol>
 *   <li>目标知识库已按当前入库参数灌入语料（T6-03 改动后需重建索引）；</li>
 *   <li>嵌入/生成平台的 key 已配置且健康（先跑管理端模型健康检查）；</li>
 *   <li>withGeneration=false 时只评检索侧，拒答题按「无检索结果即拒答」判定。</li>
 * </ol>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "adi.eval.enabled", havingValue = "true")
public class KbQaEvalGateway implements QaEvalGateway {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbDocumentService kbDocumentService;

    public KbQaEvalGateway(KnowledgeBaseService knowledgeBaseService, KbDocumentService kbDocumentService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.kbDocumentService = kbDocumentService;
    }

    @Override
    public GatewayResult ask(QaEvalItem item, EvalConfig config) {
        KnowledgeBase kb = knowledgeBaseService.getOrThrow(config.getKbUuid());

        int maxResults = config.getTopK() > 0 ? config.getTopK() : kb.getRetrieveMaxResults();
        if (maxResults < 1) {
            // 与阻塞式问答同一自适应口径：库配置 0（自动）→ 用上限
            maxResults = AdiConstant.RAG_RETRIEVE_NUMBER_MAX;
        }

        ChatModel chatModel = LLMContext.getServiceById(kb.getIngestModelId(), true).buildChatLLM(
                ChatModelBuilderProperties.builder().temperature(kb.getQueryLlmTemperature()).build());
        Set<String> disabledDocUuids = new HashSet<>(kbDocumentService.listDisabledItemUuids(kb.getUuid()));
        RetrieverCreateParam createParam = RetrieverCreateParam.builder()
                .chatModel(chatModel)
                .filter(new IsEqualTo(AdiConstant.MetadataKey.KB_UUID, kb.getUuid()))
                .maxResults(maxResults)
                .minScore(kb.getRetrieveMinScore())
                .breakIfSearchMissed(kb.getIsStrict())
                .excludedItemUuids(disabledDocUuids)
                .context(knowledgeBaseService.buildRetrieverContext(kb, disabledDocUuids))
                .kbUuid(kb.getUuid())
                .build();

        List<RetrievedChunk> chunks = new ArrayList<>();
        CompositeRag compositeRag = new CompositeRag(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE);
        compositeRag.createRetriever(createParam).forEach(wrapper -> {
            ContentRetriever retriever = wrapper.getRetriever();
            if (null == retriever) {
                return;
            }
            List<Content> contents;
            try {
                contents = retriever.retrieve(dev.langchain4j.rag.query.Query.from(item.getQuestion()));
            } catch (BaseException e) {
                throw e;
            } catch (Exception e) {
                log.error("Eval retrieval failed, kbUuid:{}, question:{}", kb.getUuid(), item.getQuestion(), e);
                throw new IllegalStateException("retrieval failed: " + e.getMessage(), e);
            }
            if (contents == null) {
                return;
            }
            for (Content content : contents) {
                if (content == null || content.textSegment() == null) {
                    continue;
                }
                Metadata metadata = content.textSegment().metadata();
                String title = metadata == null ? "" : StringUtils.defaultString(metadata.getString(AdiConstant.MetadataKey.DOC_TITLE));
                chunks.add(new RetrievedChunk(content.textSegment().text(), title));
            }
        });

        // 生成侧（可选）：与阻塞式问答同参——同一 prompt 模板、同一系统消息语义
        String answer = null;
        Boolean refused = null;
        if (config.isWithGeneration()) {
            StringBuilder context = new StringBuilder();
            chunks.forEach(chunk -> context.append(chunk.getText()).append("\n"));
            String prompt = PromptUtil.createPrompt(item.getQuestion(), "", context.toString(), "");
            AbstractLLMService llmService = LLMContext.getServiceOrDefault(null, null);
            answer = llmService.buildChatLLM(ChatModelBuilderProperties.builder()
                    .temperature(kb.getQueryLlmTemperature()).build())
                    .chat(dev.langchain4j.data.message.UserMessage.from(prompt))
                    .aiMessage().text();
            // 严格模式检索为空 → 生成侧本来就不会被调用（链路抛 B_BREAK_SEARCH）；
            // 宽松模式下「[无答案]」话术交给评分器按拒答话术判定
            refused = StringUtils.isBlank(answer);
        } else if (chunks.isEmpty() && Boolean.TRUE.equals(kb.getIsStrict())) {
            // 纯检索评测 + 严格模式：无检索结果即拒答
            refused = true;
        }
        return new GatewayResult(chunks, answer, refused, null);
    }
}
