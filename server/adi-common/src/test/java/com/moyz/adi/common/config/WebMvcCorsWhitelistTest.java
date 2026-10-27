package com.moyz.adi.common.config;

import com.moyz.adi.common.config.props.Auth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1-13（2026-10-27）：CORS 白名单回归。
 *
 * <p>改造前 {@code addCorsMappings} 未声明 origins（Spring 默认放行全部来源），
 * 任意第三方页面都能跨域携带请求读取受 CORS 保护的数据。改造后必须：
 * <ol>
 *   <li>默认白名单 = frontendUrl + 本地开发端点；</li>
 *   <li>显式配置 {@code adi.auth.cors-allowed-origin-patterns} 后以配置为准；</li>
 *   <li>恶意来源（跨域脚本）在 CORS 预检/响应阶段被拒（checkOrigin 返回 null）。</li>
 * </ol>
 */
class WebMvcCorsWhitelistTest {

    private static CorsConfiguration configurationOf(WebMvcConfig config) throws Exception {
        CorsRegistry registry = new CorsRegistry();
        config.addCorsMappings(registry);
        @SuppressWarnings("unchecked")
        List<CorsRegistration> registrations =
                (List<CorsRegistration>) ReflectionTestUtils.getField(registry, "registrations");
        assertEquals(1, registrations.size(), "应只注册一条 /** 的 CORS 映射");
        // getCorsConfiguration() 是 protected，测试内走反射
        return (CorsConfiguration) ReflectionTestUtils.invokeMethod(registrations.get(0), "getCorsConfiguration");
    }

    private static WebMvcConfig configWith(String frontendUrl, List<String> configured) {
        WebMvcConfig config = new WebMvcConfig();
        AdiProperties props = new AdiProperties();
        props.setFrontendUrl(frontendUrl);
        if (configured != null) {
            props.getAuth().setCorsAllowedOriginPatterns(configured);
        }
        ReflectionTestUtils.setField(config, "adiProperties", props);
        return config;
    }

    @DisplayName("默认白名单 = frontendUrl + 本地开发端点")
    @Test
    void fallbackWhitelistContainsFrontendAndLocalhost() {
        WebMvcConfig config = configWith("http://www.aideepin.com", null);
        List<String> whitelist = config.resolveCorsWhitelist();
        assertTrue(whitelist.contains("http://www.aideepin.com"), "frontendUrl 应并入白名单");
        assertTrue(whitelist.containsAll(Auth.DEFAULT_CORS_DEV_PATTERNS), "本地开发端点应并入白名单");
    }

    @DisplayName("跨域脚本来源必须被 CORS 拒绝（checkOrigin 返回 null）")
    @Test
    void evilOriginRejected() throws Exception {
        WebMvcConfig config = configWith("http://www.aideepin.com", null);
        CorsConfiguration cors = configurationOf(config);
        assertNull(cors.checkOrigin("https://evil.example.com"), "第三方站点不得通过 CORS");
        assertNotNull(cors.checkOrigin("http://www.aideepin.com"), "frontendUrl 来源应放行");
        assertNotNull(cors.checkOrigin("http://localhost:5173"), "本地开发端点（端口通配）应放行");
    }

    @DisplayName("显式配置 cors-allowed-origin-patterns 后以配置为准（整列表替换）")
    @Test
    void configuredWhitelistOverridesFallback() throws Exception {
        WebMvcConfig config = configWith("http://www.aideepin.com",
                List.of("https://app.example.com"));
        CorsConfiguration cors = configurationOf(config);
        assertNotNull(cors.checkOrigin("https://app.example.com"), "配置来源应放行");
        assertNull(cors.checkOrigin("http://www.aideepin.com"), "覆盖即整列表替换，未列入的默认来源不再放行");
        assertNull(cors.checkOrigin("http://localhost:5173"), "覆盖后本地开发端点也随配置走");
    }

    @DisplayName("合法来源需允许携带凭据（Cookie/Authorization 跨域）")
    @Test
    void allowCredentialsEnabled() throws Exception {
        WebMvcConfig config = configWith("http://www.aideepin.com", null);
        CorsConfiguration cors = configurationOf(config);
        assertEquals(Boolean.TRUE, cors.getAllowCredentials(), "合法来源应允许携带凭据");
        assertTrue(cors.getExposedHeaders().contains("Authorization"),
                "登录 token 在 Authorization 头，前端需可读");
    }
}
