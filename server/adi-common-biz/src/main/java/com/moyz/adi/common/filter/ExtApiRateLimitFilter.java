package com.moyz.adi.common.filter;

import com.moyz.adi.common.service.ExtApiQuotaService;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

/**
 * External API 限流与配额过滤器（T1-09，2026-10-19）。
 * <p>
 * 覆盖 {@code /ext/v1/**} 与 {@code /mcp/kb/**}（后者与 ext key 共用同一套鉴权与配额，
 * 不覆盖的话 MCP 面会成为绕过 ext 配额的旁路）。三层限制，全部以原始 ext key 的
 * SHA-256 摘要为维度（key 无效的请求由 {@link ExtApiAuthFilter} 401，这里放行）：
 * <ol>
 *   <li>单 Key QPS（默认 5，{@code adi.ext.qps-limit}）→ 429 + {@code Retry-After: 1}；</li>
 *   <li>单 Key 日调用次数（默认 10000，{@code adi.ext.daily-request-quota}）
 *       → 429 + {@code Retry-After: <距当日 0 点秒数>}；</li>
 *   <li>单 Key 日 token 配额（{@code adi.ext.daily-token-quota}，0 = 不限制）
 *       → 429 + 同上。token 由聊天类 ext 接口在 LLM 返回处调
 *       {@link ExtApiQuotaService#recordTokens} 上报，本层只做前置拦截。</li>
 * </ol>
 * ⚠️ 超限<b>必须返回真实 HTTP 429 状态码 + Retry-After 头</b>，不接受"响应体里有错误信息
 * 但状态码 200"的假实现（项目历史事故：额度检查 {@code len>0} 判 PASS 的假绿）。
 * 放在 {@code @Order(3)}：鉴权（Order 1/2）之后，非法 key 不会被计入合法调用者的配额。
 */
@Slf4j
@Component
@Order(3)
public class ExtApiRateLimitFilter extends OncePerRequestFilter {

    private static final String API_V1_PREFIX = "/ext/v1/";
    private static final String MCP_KB_PREFIX = "/mcp/kb/";
    private static final String KEY_PREFIX = "ext-";

    @Value("${server.servlet.context-path:}")
    private String contextPath;

    /** 归一化后的 context path（根路径归一化为空串），与 ExtApiAuthFilter 同款处理 */
    private String contextPathPrefix() {
        if (StringUtils.isBlank(contextPath)) {
            return "";
        }
        String normalized = contextPath.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    @Resource
    private ExtApiQuotaService quotaService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String prefix = contextPathPrefix();
        boolean isExtApi = requestUri.startsWith(prefix + API_V1_PREFIX);
        boolean isMcpKb = requestUri.startsWith(prefix + MCP_KB_PREFIX);
        if (!isExtApi && !isMcpKb) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!quotaService.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String rawKey = request.getHeader(AUTHORIZATION);
        if (StringUtils.isBlank(rawKey) || !rawKey.trim().startsWith(KEY_PREFIX)) {
            // 无 key / 非 ext key：交给 ExtApiAuthFilter 401，不占用限流维度
            filterChain.doFilter(request, response);
            return;
        }
        String keyHash = ExtApiQuotaService.sha256Short(rawKey.trim());

        if (!quotaService.tryAcquireQps(keyHash)) {
            log.warn("ExtAPI rate limited (qps), uri:{}, keyHash:{}", requestUri, keyHash);
            reject(response, 1, "rate limit exceeded (qps)");
            return;
        }
        if (!quotaService.tryConsumeDailyRequest(keyHash)) {
            log.warn("ExtAPI rate limited (daily request quota), uri:{}, keyHash:{}", requestUri, keyHash);
            reject(response, ExtApiQuotaService.secondsUntilMidnight(), "daily request quota exceeded");
            return;
        }
        if (quotaService.isTokenQuotaExceeded(keyHash, 0)) {
            log.warn("ExtAPI rate limited (daily token quota), uri:{}, keyHash:{}", requestUri, keyHash);
            reject(response, ExtApiQuotaService.secondsUntilMidnight(), "daily token quota exceeded");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 统一的 429 响应：真实状态码 429 + Retry-After 头 + JSON 错误体。
     */
    private void reject(HttpServletResponse response, long retryAfterSeconds, String message) throws IOException {
        response.setStatus(429);
        response.setHeader(RETRY_AFTER, String.valueOf(Math.max(1, retryAfterSeconds)));
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"A_REQUEST_TOO_MUCH\",\"message\":\"" + message + "\"}");
    }
}
