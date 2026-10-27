package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S1-02：把 adi.model 静态工具/抽象服务的运行期配置在启动期从 {@link AdiProperties}
 * 推送过去，替代调用路径里 SpringUtil.getBean(AdiProperties) 的服务定位器写法。
 * <p>
 * 为什么不用注入：{@link AbstractLLMService} 及其子类由 {@code AiModelInitializer}
 * 手动 new（非 Spring bean），无法字段注入；调用链深处（streaming 错误处理）
 * 显式传参侵入过宽。启动期推送 + 默认值兜底与旧行为完全一致。
 * <p>
 * S1-02 startup config pusher for model-domain statics.
 */
@Slf4j
@Component
public class ModelStaticConfigInitializer {

    @Resource
    private AdiProperties adiProperties;

    @PostConstruct
    public void pushStaticConfig() {
        AbstractLLMService.applyStreamConfig(adiProperties.getLlm().getStreamSameModelRetries());
        log.info("Model static config pushed: streamSameModelRetries={}",
                adiProperties.getLlm().getStreamSameModelRetries());
    }
}
