package com.moyz.adi.common.config.props;

import com.moyz.adi.common.constant.AuthConstant;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 注册邮箱验证码配置（二开 2026-09-28）。
 * <p>
 * 默认整体关闭：注册仍走「图形验证码 + 激活邮件链接」的老流程，接口即使被调用也会直接拒绝，
 * 避免前端开关被绕过。开启后注册改为「邮箱验证码」，校验通过即视为邮箱已验证，账号直接激活。
 * <p>
 * Registration email verification code settings. Disabled by default: registration keeps using
 * the legacy "image captcha + activation link" flow and the new endpoints refuse to run even if
 * called directly. When enabled, registration switches to an email code; a valid code proves
 * mailbox ownership, so the account is activated straight away.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.EmailCode} 嵌套类提为顶层类，Spring 绑定键不变；
 * 默认值常量由 {@code AdiConstant.AUTH_*} 改指 {@link AuthConstant}（同一常量的域接口名）。
 */
@Data
public class EmailCode {

    /**
     * 是否启用注册邮箱验证码 | Whether registration email code is enabled.
     */
    private boolean enable = false;

    /**
     * 验证码位数，默认 6 位 | Code length.
     */
    @Min(value = 4, message = "adi.register.email-code.code-length 至少 4 位")
    @Max(value = 10, message = "adi.register.email-code.code-length 至多 10 位（防邮件可读性差）")
    private int codeLength = AuthConstant.AUTH_REGISTER_EMAIL_CODE_LENGTH;

    /**
     * 验证码有效时长（分钟），默认 10 分钟 | Code lifetime in minutes.
     */
    @Min(value = 1, message = "adi.register.email-code.expire-minutes 必须 >= 1 分钟")
    private int expireMinutes = AuthConstant.AUTH_REGISTER_EMAIL_CODE_EXPIRE_MINUTES;

    /**
     * 重发间隔（秒），默认 60 秒，防止被拿来轰炸邮箱 | Resend interval in seconds.
     */
    @Min(value = 1, message = "adi.register.email-code.resend-interval-seconds 必须 >= 1 秒")
    private int resendIntervalSeconds = AuthConstant.AUTH_REGISTER_EMAIL_CODE_RESEND_INTERVAL_SECONDS;
}
