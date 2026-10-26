package com.moyz.adi.common.memory.longterm;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.enums.EventType;
import com.moyz.adi.common.enums.MemoryType;
import com.moyz.adi.common.memory.vo.ExtractedEpisodicEvent;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.*;

/**
 * Append-only writer for episodic memory. Each event becomes a row in the dedicated
 * {@code episodicEmbeddingStore} (physically isolated from semantic memory so the
 * two never compete for top-K). All structured fields (event_type, importance,
 * create_time, conv_msg_id) live in the vector store's metadata — no relational table.
 * <p>
 * 情景记忆 append-only 写入服务。事件写入独立的 {@code episodicEmbeddingStore}
 * （与语义记忆物理隔离，互不抢占 top-K 召回）。所有结构化字段（事件类型、重要性、创建时间、
 * 来源消息）都存在向量库 metadata 中，不再需要关系表。
 */
@Slf4j
@Service
public class EpisodicMemoryService {

    @Resource
    @Qualifier("episodicEmbeddingStore")
    private EmbeddingStore<TextSegment> episodicEmbeddingStore;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private AdiProperties adiProperties;

    /**
     * userId is persisted into metadata as the stringified {@code user_id} key when the
     * user dimension is enabled (T7-07) — the isolation dimension that lets the same
     * user share memories across agents/sessions while never seeing anyone else's.
     * <p>
     * userDimensionEnabled（T7-07）时把字符串化的 user_id 写进 metadata：同一用户
     * 跨 Agent 跨会话共享记忆，且永远读不到他人的记忆。
     */
    private Long effectiveUserId(Long userId) {
        if (null == userId) {
            return null;
        }
        return adiProperties.getMemory() != null && adiProperties.getMemory().isUserDimensionEnabled()
                ? userId : null;
    }

    /**
     * Batch add episodic events to the dedicated episodic vector store. No
     * idempotency check is performed: retried calls with the same
     * {@code characterMsgId} produce duplicate rows, which is acceptable for
     * episodic recall since retries are rare and duplicates have negligible
     * impact on LLM analysis quality.
     *
     * @param characterId    character id
     * @param userId         user id (written into metadata as the user dimension when
     *                       {@code adi.memory.user-dimension-enabled}, T7-07)
     * @param characterMsgId source character message id, stored in metadata for traceability only
     * @param events         events to add
     * @param isFreeToken    whether the model is free-tier (passed through for cost accounting)
     */
    public void batchAdd(Long characterId, Long userId, Long characterMsgId,
                         List<ExtractedEpisodicEvent> events, boolean isFreeToken) {
        if (CollectionUtils.isEmpty(events)) {
            return;
        }
        // isFreeToken is reserved for cost accounting; userId now feeds the T7-07
        // user dimension (see effectiveUserId).

        // Format once per batch with project-wide pattern (yyyy-MM-dd HH:mm:ss).
        // Sub-second precision adds no recall value for "past events"; the canonical
        // format is also the lingua franca used elsewhere in the codebase / UI.
        // 每批格式化一次，使用项目统一的 yyyy-MM-dd HH:mm:ss——亚秒精度对"过往事件"
        // 召回无价值，此格式与代码库/前端其他时间字段一致。
        String createTimeStr = LocalDateTimeUtil.format(LocalDateTime.now(), LocalDateTimeUtil.PATTERN_DEFAULT);
        List<String> embeddingIds = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();

        for (ExtractedEpisodicEvent event : events) {
            if (event == null || StringUtils.isBlank(event.getSummary())) {
                continue;
            }
            TextSegment segment = buildEventSegment(characterId, effectiveUserId(userId), characterMsgId, event, createTimeStr);
            Embedding embedding = embeddingModel.embed(event.getSummary()).content();

            // PgVector requires a canonical UUID string (parsed via UUID.fromString);
            // the project-wide UuidUtil.createShort() strips dashes and would fail here.
            String embeddingId = UUID.randomUUID().toString();
            embeddingIds.add(embeddingId);
            embeddings.add(embedding);
            segments.add(segment);
        }

        if (segments.isEmpty()) {
            log.warn("No valid episodic events to persist");
            return;
        }

        episodicEmbeddingStore.addAll(embeddingIds, embeddings, segments);
        log.info("Episodic memory batch added: characterId={}, count={}", characterId, segments.size());
    }

    /**
     * Persist a single user-confirmed episodic event (T7-07 pending → confirm flow).
     * Same metadata contract as {@link #batchAdd} so confirmed rows are indistinguishable
     * from direct writes.
     * <p>
     * 持久化一条用户已确认的敏感情景事件（T7-07 confirm 端）。metadata 契约与
     * {@link #batchAdd} 完全一致，确认后的行与直写行不可区分。
     */
    public void addConfirmedEvent(Long characterId, Long userId, Long characterMsgId, ExtractedEpisodicEvent event) {
        if (null == event || StringUtils.isBlank(event.getSummary())) {
            return;
        }
        TextSegment segment = buildEventSegment(characterId, effectiveUserId(userId), characterMsgId, event,
                LocalDateTimeUtil.format(LocalDateTime.now(), LocalDateTimeUtil.PATTERN_DEFAULT));
        Embedding embedding = embeddingModel.embed(event.getSummary()).content();
        episodicEmbeddingStore.addAll(List.of(UUID.randomUUID().toString()), List.of(embedding), List.of(segment));
        log.info("Confirmed episodic event added: characterId={}", characterId);
    }

    private TextSegment buildEventSegment(Long characterId, Long userId, Long characterMsgId,
                                          ExtractedEpisodicEvent event, String createTimeStr) {
        String eventType = EventType.fromString(event.getEventType()).getCode();
        int importance = event.getImportance() != null
                ? Math.max(1, Math.min(5, event.getImportance()))
                : AdiConstant.EPISODIC_IMPORTANCE_DEFAULT;

        // Build metadata with a mutable HashMap so characterMsgId can be conditionally
        // added (Map.of rejects null values). CHARACTER_ID stays as Long to match
        // the type used by IsEqualTo filters.
        Map<String, Object> meta = new HashMap<>();
        meta.put(CHARACTER_ID, characterId);
        meta.put(MEMORY_TYPE, MemoryType.EPISODIC.getDesc());
        meta.put(CREATE_TIME, createTimeStr);
        meta.put(EVENT_TYPE, eventType);
        meta.put(IMPORTANCE, importance);
        if (characterMsgId != null) {
            meta.put(CHARACTER_MSG_ID, characterMsgId);
        }
        // T7-07：用户维度（userDimensionEnabled 且 userId 已知时写入，见 effectiveUserId）
        if (userId != null) {
            meta.put(USER_ID, String.valueOf(userId));
        }
        Metadata metadata = new Metadata(meta);
        return TextSegment.from(event.getSummary(), metadata);
    }
}

