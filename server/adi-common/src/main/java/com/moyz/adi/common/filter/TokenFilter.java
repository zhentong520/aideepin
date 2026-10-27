package com.moyz.adi.common.filter;

import com.moyz.adi.common.base.BaseResponse;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Auth;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.constant.RedisKeyConstant;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.SpringUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@Component
@Order(2)
public class TokenFilter extends OncePerRequestFilter {

    /**
     * S1-06（2026-10-27）：匿名放行白名单从硬编码常量改为可配置注入（adi.auth.exclude-urls），
     * 默认值见 {@link com.moyz.adi.common.config.props.Auth#DEFAULT_EXCLUDE_URLS}（与原
     * EXCLUDE_API 常量逐条一致），未配置时行为不变。
     */
    private List<String> excludeUrls;

    protected static final String[] TOKEN_IN_PARAMS = {
            "/my-image/",
            "/my-thumbnail/",
            "/image/",
            "/file/"
    };

    /** OPT-12：springdoc 文档资源，仅当 springdoc.api-docs.enabled=true 时放行（见 apiDocsEnabled）。 */
    protected static final String[] OPENAPI_RESOURCES = {
            "/v3/api-docs",
            "/swagger-ui",
            "/webjars/",
    };

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private AdiProperties adiProperties;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /**
     * OPT-12（2026-10-24）：OpenAPI 契约出库。springdoc 的文档端点跟随
     * {@code springdoc.api-docs.enabled} 配置放行——dev/test 默认开（application.yml 未关），
     * prod 显式 false（application-prod.yml），生产行为不变；
     * CI 契约门禁（导出 openapi.json 供前端生成类型）依赖此放行。
     */
    @Value("${springdoc.api-docs.enabled:true}")
    private boolean apiDocsEnabled;

    /**
     * 归一化后的 context path：去掉尾部 '/'，根路径（/）归一化为空串。
     * 直接拼接配置原值是错的：配置写 {@code context-path: /} 时会拼出 {@code //auth/}，
     * 与 requestURI 永远不匹配，导致整份白名单静默失效。
     */
    private String contextPathPrefix;

    @PostConstruct
    public void initContextPathPrefix() {
        String raw = contextPath;
        if (StringUtils.isBlank(raw)) {
            this.contextPathPrefix = "";
            return;
        }
        String normalized = raw.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        this.contextPathPrefix = normalized;
        log.info("TokenFilter context path prefix: '{}'", this.contextPathPrefix);
    }

    /** 白名单单独装填：@PostConstruct 拿 bean 依赖，便于测试里独立替换（S1-06）。 */
    @PostConstruct
    public void initExcludeUrls() {
        Auth auth = adiProperties.getAuth();
        this.excludeUrls = (auth == null || auth.getExcludeUrls() == null)
                ? new ArrayList<>(Auth.DEFAULT_EXCLUDE_URLS)
                : auth.getExcludeUrls();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String requestUri = request.getRequestURI();
            String token = request.getHeader(AUTHORIZATION);
            if (StringUtils.isBlank(token) && checkPathWithToken(requestUri)) {
                token = request.getParameter("token");
            }
            if (excludePath(requestUri)) {

                if (StringUtils.isNotBlank(token)) {
                    // S1-10：白名单路径本就允许匿名访问，Redis 故障时降级为"未登录"继续放行，
                    // 而不是把登录页/公开接口一起拖成 500。恢复后下个请求自动带回用户身份。
                    try {
                        String tokenKey = MessageFormat.format(RedisKeyConstant.USER_TOKEN, token);
                        String userJson = stringRedisTemplate.opsForValue().get(tokenKey);
                        if (StringUtils.isNotBlank(userJson)) {
                            User user = JsonUtil.fromJson(userJson, User.class);
                            if (null != user) {
                                ThreadContext.setCurrentUser(user);
                                ThreadContext.setToken(token);
                            }
                        }
                    } catch (DataAccessException e) {
                        log.warn("TokenFilter degraded (redis unavailable) on whitelist path:{}, cause:{}", requestUri, e.getMessage());
                    }
                }
                filterChain.doFilter(request, response);
            } else if (StringUtils.isNotBlank(token)) {
                String userJson;
                try {
                    String tokenKey = MessageFormat.format(RedisKeyConstant.USER_TOKEN, token);
                    userJson = stringRedisTemplate.opsForValue().get(tokenKey);
                } catch (DataAccessException e) {
                    // S1-10：受保护路径无法核实 token 时，既不能放行（越权）也不能 401
                    //（401 会误导前端反复跳登录页），明确报 503"服务暂不可用"。
                    log.error("TokenFilter redis unavailable on protected path:{}", requestUri, e);
                    respondServiceUnavailable(response);
                    return;
                }
                if (StringUtils.isBlank(userJson)) {
                    log.warn("Not logged in:{}", requestUri);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }

                User user = JsonUtil.fromJson(userJson, User.class);
                if (null == user) {
                    log.warn("User not found:{}", requestUri);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                // requestURI 含 context-path，必须与白名单一样拼上前缀，
                // 否则 context-path 一旦不是根路径，/admin/ 校验会整体失效
                if (!isAdmin(user) && requestUri.startsWith(contextPathPrefix + "/admin/")) {
                    log.warn("No admin permission:{}", requestUri);
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                ThreadContext.setCurrentUser(user);
                ThreadContext.setToken(token);
                filterChain.doFilter(request, response);
            } else {
                log.warn("Unauthorized:{}", requestUri);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
        } finally {
            // External API requests are cleaned up by ExtApiAuthFilter
            if (!ThreadContext.isExtApiRequest()) {
                ThreadContext.unload();
            }
        }
    }

    private boolean excludePath(String requestUri) {
        for (String path : excludeUrls) {
            if (requestUri.startsWith(contextPathPrefix + path)) {
                return true;
            }
        }
        if (apiDocsEnabled) {
            for (String path : OPENAPI_RESOURCES) {
                if (requestUri.startsWith(contextPathPrefix + path)) {
                    return true;
                }
            }
        }
        for (String path : AdiConstant.WEB_RESOURCES) {
            if (requestUri.startsWith(contextPathPrefix + path)) {
                return true;
            }
            // 只对完整路径（以 / 开头）做后缀匹配。
            // 若允许裸扩展名走 endsWith，「任意受保护 URL + .js」即可绕过鉴权与 /admin/ 校验。
            if (path.startsWith("/") && requestUri.endsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPathWithToken(String requestUri) {
        for (String path : TOKEN_IN_PARAMS) {
            if (requestUri.startsWith(contextPathPrefix + path)) {
                return true;
            }
        }
        return false;
    }

    /** is_admin 列可空，null 视为非管理员；避免原先的拆箱 NPE 写法。 */
    private boolean isAdmin(User user) {
        return user != null && Boolean.TRUE.equals(user.getIsAdmin());
    }

    /**
     * S1-10：Redis 故障时受保护路径返回 503 + 结构化错误体（A_REDIS_UNAVAILABLE），
     * 前端可凭 code 与 401 区分"该去登录"和"服务端依赖故障"。
     */
    private void respondServiceUnavailable(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JsonUtil.toJson(new BaseResponse<>(
                ErrorEnum.A_REDIS_UNAVAILABLE.getCode(),
                SpringUtil.getMessage(ErrorEnum.A_REDIS_UNAVAILABLE.getInfo()),
                null)));
    }
}
