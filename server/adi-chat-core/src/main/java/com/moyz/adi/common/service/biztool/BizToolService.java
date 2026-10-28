package com.moyz.adi.common.service.biztool;

import com.moyz.adi.common.interfaces.BizToolProvider;
import com.moyz.adi.common.interfaces.BizToolSpi;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 业务工具聚合路由（SPI 实现，对应 SkillToolService 的同构位置）。
 * <p>
 * 启动期收集容器内全部 {@link BizToolProvider}（拔插点：新增业务场景只需注册一个 provider bean）；
 * prepare 按 Character/Agent 的 plugin_ids 过滤生成 manifest + 工具规格；
 * execute 按工具名前缀 {code}_ 路由到对应 provider。
 * <p>
 * Aggregates all Spring-registered BizToolProvider beans. Routing key = tool name prefix.
 */
@Slf4j
@Service
public class BizToolService implements BizToolSpi {

    // @Autowired 注入集合：Spring 把所有 BizToolProvider bean 聚合成 List（拔插点）；
    // @Resource 对集合类型按名称解析会失败，这里必须用 @Autowired
    @Autowired(required = false)
    private List<BizToolProvider> providers = List.of();

    private Map<String, BizToolProvider> providerMap() {
        Map<String, BizToolProvider> map = new LinkedHashMap<>();
        for (BizToolProvider p : providers) {
            map.put(p.code(), p);
        }
        return map;
    }

    @Override
    public ToolBundle prepare(List<String> pluginCodes) {
        if (null == pluginCodes || pluginCodes.isEmpty()) {
            return new ToolBundle("", List.of());
        }
        Map<String, BizToolProvider> map = providerMap();
        StringBuilder manifest = new StringBuilder();
        List<ToolSpecification> specs = new ArrayList<>();
        for (String code : pluginCodes) {
            BizToolProvider provider = map.get(code);
            if (null == provider) {
                // plugin_ids 里配置了不存在的插件码：跳过而不是打断对话（后台配置错误不应 500）
                log.warn("biz tool plugin not found in spring context, code:{}", code);
                continue;
            }
            if (StringUtils.isNotBlank(provider.manifest())) {
                manifest.append(provider.manifest()).append("\n\n");
            }
            specs.addAll(provider.toolSpecifications());
        }
        return new ToolBundle(manifest.toString(), specs);
    }

    @Override
    public String execute(String toolName, String argsJson, Long userId, String characterUuid) {
        // 路由键 = 第一个 '_' 之前的部分；与工具命名约定 {code}_xxx 严格对应
        int idx = toolName.indexOf('_');
        if (idx <= 0) {
            return "ERROR: unknown tool: " + toolName;
        }
        BizToolProvider provider = providerMap().get(toolName.substring(0, idx));
        if (null == provider) {
            return "ERROR: unknown plugin for tool: " + toolName;
        }
        return provider.execute(toolName, argsJson, userId, characterUuid);
    }
}
