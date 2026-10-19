package com.moyz.adi.common.aop;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import static com.moyz.adi.common.enums.ErrorEnum.A_NO_ADMIN_PERMISSION;

/**
 * {@link AdminOnly} 切面（ARCH-01，2026-10-16）。
 * <p>
 * 类级或方法级声明：当前登录用户必须是管理员（is_admin=true），否则抛
 * {@code A_NO_ADMIN_PERMISSION}。未登录（ThreadContext 无用户）同样拒绝。
 * 这是 {@code TokenFilter} URL 前缀校验之外的第二道防线，不替代它。
 */
@Slf4j
@Aspect
@Component
public class AdminOnlyAspect {

    @Before("@within(com.moyz.adi.common.annotation.AdminOnly) || @annotation(com.moyz.adi.common.annotation.AdminOnly)")
    public void checkAdmin(JoinPoint joinPoint) {
        User user = ThreadContext.getCurrentUser();
        if (null == user || !Boolean.TRUE.equals(user.getIsAdmin())) {
            log.warn("AdminOnly denied, user:{}, method:{}.{}",
                    null == user ? "anonymous" : user.getEmail(),
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName());
            throw new BaseException(A_NO_ADMIN_PERMISSION);
        }
    }
}
