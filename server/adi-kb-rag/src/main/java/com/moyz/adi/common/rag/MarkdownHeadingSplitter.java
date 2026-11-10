package com.moyz.adi.common.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 标题层级结构化切分器（解析升级 P1 配套，2026-11-05）。
 * <p>
 * Why / 为什么需要它：
 * MinerU（以及用户上传的 .md）输出的是带 ATX 标题（{@code # ~ ######}）的 Markdown。
 * 纯定长/递归切分会把「一个三级标题下的一整节」拦腰截断，检索命中的片段缺失标题语境，
 * 模型拿到「孤立的一段话」答不准。本切分器按标题边界切节，并把祖先标题路径作为前缀
 * 注入每个块（如 {@code 第一章 > 第二节 > 三、实施步骤}），块自带上下文，召回即可读。
 * <p>
 * Behavior / 行为：
 * <ul>
 *   <li>标题行判定：行首 1~6 个 {@code #} 后跟空白（ATX 风格，MinerU 即此格式）。</li>
 *   <li>每个标题节生成若干块：节体 token 数 ≤ maxSegmentSize 时整节一块；
 *       超长节先按段落递归切，再逐块补标题前缀（前缀计入长度预算）。</li>
 *   <li>首个标题前的正文（文档导语）独立成节，前缀为空。</li>
 *   <li>整篇没有标题：退化为 recursive 切分，与历史行为一致（无行为回归）。</li>
 *   <li>图片占位（{@code ![caption](/file/uuid.png)}）原样保留在所属节内，随块一起向量化。</li>
 * </ul>
 * <p>
 * 前缀长度预算：标题路径过长时截断到 maxSegmentSize 的 1/4，避免「全是标题没有正文」。
 */
@Slf4j
public class MarkdownHeadingSplitter implements DocumentSplitter {

    private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,6})\\s+(.*)$");

    private final int maxSegmentSize;
    private final int overlap;
    private final TokenCountEstimator estimator;

    public MarkdownHeadingSplitter(int maxSegmentSize, int overlap, TokenCountEstimator estimator) {
        this.maxSegmentSize = Math.max(1, maxSegmentSize);
        this.overlap = Math.max(0, overlap);
        this.estimator = estimator;
    }

    /** 单条 Markdown 文本中的 ATX 标题行数（EmbeddingRag 自动升级判断用） */
    public static int countHeadings(String text) {
        if (StringUtils.isBlank(text)) {
            return 0;
        }
        int count = 0;
        for (String line : text.split("\n", -1)) {
            if (HEADING_LINE.matcher(line.trim()).matches()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public List<TextSegment> split(Document document) {
        String text = null == document ? "" : document.text();
        List<TextSegment> result = new ArrayList<>();
        if (StringUtils.isBlank(text)) {
            return result;
        }
        int headingCount = countHeadings(text);
        if (headingCount == 0) {
            // 无标题：退化为 recursive，与历史默认行为一致
            return DocumentSplitters.recursive(maxSegmentSize, overlap, estimator).split(document);
        }
        // 前缀预算：最多占块大小的 1/4，且保底 24 token，防止超长标题把正文挤没
        int prefixBudget = Math.max(24, maxSegmentSize / 4);

        List<String> headingStack = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        // 首个标题前的导语也作为一节处理（路径为空）
        List<Section> sections = new ArrayList<>();
        for (String rawLine : text.split("\n", -1)) {
            var matcher = HEADING_LINE.matcher(rawLine.trim());
            if (matcher.matches()) {
                if (body.length() > 0) {
                    sections.add(new Section(List.copyOf(headingStack), body.toString()));
                    body.setLength(0);
                }
                int level = matcher.group(1).length();
                String title = matcher.group(2).trim();
                // 维护标题栈：弹出同级及更深层级后压入当前标题
                while (headingStack.size() >= level) {
                    headingStack.remove(headingStack.size() - 1);
                }
                headingStack.add(title);
            } else {
                body.append(rawLine).append('\n');
            }
        }
        if (body.length() > 0) {
            sections.add(new Section(List.copyOf(headingStack), body.toString()));
        }

        for (Section section : sections) {
            String prefix = buildPrefix(section.path(), prefixBudget);
            String content = section.body().trim();
            if (content.isEmpty()) {
                continue;
            }
            if (estimator.estimateTokenCountInText(prefix + content) <= maxSegmentSize) {
                result.add(TextSegment.from(prefix + content, document.metadata()));
                continue;
            }
            // 超长节：按段落递归切，逐块补前缀（元信息随块保留）
            Document sectionDoc = Document.from(content, document.metadata());
            DocumentSplitter child = DocumentSplitters.recursive(maxSegmentSize, overlap, estimator);
            for (TextSegment seg : child.split(sectionDoc)) {
                result.add(TextSegment.from(prefix + seg.text(), seg.metadata()));
            }
        }
        log.debug("MarkdownHeadingSplitter: {} sections -> {} segments, headings:{}", sections.size(), result.size(), headingCount);
        return result;
    }

    /**
     * 标题路径前缀：{@code A > B > C}，超预算时从后往前保留最近的标题（越深层的标题与块内容越相关）。
     */
    private String buildPrefix(List<String> path, int budget) {
        if (path.isEmpty()) {
            return "";
        }
        List<String> kept = new ArrayList<>();
        for (int i = path.size() - 1; i >= 0; i--) {
            List<String> candidate = new ArrayList<>(kept);
            candidate.add(0, path.get(i));
            if (estimator.estimateTokenCountInText(String.join(" > ", candidate) + "\n\n") > budget && !kept.isEmpty()) {
                break;
            }
            kept = candidate;
        }
        return String.join(" > ", kept) + "\n\n";
    }

    private record Section(List<String> path, String body) {
    }
}
