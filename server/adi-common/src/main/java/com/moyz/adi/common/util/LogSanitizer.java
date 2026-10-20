package com.moyz.adi.common.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具日志脱敏工具类（T6-12）：工具入参/出参写入日志前的统一出口。
 * <p>
 * 三步处理：
 * 1. 正则掩码——api_key / token / secret / password / Authorization 等键值对、
 *    sk- 开头的 API key、JWT、长 base64 串；
 * 2. 超长截断——默认 500 字符，截断后带原始长度标注（日志体积可控）；
 * 3. 输出单行化选项——调用方可选把换行压成空格，防止日志注入/多行刷屏。
 * <p>
 * Tool log sanitizer (T6-12): mask secrets + truncate before any tool argument/result
 * reaches the logs. Masking covers key=value style secrets, OpenAI-style keys, JWTs and
 * long base64 blobs; truncation keeps log volume bounded.
 */
@Slf4j
public final class LogSanitizer {

    private LogSanitizer() {
    }

    /** 默认截断长度 | Default max length kept in logs */
    public static final int DEFAULT_MAX_LENGTH = 500;

    public static final String MASK = "***";

    /** key=value / key: value 形式的敏感字段（值整段掩码） */
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
            "(?i)((?:api[_-]?key|apikey|access[_-]?token|refresh[_-]?token|token|secret|client[_-]?secret|password|passwd|authorization|credential)[\"']?\\s*[:=]\\s*)(\"?)([^\"',;\\s}\\]]{1,})(\\2)");

    /** Authorization: Bearer xxx */
    private static final Pattern BEARER = Pattern.compile("(?i)(bearer\\s+)[A-Za-z0-9._~+\\-/=]{8,}");

    /** OpenAI 风格 key：sk-开头 */
    private static final Pattern SK_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_\\-]{8,}\\b");

    /** JWT：三段式 eyJ... */
    private static final Pattern JWT = Pattern.compile("\\beyJ[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\.[A-Za-z0-9_\\-]{8,}\\b");

    /** 长 base64 串（>=40 位，可能是密钥/证书/图片数据） */
    private static final Pattern LONG_BASE64 = Pattern.compile("\\b[A-Za-z0-9+/]{40,}={0,2}\\b");

    /** 数据 URI（data:...;base64,xxx）整段掩码 */
    private static final Pattern DATA_URI = Pattern.compile("(?i)data:[a-z0-9.\\-+/]+;base64,[A-Za-z0-9+/=]+");

    /**
     * 掩码 + 截断（默认 500 字符），工具日志统一入口。
     */
    public static String sanitize(String raw) {
        return sanitize(raw, DEFAULT_MAX_LENGTH);
    }

    /**
     * 掩码 + 截断（自定义长度）。null 安全。
     */
    public static String sanitize(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String masked = mask(raw);
        return truncate(masked, maxLength);
    }

    /**
     * 仅掩码（不截断）。单测可分别断言两个能力。
     */
    public static String mask(String raw) {
        if (StringUtils.isEmpty(raw)) {
            return raw;
        }
        String result = raw;
        result = DATA_URI.matcher(result).replaceAll("data:image/***");
        // Bearer 整段先掩码（若 JWT 先跑会吃掉 Bearer 后面的令牌导致 Bearer 规则落空）
        result = BEARER.matcher(result).replaceAll("$1" + MASK);
        result = SK_KEY.matcher(result).replaceAll("sk-" + MASK);
        result = JWT.matcher(result).replaceAll(MASK + "-JWT");
        result = KEY_VALUE_SECRET.matcher(result).replaceAll("$1$2" + MASK + "$4");
        result = LONG_BASE64.matcher(result).replaceAll(MASK + "-BASE64");
        return result;
    }

    /**
     * 仅截断：超过 maxLength 时截断并追加标注（保留原始长度信息便于排查）。
     */
    public static String truncate(String raw, int maxLength) {
        if (raw == null || raw.length() <= maxLength) {
            return raw;
        }
        return raw.substring(0, maxLength) + "...[truncated, total " + raw.length() + " chars]";
    }

    /**
     * 掩码 + 截断 + 单行化（工具调用日志推荐用法：防止多行内容刷屏日志）。
     */
    public static String sanitizeSingleLine(String raw) {
        return sanitizeSingleLine(raw, DEFAULT_MAX_LENGTH);
    }

    public static String sanitizeSingleLine(String raw, int maxLength) {
        String s = sanitize(raw, maxLength);
        return s == null ? null : s.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ');
    }
}
