package com.moyz.adi.common.filter;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2-05：worker 角色 HTTP 收敛门。
 * 行为用例：健康检查放行、业务请求 404；装配用例：仅 adi.role=worker 注册。
 */
class WorkerRoleHttpGuardFilterTest {

    private final WorkerRoleHttpGuardFilter filter = new WorkerRoleHttpGuardFilter();

    private MockHttpServletResponse doFilter(String uri, MockFilterChain chain)
            throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void healthCheckAllowed() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = doFilter("/actuator/health", chain);
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null, "健康检查必须放行到下游");
    }

    @Test
    void healthLivenessAllowed() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = doFilter("/actuator/health/liveness", chain);
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void errorDispatchAllowed() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = doFilter("/error", chain);
        assertEquals(200, response.getStatus());
    }

    @Test
    void businessRequestBlocked404() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = doFilter("/api/chat/completion", chain);
        assertEquals(404, response.getStatus());
        assertFalse(chain.getRequest() != null, "业务请求不得到达下游");
        assertTrue(response.getContentAsString().contains("not_found"));
    }

    @Test
    void rootBlocked404() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = doFilter("/", chain);
        assertEquals(404, response.getStatus());
    }

    /** 装配矩阵：仅 worker 注册；缺失（=all）/api/all 不注册 */
    @Test
    void registeredOnlyForWorkerRole() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withUserConfiguration(GatedConfig.class);
        runner.withPropertyValues("adi.role=worker")
                .run(ctx -> assertTrue(ctx.containsBean("gated"), "worker 必须装配收敛门"));
        runner.run(ctx -> assertFalse(ctx.containsBean("gated"), "缺省（all）不装配"));
        runner.withPropertyValues("adi.role=all").run(ctx -> assertFalse(ctx.containsBean("gated")));
        runner.withPropertyValues("adi.role=api").run(ctx -> assertFalse(ctx.containsBean("gated")));
    }

    @Configuration
    static class GatedConfig {
        @Bean
        @Conditional(WorkerRoleHttpGuardFilter.EnabledCondition.class)
        String gated() {
            return "ok";
        }
    }
}
