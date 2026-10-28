package com.moyz.adi.common.interfaces;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 业务工具插件 SPI（拔插式业务能力扩展点，2026-10-28）。
 * <p>
 * 设计目标：商城导购这类「结构化业务能力」（商品/优惠券/活动）以 function-calling 工具的
 * 形式挂进对话，而不是把业务逻辑硬编码进提示词。新增业务场景 = 实现一个
 * {@link BizToolProvider} 注册为 Spring bean，零改动对话装配层。
 * <p>
 * 与 {@link SkillToolSpi} 同构：接口在 adi-common（模型域只面向接口），
 * 实现在业务层（adi-chat-core），经 ModelStaticConfigInitializer 启动期推送为静态引用。
 * <p>
 * Biz-tool plugin SPI: pluggable business capabilities (products, coupons, activities)
 * exposed to the LLM as function-calling tools. Adding a new business scenario means
 * implementing one Spring bean — the chat assembly layer never changes.
 */
public interface BizToolSpi {

    /**
     * 工具包：注入 prompt 的插件能力清单文本 + 全部工具规格。
     */
    record ToolBundle(String manifest, List<ToolSpecification> toolSpecifications) {
    }

    /**
     * 为一组已启用的插件码构造工具包（manifest + toolSpecifications）。
     * pluginCodes 来自 Character/Agent 的 plugin_ids 列（逗号分隔，如 "mall"）。
     */
    ToolBundle prepare(List<String> pluginCodes);

    /**
     * 按工具名执行。路由规则：工具名以 "{pluginCode}_" 为前缀时路由到对应 provider。
     * userId/characterUuid 由装配层显式传入（工具在 LLM 回调线程执行，ThreadContext 不保证可达）。
     */
    String execute(String toolName, String argsJson, Long userId, String characterUuid);
}
