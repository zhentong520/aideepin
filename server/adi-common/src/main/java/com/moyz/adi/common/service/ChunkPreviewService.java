package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.ChunkPreviewDto;
import com.moyz.adi.common.dto.ChunkPreviewReq;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.rag.DocumentSplitterFactory;
import com.moyz.adi.common.rag.DocumentTextCleaner;
import com.moyz.adi.common.rag.TokenEstimatorFactory;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 分块预览（干跑）。
 * <p>
 * Chunk preview (dry run).
 * <p>
 * 商业背景 / Why
 * 入库链路此前是个黑盒：上传 → 解析 → 切分 → 向量化，中间产物一律不看。分块切得好不好只能靠
 * 「问一句看召回什么」来反推，而召回不准时根本分不清是解析没抽到、清洗没做、还是切碎了。
 * 用户反馈的原话是「不知道它是怎么拆的，无法给出更好的优化方案」。
 * <p>
 * 这里把整条链路摊开：解析原文（raw_text）→ 清洗（去掉多少字符/合并多少行/丢掉多少页眉行）
 * → 切分（多少块、每块多少字/token、哪些过长哪些过短）。所有参数都能在页面上临时改并立刻重跑，
 * 不写库、不花钱（不调 embedding），满意后再去改知识库的正式参数并重建索引。
 * <p>
 * 与真实索引的一致性 / Consistency with the real pipeline
 * 切分器、参数守卫、降级策略全部走 {@link DocumentSplitterFactory} 与 {@link SegmentIndexService}
 * 的同一条路径，预览看到的就是真实入库会切出来的东西（QA 模式除外：它的块来自问答对，不由正文切分）。
 */
@Slf4j
@Service
public class ChunkPreviewService {

    /**
     * 抽样片段长度：页面对照「解析 / 清洗」差异用，不返回整篇正文
     */
    private static final int SAMPLE_CHARS = 1200;

    /**
     * 父子模式预览时每个父块最多返回多少子块
     */
    private static final int MAX_CHILDREN_PER_PARENT = 20;

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private DocumentTextCleaner documentTextCleaner;

    @Resource
    private AdiProperties adiProperties;

    public ChunkPreviewDto preview(String docUuid, ChunkPreviewReq req) {
        ChunkPreviewDto dto = new ChunkPreviewDto();
        dto.setDocUuid(docUuid);
        ChunkPreviewReq params = null == req ? new ChunkPreviewReq() : req;

        KbDocument doc = kbDocumentService.getEnable(docUuid);
        if (null == doc) {
            dto.setWarning("文档不存在或已删除 | Document not found");
            dto.setChunks(List.of());
            dto.setTotalChunks(0);
            return dto;
        }
        KnowledgeBase kb = knowledgeBaseService.getOrThrow(doc.getKbUuid());
        SegmentModeEnum mode = SegmentIndexService.effectiveMode(doc);
        dto.setSegmentMode(mode.getValue());

        // 1) 解析原文：老文档没有 raw_text（迁移 032 之前入库），回退到 remark
        String raw = StringUtils.isBlank(doc.getRawText()) ? doc.getRemark() : doc.getRawText();
        raw = StringUtils.defaultString(raw);
        dto.setRawSample(StringUtils.substring(raw, 0, SAMPLE_CHARS));

        // 2) 清洗：页面可覆盖开关，用来做「开/关清洗」对照
        AdiProperties.Clean cleanConfig = resolveCleanConfig(params);
        DocumentTextCleaner.CleanResult cleanResult = documentTextCleaner.clean(raw, cleanConfig);
        String text = cleanResult.text();
        dto.setClean(toCleanStats(cleanResult));
        dto.setCleanedSample(StringUtils.substring(text, 0, SAMPLE_CHARS));

        // 3) 预览上限：超大文档截断，避免一次请求把接口和页面都拖垮（只影响预览）
        int maxChars = previewMaxChars();
        boolean truncated = text.length() > maxChars;
        if (truncated) {
            text = text.substring(0, maxChars);
        }
        dto.setTruncated(truncated);

        ChunkPreviewDto.Params effective = toParams(kb, doc, params, cleanConfig);
        dto.setParams(effective);

        int pageSize = clampPageSize(params.getPageSize());
        int page = null == params.getPage() || params.getPage() < 1 ? 1 : params.getPage();

        // token 估算器可能加载失败（HuggingFace 分词器缺文件等），退化成字符估算也不能让预览 500
        Function<String, Integer> tokenCounter = tokenCounter(kb);

        List<ChunkPreviewDto.ChunkItem> items;
        if (SegmentModeEnum.QA == mode) {
            items = List.of();
            dto.setWarning("QA 模式的块来自问答对，不由正文切分；预览只展示解析与清洗结果 | "
                    + "QA mode chunks come from QA pairs, not from splitting this text");
        } else {
            items = split(kb, doc, text, effective, tokenCounter);
        }

        ChunkPreviewDto.Stats stats = buildStats(items, effective.getMaxSegmentSize(), tokenCounter);
        dto.setStats(stats);
        dto.setTotalChunks(items.size());
        if (SegmentModeEnum.PARENT_CHILD == mode) {
            int childTotal = 0;
            for (ChunkPreviewDto.ChunkItem item : items) {
                if (null != item.getChildren()) {
                    childTotal += item.getChildren().size();
                }
            }
            dto.setChildChunkCount(childTotal);
        }

        int from = Math.min(items.size(), (page - 1) * pageSize);
        int to = Math.min(items.size(), from + pageSize);
        List<ChunkPreviewDto.ChunkItem> pageItems = items.subList(from, to);
        // 序号保持全量下标，和入库后的 position 对齐
        for (int i = from; i < to; i++) {
            pageItems.get(i - from).setIndex(i);
        }
        dto.setChunks(pageItems);
        return dto;
    }

    /**
     * 切分正文：父子模式额外产出子块。
     */
    private List<ChunkPreviewDto.ChunkItem> split(KnowledgeBase kb, KbDocument doc, String text,
                                                  ChunkPreviewDto.Params params,
                                                  Function<String, Integer> tokenCounter) {
        Document document = new DefaultDocument(text, baseMetadata(kb, doc));
        DocumentSplitter splitter = DocumentSplitterFactory.create(kb, params.getStrategy(),
                params.getMaxSegmentSize(), params.getOverlap(), params.getSeparator());
        DocumentSplitterFactory.SplitOutcome outcome = DocumentSplitterFactory.splitSafely(
                document, splitter, params.getMaxSegmentSize());
        List<ChunkPreviewDto.ChunkItem> items = new ArrayList<>();
        if (null != outcome.warning()) {
            log.warn("Chunk preview split degraded, docUuid:{}, reason:{}", doc.getUuid(), outcome.warning());
        }
        for (TextSegment segment : outcome.segments()) {
            if (StringUtils.isBlank(segment.text())) {
                continue;
            }
            items.add(toItem(segment.text(), params.getMaxSegmentSize(), tokenCounter));
        }
        if (SegmentModeEnum.PARENT_CHILD == SegmentIndexService.effectiveMode(doc)) {
            int childSize = null == params.getChildMaxChunkSize() || params.getChildMaxChunkSize() < 1
                    ? 200 : params.getChildMaxChunkSize();
            for (ChunkPreviewDto.ChunkItem parent : items) {
                List<ChunkPreviewDto.ChunkItem> children = splitChildren(kb, doc, parent.getContent(), childSize,
                        params.getStrategy(), tokenCounter);
                parent.setChildren(children);
            }
        }
        return items;
    }

    private List<ChunkPreviewDto.ChunkItem> splitChildren(KnowledgeBase kb, KbDocument doc, String parentContent,
                                                          int childSize, String strategy,
                                                          Function<String, Integer> tokenCounter) {
        // 与真实入库一致：子块沿用知识库的切分策略，重叠固定 0（子块是检索粒度，不需要重叠）
        DocumentSplitter childSplitter = DocumentSplitterFactory.create(kb, strategy, childSize, 0, null);
        DocumentSplitterFactory.SplitOutcome outcome = DocumentSplitterFactory.splitSafely(
                new DefaultDocument(parentContent, baseMetadata(kb, doc)), childSplitter, childSize);
        List<ChunkPreviewDto.ChunkItem> children = new ArrayList<>();
        for (TextSegment segment : outcome.segments()) {
            if (StringUtils.isBlank(segment.text())) {
                continue;
            }
            children.add(toItem(segment.text(), childSize, tokenCounter));
            if (children.size() >= MAX_CHILDREN_PER_PARENT) {
                break;
            }
        }
        return children;
    }

    private ChunkPreviewDto.ChunkItem toItem(String content, int size, Function<String, Integer> tokenCounter) {
        ChunkPreviewDto.ChunkItem item = new ChunkPreviewDto.ChunkItem();
        item.setContent(content);
        item.setCharCount(content.length());
        item.setTokenCount(tokenCounter.apply(content));
        item.setOversize(item.getTokenCount() > size);
        item.setTiny(item.getTokenCount() < Math.max(16, size * 0.1));
        return item;
    }

    private ChunkPreviewDto.Stats buildStats(List<ChunkPreviewDto.ChunkItem> items, int size,
                                             Function<String, Integer> tokenCounter) {
        ChunkPreviewDto.Stats stats = new ChunkPreviewDto.Stats();
        stats.setChunkCount(items.size());
        if (items.isEmpty()) {
            stats.setTotalChars(0);
            stats.setAvgChars(0);
            stats.setMinChars(0);
            stats.setMaxChars(0);
            stats.setAvgTokens(0);
            stats.setMaxTokens(0);
            stats.setOversizeCount(0);
            stats.setTinyCount(0);
            return stats;
        }
        int total = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        int tokenMax = 0;
        int tokenTotal = 0;
        int oversize = 0;
        int tiny = 0;
        for (ChunkPreviewDto.ChunkItem item : items) {
            int chars = null == item.getCharCount() ? 0 : item.getCharCount();
            int tokens = null == item.getTokenCount() ? tokenCounter.apply(item.getContent()) : item.getTokenCount();
            total += chars;
            tokenTotal += tokens;
            min = Math.min(min, chars);
            max = Math.max(max, chars);
            tokenMax = Math.max(tokenMax, tokens);
            if (tokens > size) {
                oversize++;
            }
            if (tokens < Math.max(16, size * 0.1)) {
                tiny++;
            }
        }
        stats.setTotalChars(total);
        stats.setAvgChars(total / items.size());
        stats.setMinChars(min);
        stats.setMaxChars(max);
        stats.setAvgTokens(tokenTotal / items.size());
        stats.setMaxTokens(tokenMax);
        stats.setOversizeCount(oversize);
        stats.setTinyCount(tiny);
        stats.setChunkCount(items.size());
        return stats;
    }

    private AdiProperties.Clean resolveCleanConfig(ChunkPreviewReq req) {
        AdiProperties.Ingest ingest = adiProperties.getIngest();
        AdiProperties.Clean base = null == ingest || null == ingest.getClean() ? new AdiProperties.Clean() : ingest.getClean();
        AdiProperties.Clean config = copy(base);
        if (null != req.getClean()) {
            config.setEnabled(req.getClean());
        }
        if (null != req.getMergeSoftLineBreaks()) {
            config.setMergeSoftLineBreaks(req.getMergeSoftLineBreaks());
        }
        if (null != req.getDropDuplicateLines()) {
            config.setDropDuplicateLines(req.getDropDuplicateLines());
        }
        return config;
    }

    private static AdiProperties.Clean copy(AdiProperties.Clean source) {
        AdiProperties.Clean target = new AdiProperties.Clean();
        target.setEnabled(source.isEnabled());
        target.setMergeSoftLineBreaks(source.isMergeSoftLineBreaks());
        target.setMaxConsecutiveMerge(source.getMaxConsecutiveMerge());
        target.setDropDuplicateLines(source.isDropDuplicateLines());
        target.setDuplicateLineMinOccurrences(source.getDuplicateLineMinOccurrences());
        target.setDuplicateLineMaxLength(source.getDuplicateLineMaxLength());
        return target;
    }

    private ChunkPreviewDto.Params toParams(KnowledgeBase kb, KbDocument doc, ChunkPreviewReq req,
                                            AdiProperties.Clean cleanConfig) {
        ChunkPreviewDto.Params params = new ChunkPreviewDto.Params();
        String strategy = StringUtils.isBlank(req.getStrategy()) ? kb.getIngestSplitStrategy() : req.getStrategy();
        params.setStrategy(DocumentSplitterFactory.normalizeStrategy(strategy));
        int size = DocumentSplitterFactory.resolveSegmentSize(
                null == req.getMaxSegmentSize() ? kb.getIngestMaxSegmentSize() : req.getMaxSegmentSize());
        params.setMaxSegmentSize(size);
        Integer overlap = null == req.getOverlap() ? kb.getIngestMaxOverlap() : req.getOverlap();
        params.setOverlap(DocumentSplitterFactory.resolveOverlap(size, overlap));
        params.setSeparator(StringUtils.isBlank(req.getSeparator()) ? kb.getIngestCustomSeparator() : req.getSeparator());
        params.setTokenEstimator(StringUtils.isBlank(kb.getIngestTokenEstimator()) ? "openai" : kb.getIngestTokenEstimator());
        params.setCleanEnabled(cleanConfig.isEnabled());
        params.setMergeSoftLineBreaks(cleanConfig.isMergeSoftLineBreaks());
        params.setDropDuplicateLines(cleanConfig.isDropDuplicateLines());
        Integer childSize = null == req.getChildMaxChunkSize() ? doc.getChildMaxChunkSize() : req.getChildMaxChunkSize();
        params.setChildMaxChunkSize(childSize == null || childSize < 1 ? 200 : childSize);
        return params;
    }

    private ChunkPreviewDto.CleanStats toCleanStats(DocumentTextCleaner.CleanResult result) {
        ChunkPreviewDto.CleanStats stats = new ChunkPreviewDto.CleanStats();
        stats.setApplied(result.applied());
        stats.setRawChars(result.rawChars());
        stats.setCleanedChars(result.cleanedChars());
        stats.setRemovedChars(result.removedChars());
        stats.setRawLines(result.rawLines());
        stats.setCleanedLines(result.cleanedLines());
        stats.setMergedLines(result.mergedLines());
        stats.setDroppedLines(result.droppedLines());
        return stats;
    }

    /**
     * token 估算：估算器不可用（模型文件缺失 / 分词器初始化失败）时退化为字符估算，
     * 宁可数字不精确也不能让预览报错——这正是「看一眼切分结果」这个功能的存在意义。
     */
    private Function<String, Integer> tokenCounter(KnowledgeBase kb) {
        try {
            TokenCountEstimator estimator = TokenEstimatorFactory.create(kb.getIngestTokenEstimator());
            // 探针：先算一次，确认分词器真的可用
            estimator.estimateTokenCountInText("预览探针 preview probe");
            return text -> {
                try {
                    return estimator.estimateTokenCountInText(text);
                } catch (Exception e) {
                    return estimateByChars(text);
                }
            };
        } catch (Exception e) {
            log.warn("Token estimator unavailable ({}), chunk preview falls back to character estimation", e.getMessage());
            return ChunkPreviewService::estimateByChars;
        }
    }

    /**
     * 粗略估算：中文约 1 字 1 token，西文约 4 字符 1 token，混排取折中。
     */
    private static int estimateByChars(String text) {
        if (StringUtils.isEmpty(text)) {
            return 0;
        }
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) >= '\u4e00' && text.charAt(i) <= '\u9fff') {
                cjk++;
            }
        }
        int ascii = text.length() - cjk;
        return Math.max(1, cjk + ascii / 4);
    }

    private int previewMaxChars() {
        AdiProperties.Ingest ingest = adiProperties.getIngest();
        return null == ingest || ingest.getPreviewMaxChars() < 1000 ? 400_000 : ingest.getPreviewMaxChars();
    }

    private static int clampPageSize(Integer pageSize) {
        if (null == pageSize || pageSize < 1) {
            return 50;
        }
        return Math.min(pageSize, 200);
    }

    private Metadata baseMetadata(KnowledgeBase kb, KbDocument doc) {
        Metadata metadata = new Metadata();
        metadata.put(com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.KB_UUID, kb.getUuid());
        metadata.put(com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.KB_ITEM_UUID, doc.getUuid());
        return metadata;
    }
}
