package com.moyz.adi.common.filter;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.util.JsonUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TokenFilter 的 HTTP 层行为测试（不依赖数据库/Redis 实例，Redis 用 mock）。
 *
 * <p>与 {@link TokenFilterExcludePathTest} 的分工：那边直接断言判定方法，这边把请求真正
 * 过一遍 {@code doFilterInternal}，验证「响应码 + ThreadContext」这一对外可见的行为。
 *
 * <p>钉死的三条对外契约：
 * <ol>
 *   <li>免登录白名单（/auth/login 等）→ 放行，且不写 ThreadContext；</li>
 *   <li>受保护路径无 token → 401；</li>
 *   <li>非管理员访问 /admin/** → 401（context-path 为根时也要成立）。</li>
 * </ol>
 */
class TokenFilterHttpBehaviourTest {

    private static final String VALID_TOKEN = "valid-token";

    private TokenFilter filter;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        filter = new TokenFilter();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(filter, "stringRedisTemplate", redisTemplate);
        // S1-06：白名单改为 adi.auth.exclude-urls 注入，无容器环境手动装填（默认值=原常量）
        ReflectionTestUtils.setField(filter, "adiProperties", new com.moyz.adi.common.config.AdiProperties());
        ReflectionTestUtils.invokeMethod(filter, "initExcludeUrls");
        setContextPath("/");
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
    }

    private void setContextPath(String raw) throws Exception {
        ReflectionTestUtils.setField(filter, "contextPath", raw);
        Method init = TokenFilter.class.getDeclaredMethod("initContextPathPrefix");
        init.setAccessible(true);
        init.invoke(filter);
    }

    /** 让 Redis 返回一个已登录用户 */
    private void givenLoggedInUser(User user) {
        when(valueOperations.get(anyString())).thenReturn(JsonUtil.toJson(user));
    }

    private MockHttpServletResponse doFilter(String uri, String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (null != token) {
            request.addHeader("Authorization", token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }

    private static User user(Boolean isAdmin) {
        User user = new User();
        user.setUuid("u-1");
        user.setEmail("a@b.c");
        user.setIsAdmin(isAdmin);
        return user;
    }

    // ------------------------------------------------------------------
    // 契约 1：免登录白名单
    // ------------------------------------------------------------------

    @DisplayName("context-path=/ 时 /auth/login 必须免登录放行（回归 //auth/ 拼接缺陷）")
    @Test
    void authLoginIsPublic() throws Exception {
        MockHttpServletResponse response = doFilter("/auth/login", null);
        assertEquals(200, response.getStatus(), "/auth/login 不该被 401 拦下");
        assertNull(ThreadContext.getCurrentUser(), "未带 token 时不应写入当前用户");
    }

    @ParameterizedTest(name = "{0} 应免登录")
    @CsvSource({
            "/model/platforms",
            "/knowledge-base/public/list",
            "/ext/v1/finetune/worker/heartbeat",
            "/mcp/public/list",
            "/sys/config/public/get"
    })
    void whitelistPathsArePublic(String uri) throws Exception {
        assertEquals(200, doFilter(uri, null).getStatus(), uri + " 应在白名单内");
    }

    // ------------------------------------------------------------------
    // 契约 2：受保护路径
    // ------------------------------------------------------------------

    @DisplayName("受保护路径无 token 一律 401，且不进入业务链")
    @ParameterizedTest(name = "{0} 无 token → 401")
    @CsvSource({
            "/admin/user/search",
            "/knowledge-base/list",
            "/finetune/job/list",
            "/user/profile"
    })
    void protectedPathsRejectAnonymous(String uri) throws Exception {
        assertEquals(401, doFilter(uri, null).getStatus(), uri + " 应要求登录");
    }

    @DisplayName("Redis 中无有效 token → 401")
    @Test
    void invalidTokenRejected() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        assertEquals(401, doFilter("/user/profile", VALID_TOKEN).getStatus());
    }

    @DisplayName("有效 token → 放行，且在业务链中能读到用户与 token")
    @Test
    void validTokenPasses() throws Exception {
        givenLoggedInUser(user(false));

        // 过滤器在 finally 里会清理 ThreadContext（防线程复用串号），
        // 所以必须在**业务链执行期间**取证，请求结束后再断言必然是 null。
        User[] capturedUser = new User[1];
        String[] capturedToken = new String[1];

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/user/profile");
        request.addHeader("Authorization", VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                capturedUser[0] = ThreadContext.getCurrentUser();
                capturedToken[0] = ThreadContext.getToken();
            }
        });

        assertEquals(200, response.getStatus());
        assertNotNull(capturedUser[0], "业务链中应能读到当前用户");
        assertSame(VALID_TOKEN, capturedToken[0]);
        // 请求结束后必须已清理，避免 Tomcat 线程复用时串号
        assertNull(ThreadContext.getCurrentUser(), "请求结束应清理上下文");
    }

    // ------------------------------------------------------------------
    // 契约 3：/admin/** 的管理员校验
    // ------------------------------------------------------------------

    @DisplayName("非管理员访问 /admin/** → 401")
    @Test
    void nonAdminForbidden() throws Exception {
        givenLoggedInUser(user(false));
        assertEquals(401, doFilter("/admin/user/search", VALID_TOKEN).getStatus());
    }

    @DisplayName("管理员访问 /admin/** → 放行")
    @Test
    void adminAllowed() throws Exception {
        givenLoggedInUser(user(true));
        assertEquals(200, doFilter("/admin/user/search", VALID_TOKEN).getStatus());
    }

    @DisplayName("is_admin 为 null 按非管理员处理（不得因拆箱 NPE 变成 500）")
    @Test
    void nullAdminTreatedAsNonAdmin() throws Exception {
        givenLoggedInUser(user(null));
        assertEquals(401, doFilter("/admin/user/search", VALID_TOKEN).getStatus());
    }

    // ------------------------------------------------------------------
    // 核心回归：.js 后缀不得绕过
    // ------------------------------------------------------------------

    @DisplayName("URL 追加 .js 不得绕过鉴权（核心回归）")
    @ParameterizedTest(name = "{0} + .js → 仍 401")
    @CsvSource({
            "/admin/user/info/abc.js",
            "/admin/kb/info/abc.js",
            "/admin/finetune/job/abc.js"
    })
    void jsSuffixCannotBypassAdminAuth(String uri) throws Exception {
        // 非管理员带有效 token：修复前会因 endsWith('.js') 落入白名单分支、
        // 跳过 /admin/ 校验而 200 放行
        givenLoggedInUser(user(false));
        assertEquals(401, doFilter(uri, VALID_TOKEN).getStatus(),
                uri + " 不应绕过 /admin/ 校验");
    }

    @DisplayName("URL 追加 .js 不得让受保护路径变成免登录（核心回归）")
    @ParameterizedTest(name = "{0} + .js 无 token → 401")
    @CsvSource({"/user/profile.js", "/knowledge-base/list.js", "/finetune/job/list.js"})
    void jsSuffixCannotBypassLogin(String uri) throws Exception {
        assertEquals(401, doFilter(uri, null).getStatus(),
                uri + " 拼接 .js 后不应被放行");
    }

    @DisplayName("静态资源仍可匿名访问（修复不能误伤 swagger/doc）")
    @ParameterizedTest(name = "{0} 免登录")
    @CsvSource({"/doc.html", "/swagger-ui", "/swagger-ui/index.html", "/v3/api-docs", "/favicon.ico"})
    void staticResourcesStillPublic(String uri) throws Exception {
        assertEquals(200, doFilter(uri, null).getStatus(), uri + " 应保持免登录");
    }

    // ------------------------------------------------------------------
    // 过滤器上下文清理
    // ------------------------------------------------------------------

    @DisplayName("请求结束后必须清理 ThreadContext，避免线程复用串号")
    @Test
    void contextClearedAfterRequest() throws Exception {
        givenLoggedInUser(user(false));
        doFilter("/user/profile", VALID_TOKEN);
        assertNull(ThreadContext.getCurrentUser(), "请求结束应清理上下文");
    }

    @DisplayName("401 响应同样不应残留上下文")
    @Test
    void contextClearedOnUnauthorized() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(null);
        doFilter("/user/profile", VALID_TOKEN);
        assertNull(ThreadContext.getCurrentUser());
    }

    @DisplayName("白名单路径带 token 时也会解析出用户（可选登录语义）")
    @Test
    void whitelistWithTokenResolvesUser() throws Exception {
        givenLoggedInUser(user(false));
        User[] captured = new User[1];

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/model/platforms");
        request.addHeader("Authorization", VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured[0] = ThreadContext.getCurrentUser();
            }
        });

        assertEquals(200, response.getStatus());
        assertNotNull(captured[0], "白名单路径带有效 token 时应解析出用户");
    }

    // ------------------------------------------------------------------
    // S1-10：Redis 故障降级（停 Redis 不是全站 500）
    // ------------------------------------------------------------------

    private void givenRedisDown() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused: localhost/127.0.0.1:6379"));
    }

    @DisplayName("S1-10：Redis 故障时白名单路径降级为匿名放行（200，不 500）")
    @Test
    void whitelistPathDegradedToAnonymousWhenRedisDown() throws Exception {
        givenRedisDown();
        User[] captured = new User[1];

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/model/platforms");
        request.addHeader("Authorization", VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                captured[0] = ThreadContext.getCurrentUser();
            }
        });

        assertEquals(200, response.getStatus(), "白名单路径 Redis 故障时应继续放行而非 500");
        assertNull(captured[0], "降级期间不得伪造登录态");
    }

    @DisplayName("S1-10：Redis 故障时受保护路径返回 503 + A_REDIS_UNAVAILABLE（不 500、不 401）")
    @Test
    void protectedPathReturns503WhenRedisDown() throws Exception {
        givenRedisDown();
        MockHttpServletResponse response = doFilter("/user/profile", VALID_TOKEN);
        assertEquals(503, response.getStatus(), "受保护路径应明确报服务暂不可用");
        String body = response.getContentAsString();
        assertNotNull(body);
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("A_REDIS_UNAVAILABLE"),
                "503 响应体应携带 A_REDIS_UNAVAILABLE 错误码，实际:" + body);
        assertNull(ThreadContext.getCurrentUser(), "降级拒绝时不得残留上下文");
    }

    @DisplayName("S1-10：Redis 恢复后自动痊愈（无需重启）")
    @Test
    void recoversAfterRedisComesBack() throws Exception {
        givenRedisDown();
        assertEquals(503, doFilter("/user/profile", VALID_TOKEN).getStatus());

        // Redis 回来了：重新打桩（旧桩会抛异常，须用 doReturn 语法覆盖），下一个请求恢复正常
        org.mockito.Mockito.doReturn(JsonUtil.toJson(user(false))).when(valueOperations).get(anyString());
        assertEquals(200, doFilter("/user/profile", VALID_TOKEN).getStatus(), "Redis 恢复后应自动痊愈");
    }
}
