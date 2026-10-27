package com.moyz.adi.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.config.props.Auth;
import com.moyz.adi.common.enums.SegmentModeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private com.moyz.adi.common.config.AdiProperties adiProperties;

    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("WebMvcConfig==configureMessageConverters");
        WebMvcConfigurer.super.configureMessageConverters(converters);
        converters.add(new StringHttpMessageConverter());
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
        converters.add(new ByteArrayHttpMessageConverter());
    }

    /**
     * Binds segmentMode query params / form fields by enum value, case-insensitively
     * (qa/text/parent_child)
     */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, SegmentModeEnum.class, source -> {
            SegmentModeEnum mode = SegmentModeEnum.fromValue(source);
            if (mode == null) {
                throw new IllegalArgumentException("Invalid segmentMode: " + source);
            }
            return mode;
        });
    }

    /**
     * S1-13（2026-10-27）：CORS 从「未声明 origins（默认放行全部）」收紧为显式白名单。
     * <ul>
     *   <li>白名单来源：{@code adi.auth.cors-allowed-origin-patterns}；未配置时回退
     *       frontendUrl + 本地开发端点（localhost/127.0.0.1 任意端口），跨域脚本来源被拒；</li>
     *   <li>用 allowedOriginPatterns（而非 allowedOrigins="*"）+ allowCredentials(true)，
     *       合法来源可携带凭据，且与显式白名单不冲突；</li>
     *   <li>expose Authorization：登录响应把 token 放在 Authorization 头，前端需可读。</li>
     * </ul>
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(resolveCorsWhitelist().toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /** CORS 白名单解析：显式配置优先，否则 frontendUrl + 开发端点兜底。包级可见便于单测。 */
    List<String> resolveCorsWhitelist() {
        Auth auth = adiProperties == null ? null : adiProperties.getAuth();
        List<String> configured = auth == null ? null : auth.getCorsAllowedOriginPatterns();
        if (!CollectionUtils.isEmpty(configured)) {
            return configured;
        }
        List<String> fallback = new ArrayList<>(Auth.DEFAULT_CORS_DEV_PATTERNS);
        String frontendUrl = adiProperties == null ? null : adiProperties.getFrontendUrl();
        if (frontendUrl != null && !frontendUrl.isBlank()) {
            fallback.add(frontendUrl);
        }
        return fallback;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//Replaced by ParamsLogAspect
        //使用ParamsLogAspect代替
//        log.info("WebMvcConfig==addInterceptors");
//        registry.addInterceptor(logInterceptor)
//                .addPathPatterns("/**")
//                .excludePathPatterns("/swagger-resources/**", "/webjars/**", "/v3/**", "/swagger-ui.html");
    }

}
