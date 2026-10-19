package com.moyz.adi.common.util;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.SysConfigService;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.MessageSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Slf4j
@Component
public class SpringUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        SpringUtil.applicationContext = applicationContext;
    }

    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }

    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    public static String getProperty(String key) {
        return applicationContext.getEnvironment().getProperty(key);
    }

    public static String getMessage(String key) {
        // applicationContext 为 null 说明处在 Spring 上下文尚未就绪的启动早期（如 bean 工厂方法里
        // 抛业务异常）：此时必须回退返回 key 本身，而不是用 NPE 掩盖真实错误
        if (null == applicationContext) {
            return key;
        }
        MessageSource messageSource = applicationContext.getBean(MessageSource.class);
        Locale locale = getCurrentUserLocale();
        return messageSource.getMessage(key, null, locale);
    }

    public static String getMessage(String key, String... args) {
        MessageSource messageSource = applicationContext.getBean(MessageSource.class);
        Locale locale = getCurrentUserLocale();
        return messageSource.getMessage(key, args, locale);
    }

    public static String getMessage(String key, Locale locale, String... args) {
        MessageSource messageSource = applicationContext.getBean(MessageSource.class);
        return messageSource.getMessage(key, args, locale);
    }

    private static Locale getCurrentUserLocale() {
        try {
            User user = ThreadContext.getCurrentUser();
            if (user != null && StringUtils.isNotBlank(user.getLocale())) {
                return Locale.forLanguageTag(user.getLocale());
            }
        } catch (Exception e) {
            log.warn("Failed to get current user locale, falling back to system config", e);
        }
        String sysLocale = SysConfigService.getByKey(AdiConstant.SysConfigKey.DEFAULT_LOCALE);
        if (StringUtils.isNotBlank(sysLocale)) {
            return Locale.forLanguageTag(sysLocale);
        }
        return Locale.getDefault();
    }
}
