package com.moyz.adi.common.filter;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.ExtApiService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExtApiAuthFilter 的路径路由测试（T1-08/T1-10）。
 * <p>
 * 钉死回归缺陷：{@code /mcp/kb/**} 必须进入 ext key 鉴权分支（外层路径守卫曾漏掉该前缀，
 * 导致 MCP 请求完全绕过鉴权、ThreadContext 无主体）。同时确认
 * 资源级类型推断：/mcp/kb → KNOWLEDGE（用户级 key 不适用）。
 */
@ExtendWith(MockitoExtension.class)
class ExtApiAuthFilterPathTest {

    private static final String RAW_KEY = "ext-0123456789abcdef";

    @Mock
    private ExtApiService extApiService;

    private ExtApiAuthFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ExtApiAuthFilter();
        ReflectionTestUtils.setField(filter, "extApiService", extApiService);
        ReflectionTestUtils.setField(filter, "contextPath", "");
        User owner = new User();
        owner.setUuid("owner-1");
        owner.setIsAdmin(false);
        lenient().when(extApiService.validateApiKey(RAW_KEY, "knowledge"))
                .thenReturn(new ExtApiService.ValidateResult("kb-bound", owner));
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
    }

    /** 主体信息必须在过滤链内部可见（filter 自己的 finally 会清理 ThreadContext） */
    private static final class CapturingChain implements FilterChain {
        final AtomicReference<User> userAtChain = new AtomicReference<>();
        final AtomicReference<String> entityUuidAtChain = new AtomicReference<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response)
                throws IOException, ServletException {
            userAtChain.set(ThreadContext.getCurrentUser());
            entityUuidAtChain.set(ThreadContext.getExtApiEntityUuid());
        }
    }

    @DisplayName("/mcp/kb/{uuid} 必须进入鉴权分支，按 knowledge 资源级 key 校验并在链内设置主体")
    @Test
    void mcpKbPathIsAuthenticatedAsKnowledge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/kb/kb-bound");
        request.addHeader("Authorization", RAW_KEY);
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus(), "有效 key 不得被 401 拦截");
        verify(extApiService).validateApiKey(RAW_KEY, "knowledge");
        assertNotNull(chain.userAtChain.get(), "鉴权后链内必须有当前用户");
        assertEquals("owner-1", chain.userAtChain.get().getUuid());
        assertEquals("kb-bound", chain.entityUuidAtChain.get(), "ext 上下文必须携带 key 绑定的库");
        // 链返回后 ThreadContext 已被过滤器清理，不外泄
        assertNull(ThreadContext.getCurrentUser());
    }

    @DisplayName("无 key 的 /mcp/kb 请求 → 401")
    @Test
    void mcpKbPathWithoutKeyIs401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp/kb/kb-bound");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(chain.userAtChain.get(), "鉴权失败不得进入链内");
        verify(extApiService, never()).validateApiKey(anyString(), anyString());
        verify(extApiService, never()).validateUserApiKey(anyString(), anyString());
    }

    @DisplayName("非 ext 面路径不进鉴权分支（如 /knowledge-base/list）")
    @Test
    void nonExtPathSkipsAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/knowledge-base/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        CapturingChain chain = new CapturingChain();

        filter.doFilterInternal(request, response, chain);

        assertEquals(200, response.getStatus());
        verify(extApiService, never()).validateApiKey(any(), any());
    }
}
