package com.moyz.adi.common.memory.longterm;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.enums.MemoryType;
import com.moyz.adi.common.memory.vo.BatchActionMemories;
import com.moyz.adi.common.rag.EmbeddingRagContext;
import com.moyz.adi.common.vo.EmbeddingIngestParam;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.constant.AdiConstant.MetadataKey.CHARACTER_ID;
import static com.moyz.adi.common.constant.AdiConstant.MetadataKey.USER_ID;

/**
 * Semantic memory service: deduplicating merge candidates retrieval and
 * ADD / UPDATE / DELETE execution against the semantic vector store. Does not
 * call LLMs and does not account for cost — the caller (dispatcher) drives those.
 * <p>
 * 语义记忆服务：负责合并候选检索和 ADD / UPDATE / DELETE 执行。不调 LLM、不计 token 成本，
 * 由调度器统筹这些事项。物理隔离后只在 semantic store 上工作，不再过滤 memory_type。
 */
@Slf4j
@Service
public class SemanticMemoryService {

    @Resource
    @Qualifier("semanticEmbeddingStore")
    private EmbeddingStore<TextSegment> semanticEmbeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private AdiProperties adiProperties;

    /**
     * For each fact, find its top-K nearest neighbours in the semantic store. Each
     * fact keeps its own tmpId space so the LLM never sees ambiguous IDs across facts.
     * <p>
     * 对每条 fact 检索 top-K 候选。每条 fact 独立的 tmpId 空间，避免 LLM 跨 fact 时 id 歧义。
     * <p>
     * 二开（T7-07）：userDimensionEnabled 时按 {@code character_id + user_id} 双条件过滤，
     * 保证合并候选只来自当前用户名下的记忆（用户隔离，见 {@link #memoryFilter}）。
     */
    public MergeCandidates searchMergeCandidates(List<String> facts, Long characterId, Long userId) {
        Map<String, List<Map<String, String>>> factToOldMemoriesForPrompt = new LinkedHashMap<>();
        Map<String, Map<String, String>> factToTmpIdToEmbeddingId = new HashMap<>();
        Map<String, Map<String, EmbeddingMatch<TextSegment>>> factToEmbeddingIdToMatch = new HashMap<>();
        double minScore = adiProperties.getMemory() != null
                ? adiProperties.getMemory().getMinScore()
                : AdiConstant.LONG_TERM_MEMORY_MIN_SCORE_DEFAULT;
        boolean userDim = adiProperties.getMemory() != null && adiProperties.getMemory().isUserDimensionEnabled();
        Filter mergeFilter = memoryFilter(characterId, userId, userDim);

        for (String fact : facts) {
            if (StringUtils.isBlank(fact)) {
                continue;
            }
            Embedding embedding = embeddingModel.embed(fact).content();
            // 二开（T1-05，2026-10-16）命名空间结论：此处与知识库检索【不共用 kb_uuid 命名空间】。
            // 1) 物理隔离：semanticEmbeddingStore 落在 adi_character_memory_embedding[_<dim>] 表，
            //    知识库向量在 adi_knowledge_base_embedding[_<dim>]，两张表互不可见；
            // 2) metadata 隔离：本链路只写 conv_id(character_id)/memory_type 等键，
            //    从不写 kb_uuid，也不写 T1-04 的 acl_* 键；filter 见 {@link #memoryFilter}
            //    （T7-07 起 userDimensionEnabled 时叠加 user_id 条件，隔离逻辑集中一处）。
            // 因此知识库侧的 ACL 过滤（AdiEmbeddingStoreContentRetriever / KbAclFilterBuilder）
            // 对记忆检索零影响，记忆链路无需任何 ACL 处理。
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(5)
                    .minScore(minScore)
                    .filter(mergeFilter)
                    .build();
            EmbeddingSearchResult<TextSegment> searchResult = semanticEmbeddingStore.search(searchRequest);

            Map<String, EmbeddingMatch<TextSegment>> embeddingIdToMatch = new LinkedHashMap<>();
            searchResult.matches().forEach(item -> embeddingIdToMatch.put(item.embeddingId(), item));

            List<Map<String, String>> oldMemoryForPrompt = new ArrayList<>();
            Map<String, String> tmpIdToEmbeddingId = new HashMap<>();
            int i = 0;
            for (Map.Entry<String, EmbeddingMatch<TextSegment>> entry : embeddingIdToMatch.entrySet()) {
                String tmpId = String.valueOf(i);
                oldMemoryForPrompt.add(Map.of("id", tmpId, "text", entry.getValue().embedded().text()));
                tmpIdToEmbeddingId.put(tmpId, entry.getKey());
                i++;
            }

            factToOldMemoriesForPrompt.put(fact, oldMemoryForPrompt);
            factToTmpIdToEmbeddingId.put(fact, tmpIdToEmbeddingId);
            factToEmbeddingIdToMatch.put(fact, embeddingIdToMatch);
        }
        return new MergeCandidates(factToOldMemoriesForPrompt, factToTmpIdToEmbeddingId, factToEmbeddingIdToMatch);
    }

    /**
     * Apply ADD / UPDATE / DELETE actions to the semantic vector store. Single-action
     * failures are caught here so other actions still proceed.
     * <p>
     * 应用 ADD / UPDATE / DELETE 操作。单条失败不影响其他操作。
     * <p>
     * 二开（T7-07）：userId 用于 ADD/UPDATE 写入 metadata 的 {@code user_id} 键
     * （userDimensionEnabled 时），同一用户跨 Agent 跨会话共享、他人不可见。
     */
    public void applyActions(BatchActionMemories batchActions, Long characterId, Long userId, MergeCandidates candidates) {
        if (batchActions == null || batchActions.getActions() == null) {
            return;
        }
        for (BatchActionMemories.BatchActionMemory action : batchActions.getActions()) {
            try {
                applyAction(action, characterId, userId, candidates);
            } catch (Exception ex) {
                log.warn("Apply memory action failed, action={}, error={}", action, ex.getMessage());
            }
        }
    }

    /**
     * Persist a user-confirmed memory (T7-07 pending → confirm flow) directly into the
     * semantic store with the same metadata contract as the ADD path.
     * <p>
     * 持久化用户已确认的敏感记忆（T7-07 pending→confirm 两段式的 confirm 端），
     * metadata 契约与 ADD 路径完全一致（含 user_id 维度）。
     */
    public void addConfirmed(Long characterId, Long userId, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        ingestSemantic(characterId, userId, text);
    }

    /**
     * Semantic ingest shared by ADD action and the confirm flow: same split params,
     * same metadata keys, same rag context.
     * <p>
     * 语义入库共用入口：ADD 与 confirm 走完全一致的分块参数与 metadata。
     */
    private void ingestSemantic(Long characterId, Long userId, String text) {
        Document document = new DefaultDocument(text, memoryMetadata(characterId, userId));
        EmbeddingRagContext.get(AdiConstant.RetrieveContentFrom.CHARACTER_MEMORY).ingest(document,
                EmbeddingIngestParam.builder()
                        .overlap(20)
                        .strategy(AdiConstant.SplitStrategy.RECURSIVE)
                        .maxSegmentSize(AdiConstant.RAG_MAX_SEGMENT_SIZE_IN_TOKENS)
                        .customSeparator("")
                        .build());
    }

    /**
     * Memory metadata contract for the semantic store. {@code user_id} is written
     * (stringified) only when the user dimension is enabled and the user id is known —
     * otherwise the key is omitted entirely, matching the legacy (character-only) data.
     * <p>
     * 语义记忆 metadata 契约。userDimensionEnabled 且 userId 已知时写入字符串化的
     * {@code user_id}；否则完全不写该键，与历史（仅 character 维度）数据一致。
     * 写哨兵 {@code USER_ID_UNKNOWN} 的只有 V14 迁移对无主存量的回填，写入路径宁缺毋滥。
     */
    public static Metadata memoryMetadata(Long characterId, Long userId) {
        Map<String, Object> meta = new HashMap<>();
        meta.put(CHARACTER_ID, characterId);
        meta.put(AdiConstant.MetadataKey.MEMORY_TYPE, MemoryType.SEMANTIC.getDesc());
        if (null != userId) {
            meta.put(USER_ID, String.valueOf(userId));
        }
        return new Metadata(meta);
    }

    /**
     * Semantic memory filter factory — the single place where the character/user
     * isolation conditions are composed, shared by merge-candidate search and unit tests.
     * <p>
     * 语义记忆过滤条件工厂——合并候选检索与单测共用的唯一组装点：
     * userDimensionEnabled 且 userId 已知时为 {@code And(character_id, user_id)}，
     * 否则退化为历史行为 {@code character_id} 单条件。
     */
    public static Filter memoryFilter(Long characterId, Long userId, boolean userDimensionEnabled) {
        IsEqualTo characterEq = new IsEqualTo(CHARACTER_ID, characterId);
        if (!userDimensionEnabled || null == userId) {
            return characterEq;
        }
        return new And(characterEq, new IsEqualTo(USER_ID, String.valueOf(userId)));
    }

    private void applyAction(BatchActionMemories.BatchActionMemory action,
                             Long characterId,
                             Long userId,
                             MergeCandidates candidates) {
        if (action == null || StringUtils.isBlank(action.getEvent())) {
            return;
        }
        String event = action.getEvent().trim().toUpperCase();
        if (AdiConstant.MemoryEvent.NONE.equalsIgnoreCase(event)) {
            log.info("No changes required for memory id: {}", action.getId());
            return;
        }

        // ADD: no need to resolve old memories, ingest the new content directly.
        // ADD：无需定位旧记忆，直接 ingest 新内容。
        if (AdiConstant.MemoryEvent.ADD.equalsIgnoreCase(event)) {
            if (StringUtils.isBlank(action.getText())) {
                log.warn("ADD action missing text, skip");
                return;
            }
            ingestSemantic(characterId, userId, action.getText());
            return;
        }

        // UPDATE / DELETE: locate the old memory via the fact-scoped tmpId space.
        // UPDATE / DELETE：通过 fact-scoped tmpId 空间定位旧记忆。
        Map<String, String> tmpIdToEmbeddingId = candidates.factToTmpIdToEmbeddingId.get(action.getFact());
        Map<String, EmbeddingMatch<TextSegment>> embeddingIdToMatch = candidates.factToEmbeddingIdToMatch.get(action.getFact());
        if (tmpIdToEmbeddingId == null || embeddingIdToMatch == null) {
            log.warn("LLM hallucinated fact field, no matching context, fact={}, skip", action.getFact());
            return;
        }
        String tmpId = action.getId();
        if (StringUtils.isBlank(tmpId)) {
            log.warn("UPDATE/DELETE action missing id, action={}, skip", action);
            return;
        }
        String embeddingId = tmpIdToEmbeddingId.get(tmpId);
        if (embeddingId == null) {
            log.warn("LLM hallucinated id, tmpId={} not in mapping, fact={}, skip", tmpId, action.getFact());
            return;
        }

        if (AdiConstant.MemoryEvent.DELETE.equalsIgnoreCase(event)) {
            semanticEmbeddingStore.remove(embeddingId);
            return;
        }

        if (AdiConstant.MemoryEvent.UPDATE.equalsIgnoreCase(event)) {
            if (StringUtils.isBlank(action.getText())) {
                log.warn("UPDATE action missing text, action={}, skip", action);
                return;
            }
            EmbeddingMatch<TextSegment> match = embeddingIdToMatch.get(embeddingId);
            if (match == null) {
                log.warn("UPDATE action embeddingId not found in match map, skip");
                return;
            }
            semanticEmbeddingStore.remove(embeddingId);
            TextSegment newSegment = TextSegment.from(action.getText(), memoryMetadata(characterId, userId));
            semanticEmbeddingStore.addAll(List.of(embeddingId), List.of(match.embedding()), List.of(newSegment));
            return;
        }

        log.warn("Unknown memory event: {}, action={}", event, action);
    }

    /**
     * Merge candidates collected per-fact. Exposes the per-fact prompt input
     * for the dispatcher to feed the LLM, and keeps the tmpId↔embeddingId
     * mappings + raw EmbeddingMatches needed for {@link #applyActions}.
     * <p>
     * 按 fact 聚合的合并候选。三个 map 分别给调度器构造 prompt、给 applyActions 反查使用。
     */
    public static final class MergeCandidates {
        public final Map<String, List<Map<String, String>>> factToOldMemoriesForPrompt;
        public final Map<String, Map<String, String>> factToTmpIdToEmbeddingId;
        public final Map<String, Map<String, EmbeddingMatch<TextSegment>>> factToEmbeddingIdToMatch;

        public MergeCandidates(Map<String, List<Map<String, String>>> factToOldMemoriesForPrompt,
                               Map<String, Map<String, String>> factToTmpIdToEmbeddingId,
                               Map<String, Map<String, EmbeddingMatch<TextSegment>>> factToEmbeddingIdToMatch) {
            this.factToOldMemoriesForPrompt = factToOldMemoriesForPrompt;
            this.factToTmpIdToEmbeddingId = factToTmpIdToEmbeddingId;
            this.factToEmbeddingIdToMatch = factToEmbeddingIdToMatch;
        }

        public boolean isEmpty() {
            return factToOldMemoriesForPrompt.isEmpty();
        }
    }
}
