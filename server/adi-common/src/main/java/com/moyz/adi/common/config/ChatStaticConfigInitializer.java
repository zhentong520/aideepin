package com.moyz.adi.common.config;

import com.moyz.adi.common.util.ConversationCompressor;
import com.moyz.adi.common.util.PromptInjectionDefense;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * S1-02：把 adi.chat 下的两个纯静态工具（ConversationCompressor / PromptInjectionDefense）
 * 的运行期配置在启动期从 {@link AdiProperties} 推送过去，替代原先在每次调用路径里
 * SpringUtil.getBean(AdiProperties) 的服务定位器写法。
 * <p>
 * 为什么不改为注入式 bean：这两个工具被静态调用链深处使用
 * （CharacterChatHelper、KnowledgeRetrievalNode、AbstractLLMService 等），
 * 显式传参需要改动 10+ 个方法签名；启动期推送 + 默认值兜底保持行为完全一致
 * （无 Spring 上下文/推送前的早期调用走默认值，与旧代码 catch-后默认值语义相同）。
 * <p>
 * S1-02 startup config pusher for static chat utilities. Replaces per-call
 * SpringUtil.getBean(AdiProperties) service-locator lookups; defaults keep
 * pre-context behaviour identical.
 */
@Slf4j
@Component
public class ChatStaticConfigInitializer {

    @Resource
    private AdiProperties adiProperties;

    @PostConstruct
    public void pushStaticConfig() {
        PromptInjectionDefense.setDefenseEnabled(adiProperties.getChat().isPromptInjectionDefenseEnabled());
        ConversationCompressor.applyConfig(
                adiProperties.getChat().isSummarizeEnabled(),
                adiProperties.getChat().getSummarizeTokenThreshold(),
                adiProperties.getChat().getSummarizeKeepRecentMessages());
        log.info("Chat static config pushed: promptInjectionDefense={}, summarize(enabled={},threshold={},keepRecent={})",
                adiProperties.getChat().isPromptInjectionDefenseEnabled(),
                adiProperties.getChat().isSummarizeEnabled(),
                adiProperties.getChat().getSummarizeTokenThreshold(),
                adiProperties.getChat().getSummarizeKeepRecentMessages());
    }
}
