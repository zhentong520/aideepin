package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 聊天/Agent 行为配置（adi.chat.*，T6-05/T6-06）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Chat} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Chat {

    /**
     * 提示注入防御开关（adi.chat.prompt-injection-defense-enabled，默认 true）。
     * false 时检索内容不做隔离包装、不做注入检测，行为与历史版本一致（铁律 10 热回滚位）。
     * <p>
     * Prompt-injection defense switch (semantic layer, T6-05). When off, retrieved
     * content is injected into prompts verbatim as before.
     */
    private boolean promptInjectionDefenseEnabled = true;

    /**
     * 长对话摘要压缩开关（adi.chat.summarize-enabled，默认 true）。
     * 历史消息估算 token 超过阈值时自动摘要压缩；⚠️ 系统提示词永远保留原样，
     * 只压缩历史轮次（identity-guard 教训：压缩绝不能吃掉系统提示词）。
     * <p>
     * Long-conversation summarize-compression switch (T6-06). The system message is
     * always kept verbatim; only history turns are compressed.
     */
    private boolean summarizeEnabled = true;

    /**
     * 摘要压缩触发阈值（adi.chat.summarize-token-threshold，默认 8000 token）。
     * 历史消息（不含系统提示词）估算 token 超过该值时触发压缩。
     */
    @Min(value = 1000, message = "adi.chat.summarize-token-threshold 必须 >= 1000")
    private int summarizeTokenThreshold = 8000;

    /**
     * 压缩时保留不压缩的最近消息条数（adi.chat.summarize-keep-recent-messages，默认 4）。
     */
    @Min(value = 2, message = "adi.chat.summarize-keep-recent-messages 至少保留 2 条")
    private int summarizeKeepRecentMessages = 4;
}
