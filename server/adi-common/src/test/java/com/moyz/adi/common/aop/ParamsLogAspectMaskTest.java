package com.moyz.adi.common.aop;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S1-14：ParamsLogAspect 按对象字段脱敏回归。
 * 原实现只遮蔽参数名恰为 "password" 的参数，LoginReq 这类把 password/email
 * 作为对象字段的 DTO 会被整体序列化打进日志（明文密码泄漏）。
 */
class ParamsLogAspectMaskTest {

    private static final String SECRET_PASSWORD = "Sup3rSecret!2026";
    private static final String SECRET_EMAIL = "alice@example.com";
    private static final String SECRET_CAPTCHA = "captcha-answer-42";

    /** 模拟 LoginReq：敏感信息是对象字段而非方法参数名 */
    public static class FakeLoginReq {
        private final String email = SECRET_EMAIL;
        private final String password = SECRET_PASSWORD;
        private final String captchaCode = SECRET_CAPTCHA;
        private final String rememberMe = "true";
    }

    static class Anchor {
        public void anchorLogin(FakeLoginReq req, String password, int page) {
        }

        public void anchorSimple(String keyword, int page) {
        }
    }

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ParamsLogAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    private JoinPoint joinPointOf(String methodName, Object[] args) throws Exception {
        MethodSignature signature = mock(MethodSignature.class);
        Method method;
        if ("anchorLogin".equals(methodName)) {
            method = Anchor.class.getMethod("anchorLogin", FakeLoginReq.class, String.class, int.class);
        } else {
            method = Anchor.class.getMethod("anchorSimple", String.class, int.class);
        }
        when(signature.getMethod()).thenReturn(method);
        when(signature.getDeclaringType()).thenReturn((Class) Anchor.class);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    @Test
    void objectArgsSensitiveFieldsAreMasked() throws Exception {
        JoinPoint joinPoint = joinPointOf("anchorLogin", new Object[]{new FakeLoginReq(), SECRET_PASSWORD, 3});

        ParamsLogAspect.paramsLog(joinPoint, logger);

        assertFalse(appender.list.isEmpty(), "params log should be emitted");
        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("anchorLogin"));
        //参数名恰为 password 的旧路径仍然整体打码
        assertTrue(line.contains("password=>***"));
        //对象字段里的敏感值一律不得出现在日志
        assertFalse(line.contains(SECRET_PASSWORD), "password object field leaked");
        assertFalse(line.contains(SECRET_EMAIL), "email object field leaked");
        assertFalse(line.contains(SECRET_CAPTCHA), "captchaCode object field leaked");
        //非敏感字段正常保留，便于排查
        assertTrue(line.contains("rememberMe=true"));
        assertTrue(line.contains("page=>3"));
    }

    @Test
    void simpleArgsAreKeptIntact() throws Exception {
        JoinPoint joinPoint = joinPointOf("anchorSimple", new Object[]{"hello", 7});

        ParamsLogAspect.paramsLog(joinPoint, logger);

        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("keyword=>hello"));
        assertTrue(line.contains("page=>7"));
    }

    @Test
    void nullArgIsPrintedAsNull() throws Exception {
        JoinPoint joinPoint = joinPointOf("anchorLogin", new Object[]{null, SECRET_PASSWORD, 1});

        ParamsLogAspect.paramsLog(joinPoint, logger);

        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("req=>null"));
        assertTrue(line.contains("password=>***"));
    }

    @Test
    void directMaskArgHandlesSimpleAndNull() {
        assertTrue(ParamsLogAspect.maskArg("password", "whatever").equals("***"));
        assertTrue(ParamsLogAspect.maskArg("other", null).equals("null"));
        assertTrue(ParamsLogAspect.maskArg("page", 5).equals("5"));
        assertFalse(ParamsLogAspect.maskArg("req", new FakeLoginReq()).contains(SECRET_EMAIL));
    }
}
