package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.util.SpringUtil;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Locale;

/**
 * T7-02/T7-04 多 Agent 单测公共脚手架。
 * <p>
 * {@link com.moyz.adi.common.exception.BaseException} 构造时会经 {@link SpringUtil} 向 Spring 的
 * MessageSource 取文案，纯逻辑单测（不启容器）必须先装一个消息源桩，否则异常构造阶段就 NPE，
 * 测不到真实行为。桩文案保留 <code>{0}/{1}/{2}</code> 占位符（与真实文案包一致），
 * 这样 MessageFormat 才会把参数值填进 message，测试才能断言
 * 「错误信息带上了目标 Agent / 调用链 / 最大深度」。
 * </p>
 * 参考：WfNodeFactoryRegistrationTest 的同款装桩法。
 */
final class MultiAgentTestSupport {

    private MultiAgentTestSupport() {
    }

    static void installMessageSourceStub() throws Exception {
        MessageSource messageSource = new MessageSource() {
            @Override
            public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
                return withPlaceholders(code);
            }

            @Override
            public String getMessage(String code, Object[] args, Locale locale) {
                return withPlaceholders(code);
            }

            @Override
            public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
                return String.join(",", resolvable.getCodes() == null ? new String[0] : resolvable.getCodes());
            }

            private String withPlaceholders(String code) {
                // 三个占位符足够覆盖 A0080/A0081/A0082 的参数个数；多余参数被 MessageFormat 忽略，不会报错
                return code + "|{0}|{1}|{2}";
            }
        };
        ApplicationContext context = (ApplicationContext) Proxy.newProxyInstance(
                MultiAgentTestSupport.class.getClassLoader(),
                new Class<?>[]{ApplicationContext.class},
                (proxy, method, methodArgs) -> {
                    if ("getBean".equals(method.getName()) && methodArgs != null && methodArgs.length > 0) {
                        Object last = methodArgs[methodArgs.length - 1];
                        if (last instanceof Class<?> type && MessageSource.class.isAssignableFrom(type)) {
                            return messageSource;
                        }
                    }
                    return null;
                });
        Field field = SpringUtil.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }
}
