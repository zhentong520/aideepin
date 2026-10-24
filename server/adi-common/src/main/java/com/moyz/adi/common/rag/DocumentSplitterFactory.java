package com.moyz.adi.common.rag;

import com.moyz.adi.common.entity.KnowledgeBase;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.moyz.adi.common.constant.AdiConstant.SplitStrategy.*;

/**
 * 切分器的唯一入口：参数守卫 + 异常降级都收在这里。
 * <p>
 * Single entry point for creating a splitter: parameter guards and failure fallback live here.
 * <p>
 * 为什么需要守卫 / Why the guards
 * 知识库的分块参数来自数据库（{@code ingest_split_strategy / ingest_max_segment_size /
 * ingest_max_overlap / ingest_custom_separator}），历史上出现过三类「一跑就炸」的局面：
 * 策略值为空或拼错、自定义分隔符为空（{@code Pattern.quote(null)} 直接 NPE）、
 * overlap ≥ size（langchain4j 直接抛参）。任何一种都让整个文档的索引任务失败，
 * 用户只看到一句「索引失败」。守卫把非法参数就地修正后再切，保证「能跑出块」优先。
 * <p>
 * 为什么需要降级 / Why the fallback
 * 切分器内部（尤其是自定义分词器与 token 估算器）遇到极端文本仍可能抛异常。一次异常就丢掉
 * 整篇文档的索引，代价远大于「切得没那么漂亮」——所以这里在 splitter 抛异常时退化为按字符窗口
 * 的硬切，并把降级原因一并返回，由预览界面显式提示（真实索引只记 warn 日志）。
 */
@Slf4j
public class DocumentSplitterFactory {

    /**
     * 老数据回退值：迁移 024 之前的知识库行带着 1000 / 0 的默认值，1000 token 会把一个段
     * 该承载的单一语义稀释掉，这里回退到 512。
     */
    private static final int DEFAULT_MAX_SEGMENT_SIZE = 512;

    /**
     * overlap 回退值保持 0。langchain4j 是把上一块的最后一个子段整段追加过来，中文长段没有
     * 子切分点时那个子段就是整块：小 overlap 没效果，大 overlap 等于整块复制。见迁移 024。
     */
    private static final int DEFAULT_MAX_OVERLAP = 0;

    private DocumentSplitterFactory() {
    }

    public static DocumentSplitter create(String strategy, int maxSegmentSize,
                                          int overlap, String customSeparator,
                                          TokenCountEstimator estimator) {
        return switch (strategy) {
            case PARAGRAPH -> new DocumentByParagraphSplitter(maxSegmentSize, overlap, estimator);
            case LINE -> new DocumentByLineSplitter(maxSegmentSize, overlap, estimator);
            case SENTENCE -> new DocumentBySentenceSplitter(maxSegmentSize, overlap, estimator);
            case CUSTOM -> new CustomSeparatorSplitter(maxSegmentSize, overlap, customSeparator, estimator);
            case SENTENCE_CN -> new CjkSentenceSplitter(maxSegmentSize, overlap, estimator);
            default -> DocumentSplitters.recursive(maxSegmentSize, overlap, estimator);
        };
    }

    /**
     * 按知识库配置创建切分器，非法参数就地修正：策略空/未知 → recursive，size 非法 → 512，
     * overlap ≥ size → 夹到 size/4，自定义分隔符为空 → 退回 recursive（空分隔符会 NPE）。
     * <p>
     * Create a splitter from the KB's ingest settings, repairing illegal values in place.
     */
    public static DocumentSplitter create(KnowledgeBase kb, Integer maxSegmentSize) {
        int size = resolveSegmentSize(maxSegmentSize);
        int overlap = resolveOverlap(size, null == kb ? null : kb.getIngestMaxOverlap());
        String strategy = normalizeStrategy(null == kb ? null : kb.getIngestSplitStrategy());
        String separator = null == kb ? null : kb.getIngestCustomSeparator();
        if (CUSTOM.equals(strategy) && StringUtils.isBlank(separator)) {
            log.warn("Custom split strategy selected but no separator configured, falling back to recursive");
            strategy = RECURSIVE;
        }
        String estimatorName = null == kb ? null : kb.getIngestTokenEstimator();
        return create(strategy, size, overlap, separator, TokenEstimatorFactory.create(estimatorName));
    }

    /**
     * 按显式参数创建切分器（分块预览用：页面上临时改的参数优先于知识库配置）。
     */
    public static DocumentSplitter create(KnowledgeBase kb, String strategy, Integer maxSegmentSize,
                                          Integer overlap, String customSeparator) {
        int size = resolveSegmentSize(maxSegmentSize);
        int resolvedOverlap = resolveOverlap(size, overlap);
        String resolvedStrategy = normalizeStrategy(strategy);
        if (CUSTOM.equals(resolvedStrategy) && StringUtils.isBlank(customSeparator)) {
            resolvedStrategy = RECURSIVE;
        }
        String estimatorName = null == kb ? null : kb.getIngestTokenEstimator();
        return create(resolvedStrategy, size, resolvedOverlap, customSeparator, TokenEstimatorFactory.create(estimatorName));
    }

    /**
     * 安全切分：切分器抛异常时退化为按字符窗口硬切，保证「跑得完」优于「跑得漂亮」。
     *
     * @return 切分结果与降级提示（未降级时 warning 为 null）
     */
    public static SplitOutcome splitSafely(Document document, DocumentSplitter splitter, int maxSegmentSize) {
        try {
            return new SplitOutcome(splitter.split(document), null);
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName() + ": " + StringUtils.defaultString(e.getMessage());
            log.warn("Splitter failed ({}), falling back to character-window splitting, size:{}", reason, maxSegmentSize);
            try {
                return new SplitOutcome(fallbackSplit(document.text(), maxSegmentSize),
                        "切分器异常已降级为按字符切分 | Splitter failed, fell back to character-window splitting: " + reason);
            } catch (Exception fallbackError) {
                log.error("Fallback splitting failed as well", fallbackError);
                return new SplitOutcome(List.of(), "切分失败 | Splitting failed: " + reason);
            }
        }
    }

    /**
     * 兜底切分：按字符窗口滑动，优先在句末标点 / 换行处收尾，保证块内不被拦腰截断。
     * 只在切分器本身失败时使用。
     */
    static List<TextSegment> fallbackSplit(String text, int maxSegmentSize) {
        List<TextSegment> result = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }
        // 中文约 1 字 ≈ 1 token，西文约 4 字符 ≈ 1 token；取 3 倍字符窗口，兼顾两种语言都不至于超长
        int window = Math.max(200, maxSegmentSize * 3);
        int index = 0;
        int length = text.length();
        while (index < length) {
            int end = Math.min(length, index + window);
            if (end < length) {
                int boundary = lastBoundary(text, index + window / 2, end);
                if (boundary > index) {
                    end = boundary;
                }
            }
            String piece = text.substring(index, end).trim();
            if (!piece.isEmpty()) {
                result.add(TextSegment.from(piece));
            }
            index = end;
        }
        return result;
    }

    /**
     * 在 [start, end) 里找最后一个句末标点或换行，返回其后的位置；没有则返回 -1。
     */
    private static int lastBoundary(String text, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            char c = text.charAt(i);
            if (c == '\n' || c == '。' || c == '！' || c == '？' || c == '；' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return -1;
    }

    /**
     * 段大小：null 或非正数（老数据）回退 {@link #DEFAULT_MAX_SEGMENT_SIZE}。
     */
    public static int resolveSegmentSize(Integer maxSegmentSize) {
        return maxSegmentSize == null || maxSegmentSize < 1 ? DEFAULT_MAX_SEGMENT_SIZE : maxSegmentSize;
    }

    /**
     * 重叠：null（老数据）回退 0，显式 0 表示关闭；langchain4j 不接受 overlap ≥ size，
     * 这里夹到 size/4 而不是让整个索引任务失败。
     */
    public static int resolveOverlap(int size, Integer configuredOverlap) {
        int overlap = configuredOverlap == null || configuredOverlap < 0 ? DEFAULT_MAX_OVERLAP : configuredOverlap;
        if (overlap >= size) {
            return Math.max(0, size / 4);
        }
        return overlap;
    }

    /**
     * 策略名归一：空 / 未知 / 大小写不一致一律回退 recursive。
     */
    public static String normalizeStrategy(String strategy) {
        if (StringUtils.isBlank(strategy)) {
            return RECURSIVE;
        }
        String normalized = strategy.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case PARAGRAPH, LINE, SENTENCE, CUSTOM, SENTENCE_CN, RECURSIVE -> normalized;
            default -> {
                log.warn("Unknown split strategy '{}', falling back to recursive", strategy);
                yield RECURSIVE;
            }
        };
    }

    /**
     * 切分结果：块列表 + 降级提示（正常为 null）。
     */
    public record SplitOutcome(List<TextSegment> segments, String warning) {
    }
}
