package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 邮箱验证码注册的请求参数（二开 2026-09-28）。
 * <p>
 * Request payload for registering with an email verification code.
 */
@Schema(name = "邮箱验证码注册请求参数 | Register by email code request")
@Data
public class RegisterByEmailCodeReq {

    @Parameter(description = "邮箱 | Email")
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email format is invalid")
    private String email;

    // @Min 只能用于数值类型，标在 String 上会让 Hibernate Validator 抛 HV000028。
    // @Min only applies to numeric types; on a String it makes Hibernate Validator throw HV000028.
    @Parameter(description = "密码 | Password")
    @NotBlank(message = "Password cannot be empty")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @Parameter(description = "邮箱验证码 | Email verification code")
    @NotBlank(message = "Email code cannot be empty")
    @Length(min = 4, max = 10, message = "Email code length is invalid")
    private String emailCode;
}
