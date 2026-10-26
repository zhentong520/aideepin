package com.moyz.adi.common.constant;

/**
 * 认证/账号域常量（OPT-17 拆分：原 AdiConstant 顶层常量，引用集中在
 * UserService 与 AdiProperties 默认值）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有 {@code AdiConstant.X} 引用无需改动。
 */
public interface AuthConstant {

    /**
     * 验证码id过期时间：1小时
     */
    int AUTH_CAPTCHA_ID_EXPIRE = 1;

    /**
     * 验证码过期时间，5分钟
     */
    int AUTH_CAPTCHA_EXPIRE = 5;

    /**
     * 注册激活码有效时长，8小时
     */
    int AUTH_ACTIVE_CODE_EXPIRE = 8;

    /**
     * 注册邮箱验证码默认有效时长，10分钟（可被 adi.register.email-code.expire-minutes 覆盖）
     * Default lifetime of the registration email code (minutes).
     */
    int AUTH_REGISTER_EMAIL_CODE_EXPIRE_MINUTES = 10;

    /**
     * 注册邮箱验证码默认重发间隔，60秒（可被 adi.register.email-code.resend-interval-seconds 覆盖）
     * Default resend interval of the registration email code (seconds).
     */
    int AUTH_REGISTER_EMAIL_CODE_RESEND_INTERVAL_SECONDS = 60;

    /**
     * 注册邮箱验证码默认位数，6位（可被 adi.register.email-code.code-length 覆盖）
     * Default length of the registration email code.
     */
    int AUTH_REGISTER_EMAIL_CODE_LENGTH = 6;

    /**
     * token存活时间（8小时）
     */
    int USER_TOKEN_EXPIRE = 8;

    /**
     * 用户自设密码的长度约束（S-07：重置/修改密码共用）
     */
    int PASSWORD_MIN_LENGTH = 6;
    int PASSWORD_MAX_LENGTH = 64;

    /**
     * 找回密码邮件发送冷却秒数（S-08：防邮件轰炸）
     */
    int FORGOT_PASSWORD_COOLDOWN_SECONDS = 60;

    int LOGIN_MAX_FAIL_TIMES = 3;
}
