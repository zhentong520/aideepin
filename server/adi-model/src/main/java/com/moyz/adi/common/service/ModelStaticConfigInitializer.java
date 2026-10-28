package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.interfaces.DelegateAgentSpi;
import com.moyz.adi.common.interfaces.BizToolSpi;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import com.moyz.adi.common.languagemodel.AbstractImageModelService;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.languagemodel.DashScopeAsrService;
import com.moyz.adi.common.languagemodel.LLMFallbackRouter;
import com.moyz.adi.common.languagemodel.data.ToolLoopBudget;
import com.moyz.adi.common.util.ImageUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * S1-02：把 adi.model 静态工具/抽象服务的运行期依赖在启动期从容器推送过去，
 * 替代调用路径里 SpringUtil.getBean 的服务定位器写法。
 * <p>
 * 为什么不用注入：{@link AbstractLLMService} 及其子类由 {@code AiModelInitializer}
 * 手动 new（非 Spring bean），无法字段注入；调用链深处（streaming 错误处理）
 * 显式传参侵入过宽。启动期推送 + 默认值兜底与旧行为完全一致。
 * <p>
 * S1-02 startup config/dependency pusher for model-domain statics.
 */
@Slf4j
@Component
public class ModelStaticConfigInitializer {

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ModelHealthService modelHealthService;

    @Resource
    private SkillToolSpi skillToolService;

    @Resource
    private DelegateAgentSpi delegateAgentTool;

    @Resource
    private BizToolSpi bizToolService;

    @Resource
    private FileService fileService;

    @Resource
    private RestTemplate restTemplate;

    @PostConstruct
    public void pushStaticConfig() {
        AbstractLLMService.applyStreamConfig(adiProperties.getLlm().getStreamSameModelRetries());
        LLMFallbackRouter.initConfig(adiProperties);
        ToolLoopBudget.initConfig(adiProperties);
        AbstractLLMService.initDependencies(stringRedisTemplate, modelHealthService, skillToolService, delegateAgentTool);
        //T9-M2（2026-10-28）：业务工具插件静态推送（拔插式业务能力，如商城导购）
        AbstractLLMService.initBizToolService(bizToolService);
        LLMContext.initHealthService(modelHealthService);
        AbstractImageModelService.initStatics(fileService);
        DashScopeAsrService.initStatics(restTemplate);
        ImageUtil.initStatics(fileService);
        log.info("Model static config pushed: streamSameModelRetries={}",
                adiProperties.getLlm().getStreamSameModelRetries());
    }
}
