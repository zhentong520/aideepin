package com.moyz.adi.chat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1-13（2026-10-27）：登录 Cookie 安全属性回归。
 *
 * <p>改造前 {@code new Cookie(AUTHORIZATION, token)} 裸写入——无 HttpOnly（XSS 可
 * document.cookie 窃取 token）、无 SameSite（跨站请求自动携带）、无 Secure。
 * 改造后登录态 Cookie 必须带全三属性，Secure 由 {@code adi.auth.cookie-secure} 控制。
 */
class AuthCookieAttributeTest {

    @DisplayName("Cookie 必须带 HttpOnly / SameSite=Lax / Path=/，默认（http 调试）不带 Secure")
    @Test
    void defaultCookieHasSecurityAttributes() {
        ResponseCookie cookie = AuthController.buildAuthCookie("tok-123", false);
        String header = cookie.toString();

        assertTrue(cookie.isHttpOnly(), "HttpOnly 必须开启（防 XSS 窃取 token）");
        assertEquals("Lax", cookie.getSameSite(), "SameSite 必须为 Lax（防跨站携带）");
        assertEquals("/", cookie.getPath(), "Path 必须显式为 /");
        assertFalse(cookie.isSecure(), "cookie-secure=false 时（本地 http 调试）不带 Secure");
        assertEquals("Authorization", cookie.getName());
        assertEquals("tok-123", cookie.getValue());
        assertTrue(header.contains("SameSite=Lax"), header);
        assertTrue(header.contains("HttpOnly"), header);
    }

    @DisplayName("cookie-secure=true（生产）时 Cookie 带 Secure 属性")
    @Test
    void secureCookieWhenConfigured() {
        ResponseCookie cookie = AuthController.buildAuthCookie("tok-123", true);
        assertTrue(cookie.isSecure(), "生产环境（HTTPS）必须带 Secure，仅加密通道回传");
        String header = cookie.toString();
        assertTrue(header.contains("Secure"), header);
    }
}
