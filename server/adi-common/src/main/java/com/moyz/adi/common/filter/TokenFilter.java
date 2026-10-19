package com.moyz.adi.common.filter;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.cosntant.RedisKeyConstant;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.util.JsonUtil;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.text.MessageFormat;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

@Slf4j
@Component
@Order(2)
public class TokenFilter extends OncePerRequestFilter {

    protected static final String[] EXCLUDE_API = {
            "/auth/",
            "/model/",
            "/user/avatar/",
            "/draw/public/",
            "/draw/detail/",
            "/draw/comment/list",
            "/knowledge-base/public/",
            "/workflow/public",
            "/mcp/public",
            "/sys/config/public/",
            "/ext/v1/",
            // 二开（T1-10，2026-10-19）：KB 的 MCP Server 端点，由 ExtApiAuthFilter 用
            // knowledge 资源级 ext key 鉴权（ExtApiAuthFilter 先于本过滤器执行并设置主体）
            "/mcp/kb/",
            // OPS-01：容器探活端点（application.yml 已限制只暴露 health 一个 actuator 端点）
            "/actuator/health",
    };

    protected static final String[] TOKEN_IN_PARAMS = {
            "/my-image/",
            "/my-thumbnail/",
            "/image/",
            "/file/"
    };

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${server.servlet.context-path:}")
    private String contextPath;

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
                    String tokenKey = MessageFormat.format(RedisKeyConstant.USER_TOKEN, token);
                    String userJson = stringRedisTemplate.opsForValue().get(tokenKey);
                    if (StringUtils.isNotBlank(userJson)) {
                        User user = JsonUtil.fromJson(userJson, User.class);
                        if (null != user) {
                            ThreadContext.setCurrentUser(user);
                            ThreadContext.setToken(token);
                        }
                    }
                }
                filterChain.doFilter(request, response);
            } else if (StringUtils.isNotBlank(token)) {
                String tokenKey = MessageFormat.format(RedisKeyConstant.USER_TOKEN, token);
                String userJson = stringRedisTemplate.opsForValue().get(tokenKey);
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
        for (String path : EXCLUDE_API) {
            if (requestUri.startsWith(contextPathPrefix + path)) {
                return true;
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
}
