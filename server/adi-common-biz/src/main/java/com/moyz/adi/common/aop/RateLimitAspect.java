package com.moyz.adi.common.aop;

import com.moyz.adi.common.annotation.RateLimit;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.RateLimitHelper;
import com.moyz.adi.common.util.IPAddressUtil;
import com.moyz.adi.common.vo.RequestRateLimit;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.text.MessageFormat;

import static com.moyz.adi.common.enums.ErrorEnum.A_REQUEST_TOO_MUCH;

/**
 * 统一限流切面（ARCH-01，2026-10-16）。
 * <p>
 * 拦截 {@code @RateLimit} 注解的方法，Redis 计数（分钟窗口，复用 {@link RateLimitHelper}），
 * 超限抛 {@code A_REQUEST_TOO_MUCH}。key 维度由注解决定：byUser=true 按登录用户，
 * 否则按客户端 IP（走 {@link IPAddressUtil}，兼容 nginx 反代头）。
 * <p>
 * ⚠️ 仅适用于同步 HTTP 入口（controller）；SSE 流式接口沿用 SseManager 内的专用限流。
 */
@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RateLimitHelper rateLimitHelper;

    @Before("@annotation(rateLimit)")
    public void doRateLimit(JoinPoint joinPoint, RateLimit rateLimit) {
        RequestRateLimit config = new RequestRateLimit();
        config.setTimes(rateLimit.times());
        config.setMinutes(rateLimit.perMinutes());

        String key = MessageFormat.format("rate:limit:{0}:{1}",
                methodKey(joinPoint), subjectKey(rateLimit));

        if (!rateLimitHelper.checkRequestTimes(key, config)) {
            log.warn("RateLimitAspect blocked, uri:{}, key:{}", currentUri(), key);
            throw new BaseException(A_REQUEST_TOO_MUCH);
        }
        rateLimitHelper.increaseRequestTimes(key, config);
    }

    private String methodKey(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    private String subjectKey(RateLimit rateLimit) {
        if (rateLimit.byUser()) {
            Long userId = ThreadContext.getCurrentUserId();
            if (null != userId) {
                return "u" + userId;
            }
            // 匿名端点误配 byUser=true：退化为按 IP 并告警，请求仍然被限流（fail-close 的温和版）
            log.warn("@RateLimit(byUser=true) on anonymous endpoint, fallback to ip, method:{}", currentUri());
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (null == attrs) {
            // 非 HTTP 上下文（理论不可达：切面只拦 controller）：退化为全局键，至少保底限流
            return "unknown";
        }
        HttpServletRequest request = attrs.getRequest();
        return "ip" + IPAddressUtil.getClientIpAddress(request);
    }

    private String currentUri() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return null == attrs ? "-" : attrs.getRequest().getRequestURI();
    }
}
