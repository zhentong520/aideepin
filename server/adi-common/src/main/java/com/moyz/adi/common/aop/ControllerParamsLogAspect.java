package com.moyz.adi.common.aop;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 打印controller的请求参数
 * Print controller request parameters
 *
 * @author moyz
 * date:2021-07-15 03:16:59
 */
@Aspect
@Component
public class ControllerParamsLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(ControllerParamsLogAspect.class);

    /**
     * G-14：切点包名修复——原值 {@code com.adi.*.controller..} 与实际包
     * {@code com.moyz.adi.{chat,admin}.controller} 不匹配，导致整个 Controller
     * 参数日志切面自引入起从未生效过。
     * <p>
     * G-14 fix: the original pointcut package {@code com.adi.*} never matched the
     * real {@code com.moyz.adi.*} controller packages, so this aspect was dead code.
     * </p>
     */
    @Pointcut("execution(public * com.moyz.adi..controller..*.*(..))")
    public void controllerMethods() {
    }

    @Before("controllerMethods()")
    public void before(JoinPoint joinPoint) {
        ParamsLogAspect.paramsLog(joinPoint, logger);
    }


}
