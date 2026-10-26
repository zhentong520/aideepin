package com.moyz.adi.common.config.props;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * 注册相关配置 | Registration settings.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Register} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Register {

    /**
     * 注册邮箱验证码配置 | Registration email verification code settings.
     */
    @Valid
    private EmailCode emailCode = new EmailCode();
}
