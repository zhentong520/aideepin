package com.moyz.adi.common.service;

import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.Optional;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KbDocSourceService} 的 SSRF 防护回归测试。
 *
 * <p>2026-10-15 修复前 {@code httpFetch} 用 {@code Redirect.NORMAL} 让 JDK HttpClient 自动跟随
 * 重定向，而 {@code assertPublicUrl} 只在发请求前校验一次首跳——公网 302 跳
 * {@code 169.254.169.254}（云元数据）等内网地址时不会再过防护，可被用来窃取凭证。
 * 修复后改为 {@code Redirect.NEVER} + 自建循环逐跳复检。
 *
 * <p>「公网 302 跳内网」无法在本机端到端模拟（没有可控公网地址），故用 mock HttpClient
 * 模拟重定向响应；{@code assertPublicUrl} 的内网拒绝用 IP 字面量直测（不触发 DNS）。
 */
class KbDocSourceServiceSsrfTest {

    private KbDocSourceService serviceWith(HttpClient client) {
        KbDocSourceService spy = org.mockito.Mockito.spy(new KbDocSourceService());
        org.mockito.Mockito.doReturn(client).when(spy).newHttpClient();
        return spy;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HttpResponse<byte[]> response(int status, Map<String, List<String>> headers, byte[] body) {
        HttpResponse<byte[]> resp = mock(HttpResponse.class);
        when(resp.statusCode()).thenReturn(status);
        when(resp.headers()).thenReturn(java.net.http.HttpHeaders.of(
                headers, (name, value) -> true));
        when(resp.body()).thenReturn(body);
        return resp;
    }

    // ------------------------------------------------------------------
    // assertPublicUrl：内网/本机/元数据/协议白名单
    // ------------------------------------------------------------------

    @DisplayName("内网/本机/链路本地/云元数据地址一律拒绝（IP 字面量，不触发 DNS）")
    @Test
    void assertPublicUrlRejectsPrivateAddresses() throws Exception {
        KbDocSourceService service = new KbDocSourceService();
        Method assertMethod = KbDocSourceService.class.getDeclaredMethod("assertPublicUrl", String.class);
        assertMethod.setAccessible(true);

        String[] forbidden = {
                "http://127.0.0.1/x",
                "http://localhost/x",
                "http://10.1.2.3/x",
                "http://172.16.0.9/x",
                "http://192.168.1.1/x",
                "http://169.254.169.254/latest/meta-data/",
                "http://0.0.0.0/x",
                "http://[::1]/x",
        };
        for (String url : forbidden) {
            Exception e = assertThrows(Exception.class, () -> {
                try {
                    assertMethod.invoke(service, url);
                } catch (java.lang.reflect.InvocationTargetException te) {
                    throw te.getCause();
                }
            }, url + " 应被拒绝");
            assertTrue(e instanceof BaseException, url + " 应抛业务异常而非意外错误");
        }
    }

    @DisplayName("协议白名单：非 http/https 拒绝")
    @Test
    void assertPublicUrlRejectsNonHttpSchemes() throws Exception {
        KbDocSourceService service = new KbDocSourceService();
        Method assertMethod = KbDocSourceService.class.getDeclaredMethod("assertPublicUrl", String.class);
        assertMethod.setAccessible(true);

        for (String url : new String[]{"ftp://example.com/x", "file:///etc/passwd", "gopher://x", "http:///nohost"}) {
            Exception e = assertThrows(Exception.class, () -> {
                try {
                    assertMethod.invoke(service, url);
                } catch (java.lang.reflect.InvocationTargetException te) {
                    throw te.getCause();
                }
            }, url + " 应被拒绝");
            assertTrue(e instanceof BaseException);
        }
    }

    @DisplayName("公网 IP 字面量放行（1.1.1.1 不触发 DNS 查询）")
    @Test
    void assertPublicUrlAllowsPublicIp() throws Exception {
        KbDocSourceService service = new KbDocSourceService();
        Method assertMethod = KbDocSourceService.class.getDeclaredMethod("assertPublicUrl", String.class);
        assertMethod.setAccessible(true);
        // 不应抛异常
        assertMethod.invoke(service, "http://1.1.1.1/x");
        assertMethod.invoke(service, "https://8.8.8.8/dns-query");
    }

    // ------------------------------------------------------------------
    // 重定向逐跳复检（核心回归）
    // ------------------------------------------------------------------

    @DisplayName("公网 302 跳 169.254.169.254 必须被第二跳复检拦住（核心回归）")
    @Test
    void redirectLoopRechecksEveryHop() throws Exception {
        HttpClient client = mock(HttpClient.class);
        // 首跳：公网地址返回 302 → 云元数据
        HttpResponse<byte[]> redirect = response(302, Map.of("location", List.of("http://169.254.169.254/latest/meta-data/")), new byte[0]);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect);

        KbDocSourceService service = serviceWith(client);

        BaseException e = assertThrows(BaseException.class,
                () -> invokeHttpFetch(service, "http://1.1.1.1/redir", "GET", null, null),
                "重定向到云元数据必须被拦下");

        // 确认是被 SSRF 防护拒绝（A_DOC_SOURCE_FORBIDDEN），而非其他错误
        assertEquals(errorCode("A_DOC_SOURCE_FORBIDDEN"), e.getCode(),
                "应被 SSRF 防护拒绝，实际：" + e.getMessage());
    }

    @DisplayName("重定向回公网时正常跟随到底（功能不回归）")
    @Test
    void redirectLoopFollowsPublicRedirect() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> redirect = response(302, Map.of("location", List.of("http://1.1.1.1/final")), new byte[0]);
        HttpResponse<byte[]> ok = response(200, Map.of("content-type", List.of("text/plain")), "hello".getBytes());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect)
                .thenReturn(ok);

        KbDocSourceService service = serviceWith(client);
        String text = invokeHttpFetch(service, "http://1.1.1.1/start", "GET", null, null);

        assertEquals("hello", text);
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals(URI.create("http://1.1.1.1/final"), captor.getAllValues().get(1).uri(),
                "第二跳应请求 Location 指向的地址");
    }

    @DisplayName("用户自定义 header 只发首跳，不随重定向转发")
    @Test
    void customHeadersNotForwardedOnRedirect() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> redirect = response(302, Map.of("location", List.of("http://1.1.1.1/final")), new byte[0]);
        HttpResponse<byte[]> ok = response(200, Map.of("content-type", List.of("text/plain")), "ok".getBytes());
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect)
                .thenReturn(ok);

        KbDocSourceService service = serviceWith(client);
        invokeHttpFetch(service, "http://1.1.1.1/start", "GET", Map.of("X-Secret", "credential"), null);

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(captor.getAllValues().get(0).headers().firstValue("X-Secret").isPresent(),
                "首跳应带自定义 header");
        assertTrue(captor.getAllValues().get(1).headers().firstValue("X-Secret").isEmpty(),
                "重定向目标可能是攻击者站点，自定义 header 不得转发");
    }

    @DisplayName("重定向环达到上限后报错而不是死循环")
    @Test
    void redirectLoopTerminates() throws Exception {
        HttpClient client = mock(HttpClient.class);
        HttpResponse<byte[]> redirect = response(302, Map.of("location", List.of("http://1.1.1.1/loop")), new byte[0]);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(redirect);

        KbDocSourceService service = serviceWith(client);
        BaseException e = assertThrows(BaseException.class,
                () -> invokeHttpFetch(service, "http://1.1.1.1/loop", "GET", null, null));
        // getMessage 只含错误码前缀，具体文案在 info/data 里，断言错误码而非匹配文本
        assertEquals(errorCode("A_DOC_SOURCE_FETCH_FAIL"), e.getCode(),
                "应报抓取失败（重定向超限）：" + e.getMessage());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** httpFetch 是 private，反射调用并展开 InvocationTargetException */
    private static String invokeHttpFetch(KbDocSourceService service, String url, String method,
                                          Map<String, String> headers, String body) throws Exception {
        Method m = KbDocSourceService.class.getDeclaredMethod("httpFetch",
                String.class, String.class, Map.class, String.class);
        m.setAccessible(true);
        try {
            return (String) m.invoke(service, url, method, headers, body);
        } catch (java.lang.reflect.InvocationTargetException te) {
            throw (Exception) te.getCause();
        }
    }

    private static String errorCode(String name) {
        return String.valueOf(com.moyz.adi.common.enums.ErrorEnum.valueOf(name).getCode());
    }
}
