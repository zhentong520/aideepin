package com.moyz.adi.common.filter;

import com.moyz.adi.common.service.ExtApiQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.ServletException;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ExtApiRateLimitFilter 的 HTTP 层行为测试（T1-09）。
 * <p>
 * ⚠️ 核心契约（任务书历史事故：额度检查 {@code len>0} 判 PASS 的假绿）：
 * 超限<b>必须断言实际 HTTP 状态码 429</b>，并断言 {@code Retry-After} 头存在，
 * 不许只看响应体内容。
 */
@ExtendWith(MockitoExtension.class)
class ExtApiRateLimitFilterTest {

    private static final String VALID_KEY = "ext-abcdef0123456789";

    @Mock
    private ExtApiQuotaService quotaService;

    private ExtApiRateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ExtApiRateLimitFilter();
        ReflectionTestUtils.setField(filter, "quotaService", quotaService);
        ReflectionTestUtils.setField(filter, "contextPath", "");
        // 默认全部放行，各用例自行覆盖
        lenient().when(quotaService.isEnabled()).thenReturn(true);
        lenient().when(quotaService.tryAcquireQps(anyString())).thenReturn(true);
        lenient().when(quotaService.tryConsumeDailyRequest(anyString())).thenReturn(true);
        lenient().when(quotaService.isTokenQuotaExceeded(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(false);
    }

    private MockHttpServletResponse doFilter(String uri) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.addHeader("Authorization", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);
        return response;
    }

    @DisplayName("QPS 超限：HTTP 状态码必须为 429 且 Retry-After 头存在")
    @Test
    void qpsExceededReturns429WithRetryAfter() throws Exception {
        whenQpsDenied();

        MockHttpServletResponse response = doFilter("/ext/v1/knowledge/retrieve");

        // ⚠️ 断言实际状态码，不是响应体
        assertEquals(429, response.getStatus(), "QPS 超限必须返回真实 HTTP 429");
        assertNotNull(response.getHeader("Retry-After"), "429 必须携带 Retry-After 响应头");
        assertEquals("1", response.getHeader("Retry-After"), "QPS 超限的 Retry-After 应为 1 秒");
    }

    @DisplayName("日调用配额超限：HTTP 429 + Retry-After（距当日 0 点秒数）")
    @Test
    void dailyQuotaExceededReturns429WithRetryAfter() throws Exception {
        lenient().when(quotaService.tryConsumeDailyRequest(anyString())).thenReturn(false);

        MockHttpServletResponse response = doFilter("/ext/v1/knowledge/retrieve");

        assertEquals(429, response.getStatus(), "日配额超限必须返回真实 HTTP 429");
        String retryAfter = response.getHeader("Retry-After");
        assertNotNull(retryAfter, "429 必须携带 Retry-After 响应头");
        assertTrue(Long.parseLong(retryAfter) >= 1, "日配额超限的 Retry-After 应 >= 1 秒");
    }

    @DisplayName("日 token 配额超限：HTTP 429 + Retry-After")
    @Test
    void tokenQuotaExceededReturns429WithRetryAfter() throws Exception {
        lenient().when(quotaService.isTokenQuotaExceeded(anyString(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);

        MockHttpServletResponse response = doFilter("/ext/v1/knowledge/retrieve");

        assertEquals(429, response.getStatus(), "日 token 配额超限必须返回真实 HTTP 429");
        assertNotNull(response.getHeader("Retry-After"));
    }

    @DisplayName("未超限：请求放行到后续链路")
    @Test
    void withinQuotaPassesThrough() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ext/v1/knowledge/retrieve");
        request.addHeader("Authorization", VALID_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "放行的请求必须到达后续过滤器/控制器");
    }

    @DisplayName("无 ext key 的请求不做限流（交给鉴权过滤器 401），不占用配额维度")
    @Test
    void requestWithoutKeyIsSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/ext/v1/knowledge/retrieve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        verify(quotaService, never()).tryAcquireQps(anyString());
        verify(quotaService, never()).tryConsumeDailyRequest(anyString());
        assertEquals(200, response.getStatus());
    }

    @DisplayName("enabled=false 时全部放行（铁律 10：可热回滚）")
    @Test
    void disabledSwitchPassesThrough() throws Exception {
        lenient().when(quotaService.isEnabled()).thenReturn(false);

        MockHttpServletResponse response = doFilter("/ext/v1/knowledge/retrieve");

        assertEquals(200, response.getStatus());
        verify(quotaService, never()).tryAcquireQps(anyString());
    }

    @DisplayName("非 ext 路径不受影响")
    @Test
    void nonExtPathUnaffected() throws Exception {
        MockHttpServletResponse response = doFilter("/knowledge-base/list");

        assertEquals(200, response.getStatus());
        verify(quotaService, never()).tryAcquireQps(anyString());
    }

    @DisplayName("MCP KB 端点与 ext 共用同一限流维度")
    @Test
    void mcpKbPathIsRateLimited() throws Exception {
        whenQpsDenied();

        MockHttpServletResponse response = doFilter("/mcp/kb/kb-uuid-1");

        assertEquals(429, response.getStatus(), "MCP KB 端点超限同样必须返回 HTTP 429");
        assertNotNull(response.getHeader("Retry-After"));
    }

    @DisplayName("带 context-path 时匹配同样生效")
    @Test
    void contextPathIsRespected() throws Exception {
        ReflectionTestUtils.setField(filter, "contextPath", "/api");
        whenQpsDenied();

        MockHttpServletResponse response = doFilter("/api/ext/v1/knowledge/retrieve");

        assertEquals(429, response.getStatus());
    }

    private void whenQpsDenied() {
        lenient().when(quotaService.tryAcquireQps(anyString())).thenReturn(false);
    }
}
