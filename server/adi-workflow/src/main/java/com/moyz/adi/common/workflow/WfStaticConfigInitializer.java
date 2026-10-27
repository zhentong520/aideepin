package com.moyz.adi.common.workflow;

import com.moyz.adi.common.config.AdiProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 启动期把 {@link AdiProperties} 推送给 adi-workflow 内的静态工具类（S1-02），
 * 替代它们各自 SpringUtil.getBean(AdiProperties) 的服务定位器写法。
 * 静态字段未推送（纯单测/启动早期）时，各工具类按各自的缺省值兜底，行为与原来一致。
 */
@Slf4j
@Component
public class WfStaticConfigInitializer {

    @Resource
    private AdiProperties adiProperties;

    @PostConstruct
    public void pushStaticConfig() {
        WfFeatureGate.initConfig(adiProperties);
        WorkflowUtil.initConfig(adiProperties);
        log.info("Wf static config pushed to WfFeatureGate/WorkflowUtil");
    }
}
