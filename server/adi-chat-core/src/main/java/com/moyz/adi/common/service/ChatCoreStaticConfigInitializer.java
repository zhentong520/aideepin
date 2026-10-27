package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import com.moyz.adi.common.util.CharacterChatHelper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S1-02：把 chat-core 域静态工具（{@link CharacterChatHelper}）的运行期依赖在启动期
 * 推送过去，替代调用路径里 SpringUtil.getBean 的服务定位器写法。
 * <p>
 * 为什么不用注入：CharacterChatHelper 是纯静态工具类，被 AgentNode /
 * CharacterMessageService 等以静态方法调用；启动期推送 + null 兜底与旧行为一致
 * （memory 用户维度无配置时关闭，即安全缺省）。
 * <p>
 * S1-02 startup config pusher for chat-core statics.
 */
@Slf4j
@Component
public class ChatCoreStaticConfigInitializer {

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private UserMcpService userMcpService;

    @Resource
    private SkillToolSpi skillToolService;

    @Resource
    private SkillService skillService;

    @PostConstruct
    public void pushStaticConfig() {
        CharacterChatHelper.initConfig(adiProperties, userMcpService, skillToolService, skillService);
        log.info("ChatCore static config pushed: memoryUserDimensionEnabled={}",
                null != adiProperties && adiProperties.getMemory().isUserDimensionEnabled());
    }
}
