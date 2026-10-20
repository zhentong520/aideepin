package com.moyz.adi.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-12 工具日志脱敏单测：各类密钥样例被掩码 / 正常内容保留 / 截断。
 */
class LogSanitizerTest {

    @Test
    void keyValueSecretsMasked() {
        String raw = "config: api_key=abc123xyz, token: \"tok_999\", password=hunter2, Authorization=Bearer abc.def.ghi";
        String out = LogSanitizer.mask(raw);
        assertFalse(out.contains("abc123xyz"), "api_key 值必须被掩码");
        assertFalse(out.contains("tok_999"), "token 值必须被掩码");
        assertFalse(out.contains("hunter2"), "password 值必须被掩码");
        assertFalse(out.contains("abc.def.ghi"), "Authorization 值必须被掩码");
        assertTrue(out.contains("api_key=" + LogSanitizer.MASK));
        assertTrue(out.contains("password=" + LogSanitizer.MASK));
    }

    @Test
    void bearerAndSkKeyMasked() {
        String out = LogSanitizer.mask("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig123456");
        assertFalse(out.contains("eyJzdWIiOiIxIn0.sig123456"));
        assertFalse(out.contains("eyJhbGciOiJIUzI1NiJ9"));
        // 无 Authorization 前缀时 Bearer 规则独立生效
        assertTrue(LogSanitizer.mask("Bearer abc123def456ghi").contains("Bearer ***"));

        String sk = LogSanitizer.mask("using key sk-proj-abcdefgh12345678 in request");
        assertFalse(sk.contains("sk-proj-abcdefgh12345678"));
        assertTrue(sk.contains("sk-***"));
    }

    @Test
    void jwtMasked() {
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJVadQssw5c";
        String out = LogSanitizer.mask("token " + jwt + " expired");
        assertFalse(out.contains(jwt));
        assertTrue(out.contains("***-JWT"));
    }

    @Test
    void longBase64Masked() {
        String b64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String out = LogSanitizer.mask("payload " + b64 + " end");
        assertFalse(out.contains(b64));
        assertTrue(out.contains("***-BASE64"));
    }

    @Test
    void normalContentPreserved() {
        String normal = "检索结果：aideepin 是一个开源知识库项目，版本 1.2.3，地址 https://example.com/repo";
        assertEquals(normal, LogSanitizer.mask(normal));
    }

    @Test
    void shortValuesAndUrlsNotMasked() {
        // 短值（如版本号、布尔）不应误伤；普通 URL 保留
        String raw = "version=1.0, debug=true, url=https://example.com/path?q=abc";
        String out = LogSanitizer.mask(raw);
        assertEquals(raw, out);
    }

    @Test
    void truncatedAtLimit() {
        String raw = "x".repeat(1200);
        String out = LogSanitizer.truncate(raw, 500);
        assertEquals(500, out.indexOf("...[truncated, total 1200 chars]"));
        assertTrue(out.endsWith("[truncated, total 1200 chars]"));
    }

    @Test
    void sanitizeCombinesMaskAndTruncate() {
        String raw = "api_key=supersecretvalue123456 & " + "y".repeat(1000);
        String out = LogSanitizer.sanitize(raw, 100);
        assertFalse(out.contains("supersecretvalue123456"));
        assertTrue(out.length() < 200);
        // 纯长中文文本（掩码规则不命中、不缩短）→ 截断标注保留原始长度
        String plain = "测试文本".repeat(300);
        String out2 = LogSanitizer.sanitize(plain, 500);
        assertTrue(out2.endsWith("[truncated, total 1200 chars]"));
    }

    @Test
    void singleLineCollapsesNewlines() {
        String out = LogSanitizer.sanitizeSingleLine("line1\nline2\r\nline3");
        assertFalse(out.contains("\n"));
        assertTrue(out.contains("line1 line2 line3"));
    }

    @Test
    void nullSafe() {
        assertNull(LogSanitizer.sanitize(null));
        assertEquals("", LogSanitizer.sanitize(""));
    }
}
