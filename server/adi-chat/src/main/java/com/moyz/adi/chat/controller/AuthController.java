package com.moyz.adi.chat.controller;

import com.moyz.adi.common.annotation.RateLimit;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.LoginReq;
import com.moyz.adi.common.dto.LoginResp;
import com.moyz.adi.common.dto.PasswordResetReq;
import com.moyz.adi.common.dto.RegisterByEmailCodeReq;
import com.moyz.adi.common.dto.RegisterEmailCodeSendReq;
import com.moyz.adi.common.dto.RegisterReq;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.searchengine.SearchEngineServiceContext;
import com.moyz.adi.common.service.UserService;
import com.moyz.adi.common.vo.SearchEngineInfo;
import com.ramostear.captcha.HappyCaptcha;
import com.ramostear.captcha.support.CaptchaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;

import static com.moyz.adi.common.enums.ErrorEnum.B_ACTIVE_USER_ERROR;
import static com.moyz.adi.common.enums.ErrorEnum.B_RESET_PASSWORD_ERROR;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.SET_COOKIE;


@Slf4j
@Tag(name = "权限controller | Auth Controller", description = "权限controller | Auth Controller")
@Validated
@RestController
@RequestMapping("auth")
public class AuthController {

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private UserService userService;

    @Operation(summary = "注册 | Register")
    @PostMapping(value = "/register", produces = MediaType.TEXT_PLAIN_VALUE)
    public String register(@RequestBody RegisterReq registerReq) {
        userService.register(registerReq.getEmail(), registerReq.getPassword(), registerReq.getCaptchaId(), registerReq.getCaptchaCode());
        return "Activation link sent to your email, please check and activate";
    }

    @Operation(summary = "注册的验证码 | Registration Captcha")
    @GetMapping("/register/captcha")
    public void registerCaptcha(@Parameter(description = "验证码ID | Captcha ID") @RequestParam @Length(min = 32) String captchaId,
                                HttpServletRequest request,
                                HttpServletResponse response) {
        HappyCaptcha happyCaptcha = HappyCaptcha.require(request, response).type(CaptchaType.WORD_NUMBER_UPPER).build().finish();
        String captchaCode = happyCaptcha.getCode();
        userService.cacheRegisterCaptcha(captchaId, captchaCode);
        happyCaptcha.output();
    }

    @Operation(summary = "发送注册邮箱验证码 | Send registration email code",
            description = "需 adi.register.email-code.enable=true，否则直接报错 | Requires adi.register.email-code.enable=true, otherwise it fails fast")
    @PostMapping(value = "/register/email-code", produces = MediaType.TEXT_PLAIN_VALUE)
    public String sendRegisterEmailCode(@Validated @RequestBody RegisterEmailCodeSendReq req) {
        userService.sendRegisterEmailCode(req.getEmail());
        return "Verification code sent to your email, please check and fill it in";
    }

    @Operation(summary = "邮箱验证码注册 | Register by email code",
            description = "需 adi.register.email-code.enable=true；校验通过即激活账号并直接返回登录信息 | Requires adi.register.email-code.enable=true; a valid code activates the account and returns login info")
    @PostMapping("/register/by-email-code")
    public LoginResp registerByEmailCode(@Validated @RequestBody RegisterByEmailCodeReq req) {
        return userService.registerByEmailCode(req.getEmail(), req.getPassword(), req.getEmailCode());
    }

    @Operation(summary = "激活 | Activate")
    @GetMapping("active")
    public boolean active(@RequestParam("code") String activeCode, HttpServletResponse response) {

        try {
            userService.active(activeCode);
            response.sendRedirect(adiProperties.getFrontendUrl() + "/#/active?active=success&msg=" + URLEncoder.encode("Activation successful, please login", Charset.defaultCharset()));
        } catch (IOException e) {
            log.error("auth.active1:", e);
            try {
                response.sendRedirect(adiProperties.getFrontendUrl() + "/#/active?active=fail&msg=" + URLEncoder.encode("Activation failed: system error, please register or login again", Charset.defaultCharset()));
            } catch (IOException ex) {
                log.error("auth.active2:", ex);
                throw new BaseException(B_ACTIVE_USER_ERROR);
            }
        } catch (Exception e) {
            try {
                response.sendRedirect(adiProperties.getFrontendUrl() + "/#/active?active=fail&msg=" + URLEncoder.encode(e.getMessage(), Charset.defaultCharset()));
            } catch (IOException ex) {
                log.error("auth.active3:", ex);
                throw new BaseException(B_ACTIVE_USER_ERROR);
            }
        }
        return true;
    }

    @Operation(summary = "忘记密码 | Forgot Password")
    @RateLimit(times = 3, perMinutes = 1)
    @PostMapping("password/forgot")
    public String forgotPassword(@RequestParam @NotBlank String email) {
        userService.forgotPassword(email);
        // S-08② 防枚举：无论邮箱是否存在，对外文案一致
        return "If the email address is valid, a reset link has been sent. Please check your inbox";
    }

    /**
     * S-07：找回密码链接的落地预检。
     * 只校验 code 有效性并跳转前端「设置新密码」页，不再直接改密（原实现把口令重置为固定值）。
     */
    @Operation(summary = "重置密码落地 | Reset Password Landing")
    @GetMapping("/password/reset")
    public void resetPasswordLanding(@RequestParam @NotBlank String code, HttpServletResponse response) {
        boolean valid = true;
        try {
            userService.verifyResetCode(code);
        } catch (BaseException e) {
            valid = false;
        }
        String target = adiProperties.getFrontendUrl()
                + (valid ? "/#/resetPassword?code=" + URLEncoder.encode(code, Charset.defaultCharset())
                         : "/#/resetPassword?invalid=1");
        try {
            response.sendRedirect(target);
        } catch (IOException e) {
            log.error("resetPasswordLanding:", e);
            throw new BaseException(B_RESET_PASSWORD_ERROR);
        }
    }

    /**
     * S-07：提交新密码。code 一次性消费，成功后全部会话被踢出，需重新登录。
     */
    @Operation(summary = "设置新密码 | Set New Password")
    @RateLimit(times = 5, perMinutes = 1)
    @PostMapping(value = "/password/reset", produces = MediaType.TEXT_PLAIN_VALUE)
    public String resetPassword(@Validated @RequestBody PasswordResetReq req) {
        userService.resetPasswordByCode(req.getCode(), req.getNewPassword());
        return "Password has been updated. Please sign in with your new password";
    }

    @Operation(summary = "登录 | Login")
    @RateLimit(times = 10, perMinutes = 1, byAccountAndIp = true)
    @PostMapping("login")
    public LoginResp login(@Validated @RequestBody LoginReq loginReq, HttpServletResponse response) {
        LoginResp loginResp = userService.login(loginReq);
        response.setHeader(AUTHORIZATION, loginResp.getToken());
        // S1-13：Cookie 补 HttpOnly/Secure/SameSite=Lax（原先裸 Cookie，可被 JS 读取、无跨站防护）。
        // Secure 由 adi.auth.cookie-secure 控制（本地 http 调试 false，prod yml 默认 true）。
        response.addHeader(SET_COOKIE, buildAuthCookie(loginResp.getToken(), adiProperties.getAuth().isCookieSecure()).toString());
        return loginResp;
    }

    /**
     * S1-13（2026-10-27）：构造登录态 Cookie。
     * <ul>
     *   <li>HttpOnly：阻断 XSS 通过 document.cookie 窃取 token；</li>
     *   <li>SameSite=Lax：阻断跨站 CSRF 携带（顶级导航 GET 仍可带，符合常规）；</li>
     *   <li>Secure：仅 HTTPS 回传（ adi.auth.cookie-secure 控制）；</li>
     *   <li>Path=/：与原 Cookie 默认作用域一致（原实现未设 Path，浏览器按当前目录，
     *       实际上反而是错的，这里显式收敛为全站）。</li>
     * </ul>
     * 包级静态便于单测断言属性。
     */
    static ResponseCookie buildAuthCookie(String token, boolean secure) {
        return ResponseCookie.from(AUTHORIZATION, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .build();
    }

    @Operation(summary = "获取登录验证码 | Get Login Captcha")
    @GetMapping("/login/captcha")
    public void captcha(@RequestParam @Length(min = 32) String captchaId, HttpServletRequest request, HttpServletResponse response) {
        HappyCaptcha happyCaptcha = HappyCaptcha.require(request, response).type(CaptchaType.WORD_NUMBER_UPPER).build().finish();
        String captchaCode = happyCaptcha.getCode();
        userService.cacheLoginCaptcha(captchaId, captchaCode);
        happyCaptcha.output();
    }

    @Operation(summary = "搜索引擎列表 | Search Engine List")
    @GetMapping(value = "/search-engine/list")
    public List<SearchEngineInfo> engines() {
        return SearchEngineServiceContext.getAllService().values().stream().map(item -> {
            SearchEngineInfo info = new SearchEngineInfo();
            info.setEnable(item.isEnabled());
            info.setName(item.getEngineName());
            return info;
        }).toList();
    }
}
