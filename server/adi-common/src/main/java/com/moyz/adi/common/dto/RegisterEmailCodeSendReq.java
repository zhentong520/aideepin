package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 发送注册邮箱验证码的请求参数（二开 2026-09-28）。
 * <p>
 * Request payload for sending the registration email verification code.
 */
@Schema(name = "发送注册邮箱验证码请求参数 | Send register email code request")
@Data
public class RegisterEmailCodeSendReq {

    @Parameter(description = "邮箱 | Email")
    @NotBlank(message = "Email cannot be empty")
    @Email(message = "Email format is invalid")
    private String email;
}
