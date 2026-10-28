package com.moyz.adi.common.interfaces;

import dev.langchain4j.agent.tool.ToolSpecification;

import java.util.List;

/**
 * 业务工具插件契约：一个实现类 = 一个可拔插的业务场景能力包。
 * <p>
 * 实现类注册为 Spring bean 后由 {@link BizToolService} 自动发现并聚合；
 * 智能体在后台勾选插件码（plugin_ids）即可启用，无需改任何装配代码。
 * <p>
 * 工具命名约定：<b>{code}_xxx</b>（如 mall_list_categories）——前缀即路由键，
 * BizToolService 据此把 LLM 的工具调用分发给正确的 provider。
 * <p>
 * One implementation = one pluggable business capability pack. Tool names must be
 * prefixed with the plugin code (e.g. mall_list_categories) — the prefix is the routing key.
 */
public interface BizToolProvider {

    /**
     * 插件唯一码（小写），同时是工具名前缀与 plugin_ids 列里的绑定值。
     */
    String code();

    /**
     * 插件标题（后台展示用）。
     */
    String title();

    /**
     * 注入 system prompt 的能力说明：告诉 LLM 本插件提供什么能力、何时调用哪个工具。
     */
    String manifest();

    /**
     * 本插件提供的全部工具规格。
     */
    List<ToolSpecification> toolSpecifications();

    /**
     * 执行本插件名下的工具（toolName 一定以 code() 为前缀）。
     * 实现内部自行做参数解析与异常兜底，返回给 LLM 的文本。
     */
    String execute(String toolName, String argsJson, Long userId, String characterUuid);
}
