package com.moyz.adi.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * S-07：找回密码流程的「设置新密码」请求。
 * 服务端不再产生/告知固定口令，新密码由用户自设；
 * 长度下限在服务层 {@code assertPasswordStrength} 双重校验，此处做入口约束。
 */
@Data
public class PasswordResetReq {

    @NotBlank(message = "Reset code cannot be empty")
    private String code;

    @NotBlank(message = "New password cannot be empty")
    @Length(min = 6, max = 64, message = "Password must be 6-64 characters")
    private String newPassword;
}
