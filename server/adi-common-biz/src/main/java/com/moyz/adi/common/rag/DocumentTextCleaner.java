package com.moyz.adi.common.rag;

import com.moyz.adi.common.config.AdiProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解析之后、切分之前的文本清洗。
 * <p>
 * Cleanup step sitting between document parsing and chunking.
 * <p>
 * 商业背景 / Why this exists
 * 解析器（PDFBox / POI / Jsoup / TXT）交出来的正文带着大量「排版噪声」：PDF 抽取塞进的控制字符与
 * 零宽字符、页眉页脚在每一页重复一次、一段中文被排版拆成十几行、连续七八个空行。这些噪声直接喂给
 * 切分器，结果是同一句话被切成两块、块与块之间塞满空白、embedding 被页眉稀释——而排查时根本看不见
 * 中间产物，只能靠猜。这里把噪声处理固定成一个可开关、可量化的环节，并且把「清洗前 / 清洗后」的
 * 字符数、行数、合并行数、丢弃行数都返回出来，让分块预览能直接把这一步摆到用户眼前。
 * <p>
 * Parsers (PDFBox / POI / Jsoup / plain text) hand over text full of layout noise: control and
 * zero-width characters, headers repeated once per page, Chinese paragraphs broken into a dozen
 * lines, runs of blank lines. Fed straight into a splitter that noise splits one sentence across
 * two chunks and dilutes embeddings with boilerplate — and because the intermediate text was never
 * visible, tuning was guesswork. This cleaner makes the step explicit, switchable and measurable:
 * every run reports the before/after character and line counts.
 * <p>
 * 安全边界 / Safety
 * 默认只做「不改变语义」的整理：删控制字符与零宽字符、归一换行、去行尾空白、压缩连续空行。
 * 会改变阅读顺序的两项（合并软换行、丢弃重复短行）默认关闭，且都由配置控制，因为它们对表格、
 * 诗歌、代码块和正文里的合法重复有副作用。
 * <p>
 * By default only semantically neutral normalisation runs. The two transforms that change reading
 * order (merging soft line breaks, dropping repeated short lines) are off unless explicitly enabled.
 */
@Slf4j
@Component
public class DocumentTextCleaner {

    /**
     * 控制字符：保留 \n 与 \t，其余（含 NUL、响铃、转义）一律删除。\f 单独处理成换行。
     */
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0E-\\x1F\\x7F]");

    /**
     * 零宽字符：PDF / 网页抽取的常见副产物，肉眼看不出但会切坏句子。
     */
    private static final Pattern ZERO_WIDTH = Pattern.compile("[\\u200B\\u200C\\u200D\\uFEFF]");

    private static final Pattern CRLF = Pattern.compile("\\r\\n?");

    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    /**
     * 行尾句末标点：以它结尾的行视为一个自然段的结束，不与下一行合并。
     */
    private static final Pattern LINE_END_STOP = Pattern.compile("[。！？；…:.!?;\\)\\]】》」\"'%,，、]$");

    /**
     * 行首块标记：列表、引用、标题、表格、代码块——这些行的换行是结构，不能合并。
     */
    private static final Pattern LINE_START_BLOCK = Pattern.compile("^\\s*([#>*|\\-•·]|[0-9０-９]+[.、)）]|[（(\\[【「《])");

    private final AdiProperties adiProperties;

    public DocumentTextCleaner(AdiProperties adiProperties) {
        this.adiProperties = adiProperties;
    }

    /**
     * 按全局配置 {@code adi.ingest.clean} 清洗。
     *
     * @return 清洗结果，包含清洗后的文本与可量化统计
     *         <p>
     *         Cleaned text plus the measurable diff of what the cleanup did.
     */
    public CleanResult clean(String raw) {
        return clean(raw, config());
    }

    /**
     * 按显式配置清洗：分块预览用它做「开 / 关清洗」的对照，真实入库用 {@link #clean(String)}。
     * <p>
     * Clean with an explicit config: the chunk preview uses it to compare cleanup on vs off,
     * while the real ingest path uses {@link #clean(String)}.
     */
    public CleanResult clean(String raw, AdiProperties.Clean config) {
        if (null == raw) {
            return new CleanResult("", false, 0, 0, 0, 0, 0, 0, 0);
        }
        boolean applied = null != config && config.isEnabled();
        if (!applied) {
            int lines = countLines(raw);
            return new CleanResult(raw, false, raw.length(), raw.length(), lines, lines, 0, 0, 0);
        }
        int rawChars = raw.length();
        int rawLines = countLines(raw);

        String text = CRLF.matcher(raw).replaceAll("\n");
        text = text.replace('\f', '\n');
        text = CONTROL_CHARS.matcher(text).replaceAll("");
        text = ZERO_WIDTH.matcher(text).replaceAll("");

        List<String> lines = splitLines(text);
        lines = trimTrailingSpaces(lines);

        int mergedLines = 0;
        if (config.isMergeSoftLineBreaks()) {
            mergedLines = mergeSoftLineBreaks(lines, config.getMaxConsecutiveMerge());
        }
        int droppedLines = 0;
        if (config.isDropDuplicateLines()) {
            droppedLines = dropDuplicateLines(lines, config.getDuplicateLineMinOccurrences(),
                    config.getDuplicateLineMaxLength());
        }
        String cleaned = String.join("\n", lines);
        cleaned = MULTI_BLANK_LINES.matcher(cleaned).replaceAll("\n\n").trim();

        int cleanedLines = countLines(cleaned);
        return new CleanResult(cleaned, true, rawChars, cleaned.length(), rawLines, cleanedLines,
                mergedLines, droppedLines, Math.max(0, rawChars - cleaned.length()));
    }

    /**
     * 当前生效的清洗配置：没有配置段时返回 null（等价于关闭）。
     */
    private AdiProperties.Clean config() {
        AdiProperties.Ingest ingest = null == adiProperties ? null : adiProperties.getIngest();
        return null == ingest ? null : ingest.getClean();
    }

    private static List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            lines.add(line);
        }
        return lines;
    }

    private static List<String> trimTrailingSpaces(List<String> lines) {
        List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            int end = line.length();
            while (end > 0 && (line.charAt(end - 1) == ' ' || line.charAt(end - 1) == '\t')) {
                end--;
            }
            result.add(end == line.length() ? line : line.substring(0, end));
        }
        return result;
    }

    /**
     * 把「上一行不以句末标点结尾、下一行不是块结构开头」的换行接回去：中文段落被排版拆行的典型形态。
     * 连续合并有上限，避免整篇无标点文本被并成一块。
     *
     * @return 合并掉的换行数
     */
    private static int mergeSoftLineBreaks(List<String> lines, int maxConsecutive) {
        int limit = maxConsecutive > 0 ? maxConsecutive : 20;
        List<String> merged = new ArrayList<>(lines.size());
        int mergedCount = 0;
        int consecutive = 0;
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            boolean paragraphBreak = line.isBlank() || current.length() == 0
                    || LINE_END_STOP.matcher(current.toString()).find()
                    || LINE_START_BLOCK.matcher(line).find()
                    || consecutive >= limit;
            if (paragraphBreak) {
                if (current.length() > 0) {
                    merged.add(current.toString());
                    current.setLength(0);
                }
                merged.add(line);
                consecutive = 0;
                continue;
            }
            // CJK 之间不加空格；西文断行补一个空格，避免 "informa-tion" 这类拼接错误变成 "information"
            String tail = current.toString();
            char last = tail.charAt(tail.length() - 1);
            char first = line.isEmpty() ? ' ' : line.charAt(0);
            if (isCjk(last) && isCjk(first)) {
                current.append(line);
            } else {
                current.append(' ').append(line);
            }
            mergedCount++;
            consecutive++;
        }
        if (current.length() > 0) {
            merged.add(current.toString());
        }
        lines.clear();
        lines.addAll(merged);
        return mergedCount;
    }

    /**
     * 删除全文重复出现的短行（页眉页脚）。只在达到出现次数阈值且行很短时删除。
     *
     * @return 删除的行数
     */
    private static int dropDuplicateLines(List<String> lines, int minOccurrences, int maxLength) {
        int min = Math.max(2, minOccurrences);
        int maxLen = maxLength > 0 ? maxLength : 60;
        Map<String, Integer> counter = new HashMap<>();
        for (String line : lines) {
            String key = line.trim();
            if (key.isEmpty() || key.length() > maxLen) {
                continue;
            }
            counter.merge(key, 1, Integer::sum);
        }
        List<String> kept = new ArrayList<>(lines.size());
        int dropped = 0;
        for (String line : lines) {
            String key = line.trim();
            if (!key.isEmpty() && key.length() <= maxLen && counter.getOrDefault(key, 0) >= min) {
                dropped++;
                continue;
            }
            kept.add(line);
        }
        lines.clear();
        lines.addAll(kept);
        return dropped;
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }

    private static int countLines(String text) {
        if (StringUtils.isEmpty(text)) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    /**
     * 清洗结果：文本 + 统计，供分块预览展示「解析 → 清洗」这一步到底做了什么。
     */
    public record CleanResult(String text,
                              boolean applied,
                              int rawChars,
                              int cleanedChars,
                              int rawLines,
                              int cleanedLines,
                              int mergedLines,
                              int droppedLines,
                              int removedChars) {
    }
}
