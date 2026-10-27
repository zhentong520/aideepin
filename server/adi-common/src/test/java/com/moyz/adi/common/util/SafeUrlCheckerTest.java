package com.moyz.adi.common.util;

import com.moyz.adi.common.exception.UnsafeUrlException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SafeUrlChecker} 的 SSRF 防护单测。
 *
 * <p>内网拒绝用 IP 字面量直测（不触发 DNS，也不依赖本机网络环境）；域名解析失败
 * 用不可解析的 TLD 验证。调用方（KB 抓取 / 工作流 HTTP 节点 / 远程图片保存）的错误码
 * 翻译分别由各自的回归测试覆盖（见 KbDocSourceServiceSsrfTest）。
 */
class SafeUrlCheckerTest {

    @DisplayName("内网/本机/链路本地/云元数据地址一律拒绝")
    @Test
    void rejectsPrivateAddresses() {
        String[][] cases = {
                {"http://127.0.0.1/x", "loopback"},
                {"http://localhost/x", "loopback by name"},
                {"http://10.1.2.3/x", "10/8"},
                {"http://172.16.0.9/x", "172.16/12"},
                {"http://172.31.255.255/x", "172.16/12 上界"},
                {"http://192.168.1.1/x", "192.168/16"},
                {"http://169.254.169.254/latest/meta-data/", "云元数据"},
                {"http://0.0.0.0/x", "any-local"},
                {"http://[::1]/x", "IPv6 loopback"},
                {"http://224.0.0.1/x", "组播"},
        };
        for (String[] c : cases) {
            UnsafeUrlException e = assertThrows(UnsafeUrlException.class, () -> SafeUrlChecker.check(c[0]),
                    c[0] + " 应被拒绝");
            assertEquals(UnsafeUrlException.Reason.PRIVATE_ADDRESS, e.getReason(),
                    c[0] + " 应以 PRIVATE_ADDRESS 拒绝");
        }
    }

    @DisplayName("协议白名单：非 http/https 一律拒绝")
    @Test
    void rejectsNonHttpSchemes() {
        for (String url : new String[]{"ftp://example.com/x", "file:///etc/passwd", "gopher://x", "jar:file:/x"}) {
            UnsafeUrlException e = assertThrows(UnsafeUrlException.class, () -> SafeUrlChecker.check(url),
                    url + " 应被拒绝");
            assertEquals(UnsafeUrlException.Reason.SCHEME_NOT_ALLOWED, e.getReason());
        }
    }

    @DisplayName("空白/格式非法/缺主机名的 URL 拒绝且原因分类正确")
    @Test
    void rejectsBlankAndMalformed() {
        assertEquals(UnsafeUrlException.Reason.BLANK,
                reasonOf(() -> SafeUrlChecker.check(null)));
        assertEquals(UnsafeUrlException.Reason.BLANK,
                reasonOf(() -> SafeUrlChecker.check("   ")));
        assertEquals(UnsafeUrlException.Reason.MALFORMED,
                reasonOf(() -> SafeUrlChecker.check("ht tp://bad url/x")));
        assertEquals(UnsafeUrlException.Reason.HOST_MISSING,
                reasonOf(() -> SafeUrlChecker.check("http:///nohost")));
    }

    @DisplayName("域名解析失败给出 UNRESOLVABLE 且携带主机名")
    @Test
    void reportsUnresolvableHost() {
        UnsafeUrlException e = assertThrows(UnsafeUrlException.class,
                () -> SafeUrlChecker.check("http://this-host-does-not-exist.invalid/x"));
        assertEquals(UnsafeUrlException.Reason.UNRESOLVABLE, e.getReason());
        assertEquals("this-host-does-not-exist.invalid", e.getHost());
    }

    @DisplayName("公网 IP 字面量与 https 放行（不抛异常）")
    @Test
    void allowsPublicAddresses() {
        assertDoesNotThrow(() -> SafeUrlChecker.check("http://1.1.1.1/x"));
        assertDoesNotThrow(() -> SafeUrlChecker.check("https://8.8.8.8/dns-query"));
        assertDoesNotThrow(() -> SafeUrlChecker.check("https://example.com/x"));
    }

    private static UnsafeUrlException.Reason reasonOf(Runnable call) {
        try {
            call.run();
        } catch (UnsafeUrlException e) {
            return e.getReason();
        }
        throw new AssertionError("应抛 UnsafeUrlException");
    }
}
