package com.moyz.adi.common.rag;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.mapper.DocumentSegmentMapper;
import com.moyz.adi.common.service.DocumentSegmentChildChunkService;
import com.moyz.adi.common.service.DocumentSegmentQuestionService;
import com.moyz.adi.common.service.DocumentSegmentService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.vo.KeywordSegmentHit;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 知识库向量命中的"按模式展开"处理器，落地附件检索流程图：
 * <pre>
 * 向量库 ANN 检索 → top-K 混着三种命中（text 分段、qa 问题、parent_child 子块）
 *   → 映射回关系层 segment，按模式展开：
 *       text → 直接用主表行内容
 *       qa   → 取答案内容
 *       parent_child → 上卷父段内容；多个子块命中同一父段时去重合并（保最高分）
 *   → (可选 关键词路 + RRF 融合) → (可选 rerank) → (可选 左右扩窗) → 出处抬头 → 拼进 prompt
 * </pre>
 * Hits without a relational row are dropped (vector-store text is only a placeholder).
 * 只把保留下来的命中写入 embeddingToScore，避免悬空引用与命中统计。
 * <p>
 * 二开（2026-09-15）：新增混合检索（关键词路 + RRF）与 rerank 两个阶段，
 * 均由 {@link RetrieverContext} 开关控制；关闭时行为与原来完全一致。
 */
@Slf4j
@Component
public class SegmentExpandProcessor implements RetrievedContentProcessor {

    /**
     * 标记内容来源，便于问题排查
     */
    private static final String META_RETRIEVE_FROM = "retrieve_from";
    private static final String FROM_VECTOR = "vector";
    private static final String FROM_KEYWORD = "keyword";
    /**
     * 扩窗带进来的邻段（不是命中本身），抬头里会标出来
     */
    private static final String FROM_NEIGHBOR = "neighbor";

    /**
     * 文档标题缺失时抬头里的占位名，保证抬头结构稳定而不是整行消失
     */
    private static final String UNKNOWN_DOC_TITLE = "未命名文档";

    @Resource
    private DocumentSegmentService documentSegmentService;

    @Resource
    private DocumentSegmentQuestionService questionService;

    @Resource
    private DocumentSegmentChildChunkService childChunkService;

    @Resource
    private DocumentSegmentMapper documentSegmentMapper;

    /**
     * rerank 扩展点：{@link RerankContentReranker} 为 Primary 实现，按知识库开关决定是否调用模型
     */
    @Resource
    private ContentReranker contentReranker;

    /**
     * 出处抬头数据源（文档 uuid → 标题）。{@code @Lazy} 规避 rag → 文档服务 → rag 的注入成环；
     * 检索路径只在命中后用它做一次批量回表。
     */
    @Lazy
    @Resource
    private KbDocumentService kbDocumentService;

    /**
     * 迁移脚本未执行导致关键词列缺失时，只告警一次，避免每次检索刷日志
     */
    private volatile boolean keywordPathBroken = false;

    @Override
    public List<Content> process(Query query, List<EmbeddingMatch<TextSegment>> matches, Map<String, Double> embeddingToScore, RetrieverContext context) {
        RetrieverContext ctx = context == null ? RetrieverContext.none() : context;
        // 三段式出口，顺序不可换：
        //   1) doProcess —— 展开 / RRF 去重 / rerank，全部按纯正文进行，分数与排序口径不受后面影响
        //   2) 左右扩窗 —— 排在 rerank 之后，邻段才不会参与打分、也不会挤占 rerank 的保留名额
        //   3) 出处抬头 —— 排在最后，扩出来的邻段同样拿到「文档 · 第N段」抬头
        // 三件事都只改返回给 LLM 的文本，不碰向量化输入，因此无需重建索引即可对历史数据生效。
        return withCitation(withNeighborWindow(doProcess(query, matches, embeddingToScore, ctx), ctx));
    }

    private List<Content> doProcess(Query query, List<EmbeddingMatch<TextSegment>> matches, Map<String, Double> embeddingToScore, RetrieverContext context) {
        RetrieverContext ctx = context == null ? RetrieverContext.none() : context;

        if (matches == null || matches.isEmpty()) {
            // 向量路无命中时，开启混合检索仍可单独靠关键词路兜底
            if (isHybridUsable(ctx)) {
                List<Content> keywordOnly = keywordRetrieve(query.text(), ctx);
                return contentReranker.rerank(query.text(), keywordOnly, ctx);
            }
            return Collections.emptyList();
        }

        List<Content> vectorContents = expand(query, matches, embeddingToScore);

        if (!isHybridUsable(ctx)) {
            return contentReranker.rerank(query.text(), vectorContents, ctx);
        }

        List<Content> keywordContents = keywordRetrieve(query.text(), ctx);
        if (keywordContents.isEmpty()) {
            return contentReranker.rerank(query.text(), vectorContents, ctx);
        }

        // RRF 融合：按内容文本去重（两路内容均来自同一张 segment 表，文本一致即同一段）
        List<Content> fused = RrfFusion.fuse(List.of(vectorContents, keywordContents), ctx.getRrfK(),
                item -> item.textSegment() == null ? "" : item.textSegment().text());
        log.info("hybrid retrieve done,vector:{},keyword:{},fused:{}", vectorContents.size(), keywordContents.size(), fused.size());
        return contentReranker.rerank(query.text(), fused, ctx);
    }

    /**
     * 原有的向量命中按模式展开逻辑。
     * <p>
     * 三种形态的映射统一走 {@link SegmentHitResolver}（2026-09-30 抽出），与召回测试页面同一口径；
     * 本方法只负责"命中分数登记 + 子块上卷去重 + 构造 Content"这三件与检索链路强相关的事。
     */
    private List<Content> expand(Query query, List<EmbeddingMatch<TextSegment>> matches, Map<String, Double> embeddingToScore) {
        List<String> embeddingIds = matches.stream().map(EmbeddingMatch::embeddingId).toList();
        SegmentHitResolver.Resolution resolution = SegmentHitResolver.resolve(
                documentSegmentService, questionService, childChunkService, embeddingIds);

        List<Content> result = new ArrayList<>();
        Set<Long> emittedParents = new HashSet<>();
        // matches 按分数降序；父段去重保留最高分命中
        for (EmbeddingMatch<TextSegment> match : matches) {
            String embeddingId = match.embeddingId();
            SegmentHitResolver.Hit hit = resolution.resolveOne(embeddingId);
            if (null == hit) {
                // No relational row (dirty data) → drop; vector-store text is only a placeholder
                log.warn("No relational segment row for embedding hit {}, dropped", embeddingId);
                continue;
            }
            // 分数登记先于父段去重：多个子块命中同一父段时，每个子块都算一次命中（用于命中统计），
            // 但送进上下文的内容只有父段一份
            embeddingToScore.put(embeddingId, match.score());
            if (null != hit.getParentSegmentId() && !emittedParents.add(hit.getParentSegmentId())) {
                continue;
            }
            result.add(toContent(match, hit.getText(), hit.getSegmentId()));
        }
        return result;
    }

    /**
     * 关键词路召回：bigram 切分 → PG tsvector 全文检索 → 构造 Content
     * <p>
     * 任何异常都吞掉并退化为空列表，保证向量路不受影响。
     */
    private List<Content> keywordRetrieve(String queryText, RetrieverContext ctx) {
        if (keywordPathBroken) {
            return Collections.emptyList();
        }
        // OR 语义串（物业|业管|管理）。不能用 plainto_tsquery：它是 AND 语义，长查询实测命中 0 条
        String tsQuery = BigramTokenizer.tokenizeForTsQuery(queryText);
        if (StringUtils.isBlank(tsQuery)) {
            return Collections.emptyList();
        }
        int limit = ctx.getKeywordLimit() > 0 ? ctx.getKeywordLimit() : 20;
        List<KeywordSegmentHit> hits;
        try {
            //Exclude disabled documents: the vector path filters them via metadata, and the
            //keyword path must honour the same list or a disabled document keeps resurfacing
            Set<String> excluded = ctx.getExcludedDocUuids();
            if (null == excluded || excluded.isEmpty()) {
                hits = documentSegmentMapper.keywordSearch(ctx.getKbUuid(), tsQuery, limit);
            } else {
                hits = documentSegmentMapper.keywordSearchExcluding(ctx.getKbUuid(), tsQuery, limit, excluded);
            }
        } catch (Exception e) {
            keywordPathBroken = true;
            log.error("keyword search failed,usually means migration 017 not applied; "
                    + "hybrid retrieval degraded to vector-only.msg:{}", e.getMessage());
            return Collections.emptyList();
        }
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }
        return hits.stream()
                .filter(hit -> StringUtils.isNotBlank(hit.getContent()))
                .map(hit -> {
                    Map<String, Object> meta = new HashMap<>();
                    meta.put(AdiConstant.MetadataKey.KB_UUID, ctx.getKbUuid());
                    meta.put(AdiConstant.MetadataKey.SEGMENT_ID, String.valueOf(hit.getId()));
                    meta.put(META_RETRIEVE_FROM, FROM_KEYWORD);
                    return Content.from(TextSegment.from(hit.getContent(), Metadata.from(meta)));
                })
                .collect(Collectors.toList());
    }

    private boolean isHybridUsable(RetrieverContext ctx) {
        return ctx.isHybridEnabled() && StringUtils.isNotBlank(ctx.getKbUuid());
    }

    /**
     * 用展开后的内容构造 Content，保留原命中的 metadata（kb_uuid/kb_item_uuid/segment_id 等）
     */
    private Content toContent(EmbeddingMatch<TextSegment> match, String expandedText, Long segmentId) {
        Map<String, Object> merged = new HashMap<>();
        Metadata metadata = match.embedded() == null ? null : match.embedded().metadata();
        if (metadata != null) {
            merged.putAll(metadata.toMap());
        }
        merged.put(AdiConstant.MetadataKey.SEGMENT_ID, String.valueOf(segmentId));
        merged.put(META_RETRIEVE_FROM, FROM_VECTOR);
        return Content.from(TextSegment.from(expandedText, Metadata.from(merged)));
    }

    /**
     * 左右扩窗：把每个命中在同一文档内的相邻段一并送进上下文。
     * <p>
     * 解决的问题：向量命中以「段」为单位，而答案所需信息常压在段边界上（条款与它的例外、标题与它下面的
     * 正文、表格与它的表头）。段切得越细命中越准，但单段能回答的完整度越低；扩窗把「检索单位」与
     * 「喂给模型的单位」解耦——仍按单段检索、仍按单段打分，返回时带上邻近段。
     * <p>
     * 三条不变量：
     * <ul>
     *   <li><b>不进命中分数表</b>：邻段不写入 embeddingToScore，因此命中统计与前端「出处」列表里
     *       只会出现真正被召回的段，不会因为扩窗而虚增。</li>
     *   <li><b>全局去重</b>：两个命中相邻时它们共享的段只输出一次。</li>
     *   <li><b>命中自身永远用自己的 Content</b>：即便它是被另一个命中的窗口顺带带出来的，
     *       标签仍是命中而不是「上下文」。</li>
     * </ul>
     * 查询失败或开关关闭时原样返回——扩窗是增强而非依赖，不能让检索本身失败。
     */
    private List<Content> withNeighborWindow(List<Content> contents, RetrieverContext ctx) {
        int window = ctx.getNeighborWindow();
        if (contents == null || contents.isEmpty() || !ctx.isNeighborEnabled() || window < 1) {
            return contents;
        }
        Map<Long, Content> hitContentById = new HashMap<>();
        for (Content content : contents) {
            Long id = segmentIdOf(content);
            if (id != null) {
                hitContentById.putIfAbsent(id, content);
            }
        }
        if (hitContentById.isEmpty()) {
            return contents;
        }

        Map<Long, DocumentSegment> hits;
        Map<Long, DocumentSegment> windowRows;
        try {
            hits = documentSegmentService.mapByIds(hitContentById.keySet());
            if (hits.isEmpty()) {
                // 段行一个都没解析出来（例如全是脏数据）：没有 doc_uuid/position 就无从判断邻段，
                // 直接返回，不再白跑一次必然落空的邻段查询
                return contents;
            }
            windowRows = documentSegmentService.mapNeighborWindow(hits.values(), window);
        } catch (Exception e) {
            log.warn("neighbor window lookup failed, return hits only, msg:{}", e.getMessage());
            return contents;
        }
        if (windowRows == null || windowRows.isEmpty()) {
            return contents;
        }

        // 同文档的邻段按 position 升序，保证输出是阅读顺序而不是查询顺序
        Map<String, List<DocumentSegment>> byDoc = new HashMap<>();
        for (DocumentSegment segment : windowRows.values()) {
            if (StringUtils.isNotBlank(segment.getDocUuid())) {
                byDoc.computeIfAbsent(segment.getDocUuid(), k -> new ArrayList<>()).add(segment);
            }
        }
        byDoc.values().forEach(list -> list.sort(Comparator.comparingInt(
                s -> s.getPosition() == null ? Integer.MAX_VALUE : s.getPosition())));

        Set<Long> emitted = new HashSet<>();
        List<Content> widened = new ArrayList<>(contents.size() + hits.size() * 2 * window);
        int added = 0;
        for (Content content : contents) {
            Long hitId = segmentIdOf(content);
            DocumentSegment hit = hitId == null ? null : hits.get(hitId);
            if (hit == null || StringUtils.isBlank(hit.getDocUuid())) {
                // 段行缺失或没有文档归属：原样保留该命中，不做扩窗
                if (hitId != null) {
                    emitted.add(hitId);
                }
                widened.add(content);
                continue;
            }
            for (DocumentSegment around : byDoc.getOrDefault(hit.getDocUuid(), Collections.emptyList())) {
                if (!emitted.add(around.getId())) {
                    continue;
                }
                Content own = hitContentById.get(around.getId());
                if (own != null) {
                    // 邻段自己也是命中：用它的 Content，保留命中身份
                    widened.add(own);
                    continue;
                }
                widened.add(neighborContent(around, hit.getKbUuid()));
                added++;
            }
            if (!emitted.contains(hitId)) {
                // 窗口查询没带上命中本身（例如该行被停用过滤掉）时兜底
                emitted.add(hitId);
                widened.add(content);
            }
        }
        if (added > 0) {
            log.debug("neighbor window expanded,window:{},hits:{},added:{}", window, contents.size(), added);
        }
        return widened;
    }

    /**
     * 邻段 Content：只带 kb_uuid / segment_id 两个键。
     * <p>
     * 刻意不复制命中 Content 的 metadata——其中的 kb_item_uuid 指向的是命中所在文档，套到邻段上
     * 会给出错误的归属；文档/段序/标题由 {@link #withCitation} 按 segment_id 回表补齐。
     */
    private Content neighborContent(DocumentSegment segment, String kbUuid) {
        Map<String, Object> meta = new HashMap<>(4);
        if (StringUtils.isNotBlank(kbUuid)) {
            meta.put(AdiConstant.MetadataKey.KB_UUID, kbUuid);
        }
        meta.put(AdiConstant.MetadataKey.SEGMENT_ID, String.valueOf(segment.getId()));
        meta.put(META_RETRIEVE_FROM, FROM_NEIGHBOR);
        String text = segment.getContent() == null ? "" : segment.getContent();
        return Content.from(TextSegment.from(text, Metadata.from(meta)));
    }

    /**
     * 给返回给 LLM 的每段内容补一行出处抬头（文档标题 + 段序）。
     * <p>
     * 只重写返回文本，绝不参与向量化：段自身的 embedding 仍由纯正文算出，所以补抬头既不需要
     * 重建索引，也对历史数据立即生效。本方法在去重与 rerank 之后运行，因此不会改变两者的判定。
     * <p>
     * 回表失败或段行缺失时原样返回——抬头是增强而非依赖，不能让检索本身失败。
     * <p>
     * Rewrites the text handed to the LLM with a citation header. Runs after dedup and rerank
     * so it can change neither, and never touches the embedded input.
     */
    private List<Content> withCitation(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return contents;
        }
        Set<Long> segmentIds = contents.stream()
                .map(this::segmentIdOf)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (segmentIds.isEmpty()) {
            return contents;
        }
        Map<Long, DocumentSegment> segments;
        Map<String, String> titles;
        try {
            segments = documentSegmentService.mapByIds(segmentIds);
            Set<String> docUuids = segments.values().stream()
                    .map(DocumentSegment::getDocUuid)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());
            titles = kbDocumentService.mapTitlesByUuids(docUuids);
        } catch (Exception e) {
            log.warn("citation header lookup failed, return raw contents, msg:{}", e.getMessage());
            return contents;
        }

        List<Content> enriched = new ArrayList<>(contents.size());
        for (Content content : contents) {
            Long segmentId = segmentIdOf(content);
            DocumentSegment segment = segmentId == null ? null : segments.get(segmentId);
            if (segment == null) {
                enriched.add(content);
                continue;
            }
            String title = titles.get(segment.getDocUuid());
            Map<String, Object> merged = metadataOf(content);
            merged.put(AdiConstant.MetadataKey.DOC_UUID, segment.getDocUuid());
            if (segment.getPosition() != null) {
                merged.put(AdiConstant.MetadataKey.POSITION, String.valueOf(segment.getPosition()));
            }
            if (StringUtils.isNotBlank(title)) {
                merged.put(AdiConstant.MetadataKey.DOC_TITLE, title);
            }
            // 扩窗带进来的邻段要标出来：它不是召回结果，模型据此知道这段是补充的上下文
            boolean neighbor = FROM_NEIGHBOR.equals(merged.get(META_RETRIEVE_FROM));
            String header = citationHeader(title, segment.getPosition(), neighbor);
            enriched.add(Content.from(TextSegment.from(header + content.textSegment().text(), Metadata.from(merged))));
        }
        return enriched;
    }

    /**
     * 抬头格式：【文档标题 · 第 N 段】（段序按 position 从 1 起显示，对用户比 0 基下标自然）；
     * 扩窗带来的邻段追加「（上下文）」。段序缺失时只保留标题，标题缺失时用占位名，保证抬头结构不塌。
     */
    private String citationHeader(String title, Integer position, boolean neighbor) {
        String name = StringUtils.isBlank(title) ? UNKNOWN_DOC_TITLE : title;
        if (position == null) {
            return "【" + name + (neighbor ? " · 上下文" : "") + "】\n";
        }
        return "【" + name + " · 第 " + (position + 1) + " 段" + (neighbor ? "（上下文）" : "") + "】\n";
    }

    /**
     * 从 Content 的 metadata 取关系表主键。取不到或不是数字（例如向量库里的段 uuid 占位）
     * 一律返回 null，调用方按"无抬头"降级。
     */
    private Long segmentIdOf(Content content) {
        Metadata metadata = content == null || content.textSegment() == null
                ? null : content.textSegment().metadata();
        if (metadata == null) {
            return null;
        }
        String raw = metadata.getString(AdiConstant.MetadataKey.SEGMENT_ID);
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> metadataOf(Content content) {
        Metadata metadata = content == null || content.textSegment() == null
                ? null : content.textSegment().metadata();
        return metadata == null ? new HashMap<>() : new HashMap<>(metadata.toMap());
    }
}
