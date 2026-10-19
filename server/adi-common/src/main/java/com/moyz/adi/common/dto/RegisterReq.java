package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.springframework.validation.annotation.Validated;

@Schema(name = "注册请求参数 | Registration Request Parameters")
@Data
@Validated
public class RegisterReq {

    @Parameter(description = "邮箱 | Email")
    @Email
    private String email;

    // @Min 只能用于数值类型。标在 String 上会让 Hibernate Validator 抛 HV000028
    // （Unexpected exception during isValid call），注册接口带密码直接 500。
    // @Min only applies to numeric types. On a String it makes Hibernate Validator throw HV000028,
    // so registration with a password failed with a server error.
    @Parameter(description = "密码 | Password")
    @Size(min = 6)
    private String password;

    @Parameter(description = "验证码ID | Captcha ID")
    @Length(min = 32)
    private String captchaId;

    @Parameter(description = "验证码 | Captcha Code")
    @Length(min = 4, max = 4)
    private String captchaCode;
}
