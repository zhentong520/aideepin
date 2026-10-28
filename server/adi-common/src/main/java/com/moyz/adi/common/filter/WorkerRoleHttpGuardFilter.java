package com.moyz.adi.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * S2-05：worker 角色 HTTP 收敛门。
 * <p>
 * worker 实例只消费 task_queue，业务 HTTP 一律 404，仅放行 {@code /actuator/health*}
 * 供探活（满足「ADI_ROLE=worker 启动的实例不监听业务 HTTP（或仅暴露健康检查）」验收）。
 * 仅在 {@code adi.role=worker} 时装配（见 {@link EnabledCondition}），api/all 模式下该
 * Filter 不存在、零开销。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Conditional(WorkerRoleHttpGuardFilter.EnabledCondition.class)
public class WorkerRoleHttpGuardFilter extends OncePerRequestFilter {

    private static final String BLOCKED_BODY =
            "{\"code\":\"not_found\",\"message\":\"worker instance does not serve http\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/actuator/health") || "/error".equals(uri)) {
            chain.doFilter(request, response);
            return;
        }
        log.debug("Worker instance blocked http request:{}", uri);
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(BLOCKED_BODY);
    }

    /** 仅 {@code adi.role=worker}（忽略大小写/首尾空白）时装配；缺失或其它值不装配 */
    public static class EnabledCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String role = context.getEnvironment()
                    .getProperty("adi.role", "all")
                    .trim()
                    .toLowerCase(Locale.ROOT);
            return "worker".equals(role);
        }
    }
}
