package com.moyz.adi.common.filter;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Auth;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：TokenFilter 的白名单判定、context-path 归一化与 /admin/ 鉴权。
 *
 * <p>覆盖两类真实缺陷：
 * <ol>
 *   <li><b>裸扩展名鉴权绕过</b>：WEB_RESOURCES 曾混入 ".js/.css/.html"，而 excludePath 对它们走
 *       endsWith 分支，导致「任意受保护 URL 末尾拼 .js」即整体绕过鉴权（含 /admin/ 校验）。</li>
 *   <li><b>context-path 拼接</b>：直接拼配置原值时，{@code context-path: /} 会拼出 {@code //auth/}，
 *       与 requestURI 永不相等，整份白名单静默失效。</li>
 * </ol>
 *
 * <p>断言直接打在 excludePath / checkPathWithToken / isAdmin 三个判定方法上（反射调用），
 * 因为它们构成本类全部鉴权决策；HTTP 层行为留给集成测试。
 */
class TokenFilterExcludePathTest {

    private TokenFilter tokenFilter;
    private AdiProperties adiProperties;

    @BeforeEach
    void setUp() {
        tokenFilter = new TokenFilter();
        // S1-06：白名单改为 adi.auth.exclude-urls 注入，无容器环境手动装填
        adiProperties = new AdiProperties();
        ReflectionTestUtils.setField(tokenFilter, "adiProperties", adiProperties);
        ReflectionTestUtils.invokeMethod(tokenFilter, "initExcludeUrls");
    }

    /** 构造指定 context-path 的过滤器并执行归一化初始化 */
    private void withContextPath(String rawContextPath) throws Exception {
        ReflectionTestUtils.setField(tokenFilter, "contextPath", rawContextPath);
        Method init = TokenFilter.class.getDeclaredMethod("initContextPathPrefix");
        init.setAccessible(true);
        init.invoke(tokenFilter);
    }

    private boolean excludePath(String uri) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(tokenFilter, "excludePath", uri));
    }

    private boolean checkPathWithToken(String uri) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(tokenFilter, "checkPathWithToken", uri));
    }

    private boolean isAdmin(User user) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(tokenFilter, "isAdmin", user));
    }

    // ------------------------------------------------------------------
    // 缺陷 1：裸扩展名鉴权绕过（本次修复核心，必须有负向用例）
    // ------------------------------------------------------------------

    @DisplayName("URL 末尾拼接 .js/.css/.html 不得绕过鉴权")
    @ParameterizedTest
    @ValueSource(strings = {
            "/admin/user/info/abc.js",
            "/admin/kb/info/abc.js",
            "/admin/finetune/job/abc.js",
            "/admin/skill/abc.html",
            "/admin/model/health.css",
            "/knowledge-base/list.js",
            "/user/profile.html",
            "/finetune/job/abc.js",
            "/workflow/list.css"
    })
    void maliciousSuffixMustNotBypassAuth(String uri) throws Exception {
        withContextPath("/");
        assertFalse(excludePath(uri), "拼接裸扩展名后被当成白名单，等于完全绕过鉴权：" + uri);
    }

    @Test
    void webResourcesContainsNoBareExtension() {
        for (String path : AdiConstant.WEB_RESOURCES) {
            assertTrue(path.startsWith("/"),
                    "WEB_RESOURCES 只允许以 / 开头的完整路径，发现裸扩展名：" + path);
        }
    }

    @DisplayName("完整路径资源仍应放行（修复不能误伤 swagger / doc.html）")
    @Test
    void fullPathResourcesStillExcluded() throws Exception {
        withContextPath("/");
        assertTrue(excludePath("/doc.html"));
        assertTrue(excludePath("/swagger-ui/index.html"));
        assertTrue(excludePath("/swagger-ui"));
        assertTrue(excludePath("/v3/api-docs"));
        assertTrue(excludePath("/favicon.ico"));
    }

    // ------------------------------------------------------------------
    // 缺陷 2：context-path 拼接
    // ------------------------------------------------------------------

    @DisplayName("context-path=/ 时白名单必须真正生效（回归 //auth/ 拼接缺陷）")
    @Test
    void rootContextPathStillMatchesWhitelist() throws Exception {
        withContextPath("/");
        assertTrue(excludePath("/auth/login"), "/auth/login 应在白名单内");
        assertTrue(excludePath("/model/platforms"));
        assertTrue(excludePath("/knowledge-base/public/list"));
        assertTrue(excludePath("/ext/v1/finetune/jobs"));
    }

    @DisplayName("context-path 为空/根/空白/null 都应等价")
    @Test
    void contextPathVariantsAreEquivalent() throws Exception {
        for (String variant : new String[]{"", "/", "  ", null}) {
            withContextPath(variant);
            assertEquals("", ReflectionTestUtils.getField(tokenFilter, "contextPathPrefix"),
                    "根路径应归一化为空串，variant=[" + variant + "]");
            assertTrue(excludePath("/auth/login"),
                    "context-path=[" + variant + "] 时 /auth/login 应放行");
        }
    }

    @DisplayName("非根 context-path 必须正确参与匹配")
    @Test
    void nonRootContextPathIsApplied() throws Exception {
        withContextPath("/api");
        assertEquals("/api", ReflectionTestUtils.getField(tokenFilter, "contextPathPrefix"));
        assertTrue(excludePath("/api/auth/login"), "带 /api 前缀时白名单应命中");
        assertFalse(excludePath("/auth/login"), "不带前缀的路径不应命中 /api 白名单");
    }

    @DisplayName("context-path 带尾部斜杠需归一化，不能拼出双斜杠")
    @Test
    void trailingSlashIsNormalized() throws Exception {
        withContextPath("/api/");
        assertEquals("/api", ReflectionTestUtils.getField(tokenFilter, "contextPathPrefix"));
        assertTrue(excludePath("/api/auth/login"));
    }

    // ------------------------------------------------------------------
    // 受保护路径：任何 context-path 组合下都不得放行
    // ------------------------------------------------------------------

    @DisplayName("受保护接口在任何 context-path 组合下都不得放行")
    @ParameterizedTest
    @ValueSource(strings = {"", "/", "/api", "/api/"})
    void protectedPathsNeverExcluded(String contextPath) throws Exception {
        withContextPath(contextPath);
        String prefix = "/api".equals(contextPath.trim().replaceAll("/$", "")) ? "/api" : "";
        assertFalse(excludePath(prefix + "/admin/user/search"));
        assertFalse(excludePath(prefix + "/knowledge-base/list"));
        assertFalse(excludePath(prefix + "/finetune/job/list"));
        assertFalse(excludePath(prefix + "/user/profile"));
    }

    // ------------------------------------------------------------------
    // token 走 query 的图片/文件路径
    // ------------------------------------------------------------------

    @DisplayName("token-in-params 路径随 context-path 正确生效")
    @Test
    void tokenInParamsRespectsContextPath() throws Exception {
        withContextPath("/");
        assertTrue(checkPathWithToken("/my-image/abc.png"));
        assertTrue(checkPathWithToken("/file/abc.pdf"));
        assertFalse(checkPathWithToken("/knowledge-base/list"));

        withContextPath("/api");
        assertTrue(checkPathWithToken("/api/my-image/abc.png"));
        assertFalse(checkPathWithToken("/my-image/abc.png"));
    }

    // ------------------------------------------------------------------
    // /admin/ 鉴权：isAdmin 判定与拆箱 NPE
    // ------------------------------------------------------------------

    @DisplayName("isAdmin 只认 TRUE；null 按非管理员处理且不抛 NPE")
    @Test
    void isAdminRecognizesTrueOnly() throws Exception {
        withContextPath("/");
        assertTrue(isAdmin(user(Boolean.TRUE)));
        assertFalse(isAdmin(user(Boolean.FALSE)));
        assertFalse(isAdmin(user(null)), "is_admin 可为 null，必须按非管理员处理而非抛 NPE");
    }

    // ------------------------------------------------------------------
    // S1-06：白名单可配置（adi.auth.exclude-urls）
    // ------------------------------------------------------------------

    @DisplayName("S1-06：默认白名单包含关键匿名端点（与原 EXCLUDE_API 常量一致）")
    @Test
    void defaultExcludeUrlsContainKeyEntries() {
        List<String> defaults = Auth.DEFAULT_EXCLUDE_URLS;
        for (String key : new String[]{"/auth/", "/model/", "/actuator/health", "/ext/v1/", "/mcp/kb/"}) {
            assertTrue(defaults.contains(key), "默认白名单缺失关键端点：" + key);
        }
    }

    @DisplayName("S1-06：配置覆盖后以配置为准（整列表替换语义）")
    @Test
    void configuredExcludeUrlsOverrideDefaults() {
        adiProperties.getAuth().setExcludeUrls(List.of("/custom/public/"));
        ReflectionTestUtils.invokeMethod(tokenFilter, "initExcludeUrls");
        withContextPathUnchecked();
        assertTrue(excludePath("/custom/public/item"), "配置的公开前缀应放行");
        assertFalse(excludePath("/auth/login"), "覆盖即整列表替换，未列入配置的默认端点不再放行");
    }

    @DisplayName("S1-06：adi.auth 缺失时回退默认白名单（不 NPE）")
    @Test
    void fallbackToDefaultsWhenAuthMissing() {
        adiProperties.setAuth(null);
        ReflectionTestUtils.invokeMethod(tokenFilter, "initExcludeUrls");
        withContextPathUnchecked();
        assertTrue(excludePath("/auth/login"), "auth 配置缺失应回退默认白名单");
    }

    private void withContextPathUnchecked() {
        try {
            withContextPath("/");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static User user(Boolean isAdmin) {
        User user = new User();
        user.setIsAdmin(isAdmin);
        return user;
    }
}
