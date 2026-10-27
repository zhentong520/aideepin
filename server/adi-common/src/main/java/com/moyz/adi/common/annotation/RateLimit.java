package com.moyz.adi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一限流注解（ARCH-01，2026-10-16）。
 * <p>
 * 此前限流散落两处硬编码（SseManager / DrawService），验证码、找回密码、注册等
 * 敏感端点没有任何频控。现在声明式挂注解即可限流，实现收敛到 {@code RateLimitAspect}
 * （底层复用 {@code RateLimitHelper}，Redis 计数，分钟窗口）。
 * <p>
 * 用法：
 * <pre>{@code
 * @RateLimit(times = 5, perMinutes = 1)                     // 按 IP 限流（默认，适用于匿名端点）
 * @RateLimit(times = 20, perMinutes = 1, byUser = true)     // 按登录用户限流
 * }</pre>
 * 超限抛 {@code ErrorEnum.A_REQUEST_TOO_MUCH}（与 DrawService 现行为一致）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内允许的最大次数 */
    int times();

    /** 时间窗口（分钟） */
    int perMinutes() default 1;

    /**
     * true = 按登录用户限流（需登录态）；false = 按客户端 IP 限流（默认，适用于匿名端点）。
     * 对匿名端点误配 byUser=true 会拿到 null 用户，切面会退化为按 IP 限流并告警。
     */
    boolean byUser() default false;

    /**
     * S1-13（2026-10-27）：true = 账号+IP 双维度独立限流（两把计数器各自判超限）。
     * 账号取自方法首个参数对象的 {@code getEmail()} / {@code getAccount()}（先 email 后 account）。
     * 适用匿名但携带账号的端点（登录）：单靠 IP 可被攻击者轮换出口绕过，
     * 单靠账号可被高频打别人账号锁死——两把锁同时存在才完整。
     * 与 byUser 互斥使用（同时为 true 时以双维度为准）。
     */
    boolean byAccountAndIp() default false;
}
