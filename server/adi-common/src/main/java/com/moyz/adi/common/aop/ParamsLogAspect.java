package com.moyz.adi.common.aop;

import com.moyz.adi.common.annotation.ParamsLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * @author myz
 */
@Slf4j
@Aspect
@Component
public class ParamsLogAspect {

    /**
     * S1-14：按字段名脱敏的敏感字段集合。对象参数序列化时跳过这些字段，
     * 不再依赖"参数名恰好叫 password"才遮蔽——如 LoginReq 的 password/email/captchaCode
     * 是对象字段，原实现会把明文整体打进日志。
     * <p>
     * Sensitive field names masked during object serialization. Object args skip these
     * fields instead of relying on the parameter being literally named "password"
     * (e.g. LoginReq carries password/email as object fields, previously logged in plaintext).
     * </p>
     */
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password", "pwd", "passwd", "secret", "token", "email", "captchaid", "captchacode");

    @Before(value = "@annotation(paramsLog)")
    public void before(JoinPoint joinPoint, ParamsLog paramsLog) {
        paramsLog(joinPoint, log);
    }

    /**
     * 输出方法参数到日志
     * Output method parameters to log
     *
     * @param joinPoint joinPoint
     * @param logger    日志 / Logger
     */
    static void paramsLog(JoinPoint joinPoint, Logger logger) {
        String className = joinPoint.getSignature().getDeclaringType().getName();
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object[] args = joinPoint.getArgs();
        Parameter[] parameters = method.getParameters();
        StringBuilder sb = new StringBuilder();
        sb.append(className);
        sb.append(".");
        sb.append(method.getName());
        sb.append(" params:[");
        for (int i = 0; i < args.length; i++) {
            String paramName = parameters[i].getName();
            sb.append(paramName);
            sb.append("=>");
            sb.append(maskArg(paramName, args[i]));
            sb.append(";");
        }
        sb.append("]");
        String log = sb.toString();
        logger.info(StringUtils.substring(log, 0, 1000));
    }

    /**
     * 单个参数脱敏：
     * 1) 参数名恰为 password（String 型直接传参的旧路径）→ 整体打码；
     * 2) 简单类型（String/数字/枚举/时间等）→ 原样输出；
     * 3) 其他对象 → 反射序列化但跳过敏感字段（{@link #SENSITIVE_FIELD_NAMES}）。
     */
    static String maskArg(String paramName, Object value) {
        if ("password".equals(paramName)) {
            return "***";
        }
        if (null == value) {
            return "null";
        }
        if (isSimpleType(value.getClass())) {
            return String.valueOf(value);
        }
        //S1-14: object args are serialized field-by-field with sensitive fields excluded,
        //so a DTO like LoginReq no longer leaks plaintext password/email into logs
        ReflectionToStringBuilder builder = new ReflectionToStringBuilder(value, ToStringStyle.SHORT_PREFIX_STYLE) {
            @Override
            protected boolean accept(Field field) {
                return super.accept(field) && !SENSITIVE_FIELD_NAMES.contains(field.getName().toLowerCase());
            }
        };
        return builder.toString();
    }

    private static boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz.isEnum()
                || CharSequence.class.isAssignableFrom(clazz)
                || Number.class.isAssignableFrom(clazz)
                || Boolean.class == clazz
                || Character.class == clazz
                || Date.class.isAssignableFrom(clazz)
                || Temporal.class.isAssignableFrom(clazz)
                || UUID.class == clazz
                || clazz.isArray()
                || Collection.class.isAssignableFrom(clazz)
                || Map.class.isAssignableFrom(clazz);
    }
}
