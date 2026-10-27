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
import java.util.ArrayList;
import java.util.List;

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

        if (rateLimit.byAccountAndIp()) {
            // S1-13（2026-10-27）：账号+IP 双维度——两把计数器各自判超限，任一命中即拒绝。
            // 攻击者轮换 IP 绕不过账号锁，高频打别人账号也绕不过 IP 锁。
            for (String key : buildKeys(joinPoint, rateLimit)) {
                if (!rateLimitHelper.checkRequestTimes(key, config)) {
                    log.warn("RateLimitAspect blocked, uri:{}, key:{}", currentUri(), key);
                    throw new BaseException(A_REQUEST_TOO_MUCH);
                }
                rateLimitHelper.increaseRequestTimes(key, config);
            }
            return;
        }

        String key = MessageFormat.format("rate:limit:{0}:{1}",
                methodKey(joinPoint), subjectKey(rateLimit));

        if (!rateLimitHelper.checkRequestTimes(key, config)) {
            log.warn("RateLimitAspect blocked, uri:{}, key:{}", currentUri(), key);
            throw new BaseException(A_REQUEST_TOO_MUCH);
        }
        rateLimitHelper.increaseRequestTimes(key, config);
    }

    /**
     * S1-13：构建限流键。双维度模式返回 [ip 键, 账号键]；账号缺失（理论不可达，
     * 登录参数已 @NotBlank）时退化为仅 IP 键。包级可见便于单测。
     */
    List<String> buildKeys(JoinPoint joinPoint, RateLimit rateLimit) {
        String methodKey = methodKey(joinPoint);
        List<String> keys = new ArrayList<>(2);
        keys.add(MessageFormat.format("rate:limit:{0}:ip{1}", methodKey, currentClientIp()));
        String account = extractAccount(joinPoint);
        if (account != null && !account.isBlank()) {
            keys.add(MessageFormat.format("rate:limit:{0}:acc{1}", methodKey, account.trim().toLowerCase()));
        }
        return keys;
    }

    /** 从首个参数对象提取账号：优先 getEmail()，其次 getAccount()，均无返回 null。 */
    private String extractAccount(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        Object first = args[0];
        for (String getter : new String[]{"getEmail", "getAccount"}) {
            try {
                Object value = first.getClass().getMethod(getter).invoke(first);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            } catch (ReflectiveOperationException ignored) {
                // 参数对象没有该字段，尝试下一个
            }
        }
        return null;
    }

    private String currentClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (null == attrs) {
            return "unknown";
        }
        return IPAddressUtil.getClientIpAddress(attrs.getRequest());
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
