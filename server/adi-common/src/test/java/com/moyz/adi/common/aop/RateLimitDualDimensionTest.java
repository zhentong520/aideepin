package com.moyz.adi.common.aop;

import com.moyz.adi.common.annotation.RateLimit;
import com.moyz.adi.common.dto.LoginReq;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.helper.RateLimitHelper;
import com.moyz.adi.common.vo.RequestRateLimit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S1-13（2026-10-27）：登录限流「IP+账号」双维度回归。
 *
 * <p>改造前登录端点无任何频控（撞库/爆破解禁）；且单维度限流存在缺口：
 * 单靠 IP 可被攻击者轮换出口绕过，单靠账号可被高频打别人账号锁死。
 * 双维度 = 两把计数器各自判超限，任一命中即拒绝。
 */
class RateLimitDualDimensionTest {

    private RateLimitAspect aspect;
    private RateLimitHelper rateLimitHelper;

    @BeforeEach
    void setUp() {
        aspect = new RateLimitAspect();
        rateLimitHelper = mock(RateLimitHelper.class);
        org.springframework.test.util.ReflectionTestUtils.setField(aspect, "rateLimitHelper", rateLimitHelper);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/login");
        request.setRemoteAddr("203.0.113.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private JoinPoint joinPointWith(LoginReq req) {
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getDeclaringType()).thenReturn((Class) AuthControllerAnchor.class);
        when(signature.getName()).thenReturn("login");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(new Object[]{req});
        return joinPoint;
    }

    /** 仅为 MethodSignature mock 提供声明类型（不引用真实 AuthController，避免拉入 web 层） */
    static class AuthControllerAnchor {
    }

    /** 注解 mock：双维度限流配置（等价 @RateLimit(times=10, perMinutes=1, byAccountAndIp=true)） */
    private RateLimit rateLimit() {
        RateLimit rateLimit = mock(RateLimit.class);
        when(rateLimit.times()).thenReturn(10);
        when(rateLimit.perMinutes()).thenReturn(1);
        when(rateLimit.byAccountAndIp()).thenReturn(true);
        return rateLimit;
    }

    @DisplayName("双维度：IP 键与账号键各自计数（两把锁同时检查）")
    @Test
    void bothIpAndAccountCountersChecked() {
        when(rateLimitHelper.checkRequestTimes(anyString(), any())).thenReturn(true);

        aspect.buildKeys(joinPointWith(loginReq()), rateLimit());

        List<String> keys = aspect.buildKeys(joinPointWith(loginReq()), rateLimit());
        assertEquals(2, keys.size(), "双维度应产生两把锁：IP 键 + 账号键");
        assertTrue(keys.get(0).contains("ip203.0.113.7"), "第一把锁应为 IP 维度:" + keys.get(0));
        assertTrue(keys.get(1).contains("accuser@example.com"), "第二把锁应为账号维度:" + keys.get(1));
    }

    @DisplayName("双维度：任一计数器超限即拒绝（IP 正常但账号超限 → 抛 A_REQUEST_TOO_MUCH）")
    @Test
    void accountCounterExceededBlocksRequest() {
        when(rateLimitHelper.checkRequestTimes(anyString(), any())).thenReturn(true);
        // 第二把锁（账号）超限
        when(rateLimitHelper.checkRequestTimes(org.mockito.ArgumentMatchers.contains("acc"), any()))
                .thenReturn(false);

        assertThrows(BaseException.class,
                () -> aspect.doRateLimit(joinPointWith(loginReq()), rateLimit()),
                "账号维度超限必须拒绝请求");
    }

    @DisplayName("双维度：doRateLimit 对两把锁分别 check + increase")
    @Test
    void doRateLimitIncrementsBothKeys() {
        when(rateLimitHelper.checkRequestTimes(anyString(), any())).thenReturn(true);

        aspect.doRateLimit(joinPointWith(loginReq()), rateLimit());

        verify(rateLimitHelper, times(2)).checkRequestTimes(anyString(), any(RequestRateLimit.class));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rateLimitHelper, times(2)).increaseRequestTimes(keyCaptor.capture(), any(RequestRateLimit.class));
        List<String> keys = keyCaptor.getAllValues();
        assertTrue(keys.stream().anyMatch(k -> k.contains(":ip203.0.113.7")), keys.toString());
        assertTrue(keys.stream().anyMatch(k -> k.contains(":accuser@example.com")), keys.toString());
    }

    @DisplayName("账号键统一小写（Email 大小写不敏感，防绕过）")
    @Test
    void accountKeyIsCaseInsensitive() {
        LoginReq req = loginReq();
        req.setEmail("User@Example.COM");
        List<String> keys = aspect.buildKeys(joinPointWith(req), rateLimit());
        assertTrue(keys.get(1).contains("accuser@example.com"), keys.toString());
    }

    private static LoginReq loginReq() {
        LoginReq req = new LoginReq();
        req.setEmail("user@example.com");
        req.setPassword("secret");
        return req;
    }
}
