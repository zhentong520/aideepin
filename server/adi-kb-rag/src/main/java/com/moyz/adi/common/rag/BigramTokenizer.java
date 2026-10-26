package com.moyz.adi.common.rag;

import org.apache.commons.lang3.StringUtils;

/**
 * 中英混排 bigram 分词器，为 PostgreSQL 全文检索（simple 配置）生成可检索文本。
 * <p>
 * 为什么不用 PG 默认分词：simple/simple 之外的配置对中文无效，会把整句当一个 token；
 * zhparser/jieba 需装扩展，生产库（外部自建 PG）未必有权限。bigram 是零依赖且对中文有效的折中。
 * <p>
 * 规则：
 * <ul>
 *   <li>CJK 连续段 → 二元组滑动切分；单字段保留原字（否则「物业费」三字以上才召回得到）</li>
 *   <li>ASCII 字母数字连续段 → 整词保留并转小写（英文/数字按词匹配，不做 2-gram）</li>
 *   <li>其余字符（标点、空白、换行）→ 视为分隔符丢弃</li>
 * </ul>
 * 例：「物业费 12元」→「物业 业费 12元」
 */
public final class BigramTokenizer {

    private BigramTokenizer() {
    }

    /**
     * 上限保护：超长文本截断，避免 search_text 列过大拖慢写入与索引
     */
    private static final int MAX_INPUT_LENGTH = 8000;

    public static String tokenize(String text) {
        if (StringUtils.isBlank(text)) {
            return "";
        }
        String source = text.length() > MAX_INPUT_LENGTH ? text.substring(0, MAX_INPUT_LENGTH) : text;
        StringBuilder sb = new StringBuilder(source.length() * 2);
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (isCjk(c)) {
                int j = i;
                while (j < n && isCjk(source.charAt(j))) {
                    j++;
                }
                appendCjkBigram(sb, source.substring(i, j));
                i = j;
            } else if (isAsciiAlnum(c)) {
                int j = i;
                while (j < n && isAsciiAlnum(source.charAt(j))) {
                    j++;
                }
                appendToken(sb, source.substring(i, j).toLowerCase());
                i = j;
            } else {
                i++;
            }
        }
        return sb.toString().trim();
    }

    /**
     * 生成 to_tsquery 可用的 OR 查询串（词元以 | 连接）。
     * <p>
     * 为什么不用 {@code plainto_tsquery}：它是 AND 语义，要求文档同时包含全部 bigram。
     * 实测「物业管理费怎么算」在同时含「物业管理」的文档上命中 0 条——查询越长要求越苛刻，
     * 关键词路形同虚设。改成 OR 后同样场景命中并按 ts_rank 正确排序（0.028 > 0.0087 > 0）。
     * <p>
     * 安全性：分词结果只含 CJK 与 ASCII 字母数字，不含 & | ! ( ) : 等 tsquery 运算符，
     * 这里仍做一次字符白名单过滤作为兜底。
     *
     * @return 形如 {@code 物业|业管|管理}；空输入返回空串
     */
    public static String tokenizeForTsQuery(String text) {
        String tokens = tokenize(text);
        if (StringUtils.isBlank(tokens)) {
            return "";
        }
        StringBuilder sb = new StringBuilder(tokens.length() + 8);
        for (String token : tokens.split(" ")) {
            if (token.isEmpty() || !isSafeTsQueryLexeme(token)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(token);
        }
        return sb.toString();
    }

    private static boolean isSafeTsQueryLexeme(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (!(isCjk(c) || isAsciiAlnum(c))) {
                return false;
            }
        }
        return true;
    }

    private static void appendCjkBigram(StringBuilder sb, String cjkSegment) {
        if (cjkSegment.length() == 1) {
            appendToken(sb, cjkSegment);
            return;
        }
        for (int k = 0; k < cjkSegment.length() - 1; k++) {
            appendToken(sb, cjkSegment.substring(k, k + 2));
        }
    }

    private static void appendToken(StringBuilder sb, String token) {
        if (token.isEmpty()) {
            return;
        }
        if (sb.length() > 0) {
            sb.append(' ');
        }
        sb.append(token);
    }

    private static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || block == Character.UnicodeBlock.HIRAGANA
                || block == Character.UnicodeBlock.KATAKANA
                || block == Character.UnicodeBlock.HANGUL_SYLLABLES;
    }

    private static boolean isAsciiAlnum(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
