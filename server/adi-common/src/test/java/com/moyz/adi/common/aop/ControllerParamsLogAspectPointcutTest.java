package com.moyz.adi.common.aop;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import com.moyz.adi.test.controller.DemoLoginController;
import com.moyz.adi.test.notcontroller.DemoPlainService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * G-14：ControllerParamsLogAspect 切点包名修复回归。
 * 原切点 {@code com.adi.*.controller..} 与实际包 {@code com.moyz.adi.*} 不匹配，
 * 整个切面自引入起从未生效；本测试用真实 AOP 代理验证修复后的拦截与脱敏。
 */
class ControllerParamsLogAspectPointcutTest {

    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(ControllerParamsLogAspect.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void controllerInMoyzAdiPackageIsInterceptedAndMasked() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new DemoLoginController());
        factory.addAspect(new ControllerParamsLogAspect());
        DemoLoginController proxy = factory.getProxy();

        DemoLoginController.DemoLoginReq req = new DemoLoginController.DemoLoginReq();
        proxy.login(req);

        assertFalse(appender.list.isEmpty(),
                "pointcut must match com.moyz.adi..controller.. (G-14: it never did before the fix)");
        String line = appender.list.get(0).getFormattedMessage();
        assertTrue(line.contains("DemoLoginController.login"));
        assertFalse(line.contains("PlaintextPass!42"), "password field must be masked (S1-14)");
        assertFalse(line.contains("alice@example.com"), "email field must be masked (S1-14)");
    }

    @Test
    void nonControllerPackageIsNotIntercepted() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new DemoPlainService());
        factory.addAspect(new ControllerParamsLogAspect());
        DemoPlainService proxy = factory.getProxy();

        proxy.ping("anything");

        assertTrue(appender.list.isEmpty(),
                "classes outside com.moyz.adi..controller.. must not be intercepted");
    }
}
