package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.dto.extapi.ExtKbRetrieveResp;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.rag.AdiEmbeddingStoreContentRetriever;
import com.moyz.adi.common.rag.EmbeddingRag;
import com.moyz.adi.common.rag.EmbeddingRagContext;
import com.moyz.adi.common.rag.KbAclFilterBuilder;
import com.moyz.adi.common.rag.SegmentHitResolver;
import com.moyz.adi.common.vo.RetrieverContext;
import com.moyz.adi.common.vo.RetrieverCreateParam;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;
import static com.moyz.adi.common.enums.ErrorEnum.A_USER_NOT_AUTH;

/**
 * External API 纯检索服务（T1-08 / T1-10 共用，2026-10-19）。
 * <p>
 * <b>这是 ext 面多库检索的唯一实现</b>，两个入口都走这里：
 * <ul>
 *   <li>{@code POST /ext/v1/knowledge/retrieve}（T1-08）：多库，无权库静默跳过；</li>
 *   <li>MCP {@code kb_search} 工具（T1-10）：单库（路径上的 kbUuid），无权报明确错误。</li>
 * </ul>
 * <b>检索内核复用</b>（任务书 T1-08/T1-10 铁律：不得旁路另写 embeddingStore.search）：
 * 调用链 = {@link #retrieveSingleKb} → {@link EmbeddingRag#createRetriever}
 * → {@link AdiEmbeddingStoreContentRetriever#retrieve}
 * → 内部 {@code embeddingStore.search(searchRequest)}（全仓库唯一底层检索入口，
 * AdiEmbeddingStoreContentRetriever.java:292）。
 * 向量层 ACL 过滤（wrapFilter）与应用层双保险（isAllowed）都在内核内生效——本服务只需
 * 把检索主体（userUuid/isAdmin）与库级 ACL 快照填进 {@link RetrieverContext}。
 * <p>
 * 权限语义（ext key 绑定用户主体，语义与 {@link KbAclFilterBuilder} 一致）：
 * owner 本人 / 管理员 / PUBLIC（公开库）可读；无权或库不存在 → 静默跳过（T1-08 的
 * skippedKbUuids）。MCP 场景 key 显式点名库，由调用方把 skipped 转成明确错误。
 */
@Slf4j
@Service
public class ExtKnowledgeSearchService {

    /**
     * 响应 metadata 白名单：只放与来源定位相关、非敏感的键
     */
    private static final Set<String> METADATA_WHITELIST = Set.of(
            AdiConstant.MetadataKey.KB_UUID,
            AdiConstant.MetadataKey.KB_ITEM_UUID,
            AdiConstant.MetadataKey.DOC_UUID,
            AdiConstant.MetadataKey.DOC_TITLE,
            AdiConstant.MetadataKey.SEGMENT_ID,
            AdiConstant.MetadataKey.POSITION);

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private DocumentSegmentService documentSegmentService;

    @Resource
    private DocumentSegmentQuestionService documentSegmentQuestionService;

    @Resource
    private DocumentSegmentChildChunkService documentSegmentChildChunkService;

    @Resource
    private AdiProperties adiProperties;

    /**
     * 多库纯检索（不调 LLM）。
     *
     * @param user   ext key 绑定的用户主体（检索/ACL 的 subject）
     * @param kbUuids 要检索的知识库 uuid 列表
     * @param query   检索问题
     * @param topK    可选，单库召回条数；null/<=0 时用各库自己的 retrieve_max_results，
     *                并受 adi.ext.max-top-k 封顶
     * @return 命中项 + 静默跳过的库列表
     */
    public ExtKbRetrieveResp retrieve(User user, List<String> kbUuids, String query, Integer topK) {
        if (null == user) {
            throw new BaseException(A_USER_NOT_AUTH);
        }
        if (StringUtils.isBlank(query)) {
            throw new BaseException(A_PARAMS_ERROR.getCode(), "query is required");
        }
        EmbeddingRag rag = EmbeddingRagContext.get(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE);
        if (null == rag) {
            // 检索内核未装配（向量库未配置）：这是环境错误，不能静默返回空
            throw new BaseException(A_PARAMS_ERROR.getCode(), "embedding rag is not configured");
        }
        List<String> requested = null == kbUuids
                ? List.of() : kbUuids.stream().filter(StringUtils::isNotBlank).distinct().toList();
        List<ExtKbRetrieveResp.Item> items = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String kbUuid : requested) {
            KnowledgeBase kb = getKbOrNull(kbUuid);
            if (null == kb || !isReadable(kb, user)) {
                // T1-08 语义：无权/不存在 → 静默跳过，不报错
                log.info("Ext retrieve skip kb, kbUuid:{}, user:{}, reason:{}",
                        kbUuid, user.getUuid(), null == kb ? "not_found" : "no_permission");
                skipped.add(kbUuid);
                continue;
            }
            items.addAll(retrieveSingleKb(rag, kb, user, query, topK));
        }
        ExtKbRetrieveResp resp = new ExtKbRetrieveResp();
        resp.setItems(items);
        resp.setSkippedKbUuids(skipped);
        return resp;
    }

    /**
     * 单库检索：走与知识库问答完全相同的检索内核
     * （{@link EmbeddingRag#createRetriever} → {@link AdiEmbeddingStoreContentRetriever}）。
     */
    private List<ExtKbRetrieveResp.Item> retrieveSingleKb(EmbeddingRag rag, KnowledgeBase kb, User user,
                                                          String query, Integer topK) {
        Set<String> disabledDocUuids = new HashSet<>(kbDocumentService.listDisabledItemUuids(kb.getUuid()));
        RetrieverContext ctx = knowledgeBaseService.buildRetrieverContext(kb, disabledDocUuids);
        // 检索主体与库级 ACL 快照：内核的 wrapFilter/isAllowed 从这里取，不在内核里反查
        ctx.setUserUuid(user.getUuid());
        ctx.setUserIsAdmin(Boolean.TRUE.equals(user.getIsAdmin()));
        ctx.setUserGroups(List.of());
        ctx.setAclInfos(Map.of(kb.getUuid(), KbAclFilterBuilder.toAclInfo(kb)));
        ctx.setKbUuids(List.of(kb.getUuid()));

        RetrieverCreateParam createParam = RetrieverCreateParam.builder()
                .filter(new IsEqualTo(AdiConstant.MetadataKey.KB_UUID, kb.getUuid()))
                .maxResults(resolveTopK(topK, kb))
                .minScore(null == kb.getRetrieveMinScore() ? AdiConstant.RAG_MIN_SCORE : kb.getRetrieveMinScore())
                .breakIfSearchMissed(false)
                .excludedItemUuids(disabledDocUuids)
                .context(ctx)
                .kbUuid(kb.getUuid())
                .build();

        // ===== 检索内核复用点 =====
        // 与 KnowledgeBaseService.doBlockingAsk:945 同一入口：EmbeddingRag.createRetriever
        // 返回 AdiEmbeddingStoreContentRetriever，retrieve() 内部执行
        // embeddingStore.search()（全仓库唯一底层检索入口）+ ACL 过滤 + 双保险 + 检索日志
        AdiEmbeddingStoreContentRetriever retriever = rag.createRetriever(createParam);
        List<Content> contents = retriever.retrieve(Query.from(query));
        return toItems(contents, retriever);
    }

    /**
     * Content → 响应项：分数来自内核登记的 embeddingToScore（经 SegmentHitResolver 映射回段），
     * docUuid/docTitle 优先取内核展开器回填的 metadata，缺失时由关系行补齐。
     */
    private List<ExtKbRetrieveResp.Item> toItems(List<Content> contents, AdiEmbeddingStoreContentRetriever retriever) {
        if (null == contents || contents.isEmpty()) {
            return List.of();
        }
        Map<String, Double> embeddingToScore = retriever.getRetrievedEmbeddingToScore();
        SegmentHitResolver.Resolution resolution = SegmentHitResolver.resolve(
                documentSegmentService, documentSegmentQuestionService, documentSegmentChildChunkService,
                new ArrayList<>(embeddingToScore.keySet()));
        // segmentId → 最高分（父子模式下多个子块命中同一父段时取最优）
        Map<Long, Double> scoreBySegmentId = new HashMap<>();
        Map<Long, DocumentSegment> segmentById = new HashMap<>();
        embeddingToScore.forEach((embeddingId, score) -> {
            SegmentHitResolver.Hit hit = resolution.resolveOne(embeddingId);
            if (null == hit || null == hit.getSegmentId() || null == score) {
                return;
            }
            scoreBySegmentId.merge(hit.getSegmentId(), score, Math::max);
            if (null != hit.getSegment()) {
                segmentById.put(hit.getSegmentId(), hit.getSegment());
            }
        });

        Set<String> docUuids = new HashSet<>();
        List<ExtKbRetrieveResp.Item> items = new ArrayList<>(contents.size());
        for (Content content : contents) {
            Map<String, Object> meta = null == content.textSegment() || null == content.textSegment().metadata()
                    ? new HashMap<>() : content.textSegment().metadata().toMap();
            Long segmentId = parseSegmentId(meta.get(AdiConstant.MetadataKey.SEGMENT_ID));
            String docUuid = str(meta.get(AdiConstant.MetadataKey.DOC_UUID));
            String docTitle = str(meta.get(AdiConstant.MetadataKey.DOC_TITLE));
            if (StringUtils.isBlank(docUuid) && null != segmentId && null != segmentById.get(segmentId)) {
                docUuid = segmentById.get(segmentId).getDocUuid();
            }
            if (StringUtils.isNotBlank(docUuid)) {
                docUuids.add(docUuid);
            }
            items.add(ExtKbRetrieveResp.Item.builder()
                    .content(content.textSegment() == null ? "" : content.textSegment().text())
                    .score(null == segmentId ? null : scoreBySegmentId.get(segmentId))
                    .docUuid(docUuid)
                    .docTitle(docTitle)
                    .kbUuid(str(meta.get(AdiConstant.MetadataKey.KB_UUID)))
                    .metadata(whitelist(meta))
                    .build());
        }
        // metadata 里没带 doc_title 的（非展开路径），用文档标题映射补齐
        if (!docUuids.isEmpty()) {
            Map<String, String> titles = kbDocumentService.mapTitlesByUuids(docUuids);
            for (ExtKbRetrieveResp.Item item : items) {
                if (StringUtils.isBlank(item.getDocTitle()) && StringUtils.isNotBlank(item.getDocUuid())) {
                    item.setDocTitle(titles.get(item.getDocUuid()));
                }
            }
        }
        return items;
    }

    private Map<String, Object> whitelist(Map<String, Object> metadata) {
        Map<String, Object> result = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (METADATA_WHITELIST.contains(key)) {
                result.put(key, value);
            }
        });
        return result;
    }

    /**
     * topK 解析：请求值 > 库配置 > 全局默认，最终受 adi.ext.max-top-k 封顶。
     */
    private int resolveTopK(Integer topK, KnowledgeBase kb) {
        int maxTopK = null == adiProperties.getExt() ? 50 : Math.max(1, adiProperties.getExt().getMaxTopK());
        int resolved;
        if (null != topK && topK > 0) {
            resolved = topK;
        } else {
            Integer kbTop = kb.getRetrieveMaxResults();
            resolved = (null == kbTop || kbTop <= 0) ? AdiConstant.RAG_RETRIEVE_NUMBER_MAX : kbTop;
        }
        return Math.max(1, Math.min(resolved, maxTopK));
    }

    private boolean isReadable(KnowledgeBase kb, User user) {
        return KbAclFilterBuilder.toAclInfo(kb).visibleTo(
                user.getUuid(), Boolean.TRUE.equals(user.getIsAdmin()), null);
    }

    private KnowledgeBase getKbOrNull(String kbUuid) {
        // 复用 KnowledgeBaseService.getOrThrow 的唯一口径（按 uuid + 未删除），
        // 查不到（含已删除）BaseException 视为不存在 → 走 skipped 语义
        try {
            return knowledgeBaseService.getOrThrow(kbUuid);
        } catch (BaseException e) {
            return null;
        }
    }

    private static String str(Object value) {
        return null == value ? null : String.valueOf(value);
    }

    private static Long parseSegmentId(Object value) {
        if (null == value) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
