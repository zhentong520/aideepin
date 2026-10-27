package com.moyz.adi.common.config.props;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 鉴权相关配置（adi.auth.*）。
 * <p>
 * S1-06（2026-10-27）：TokenFilter 匿名放行白名单由硬编码常量改为可配置注入。
 * 默认值 = 原 {@code TokenFilter.EXCLUDE_API}，未配置时行为与改造前完全一致；
 * 部署方可按需增删前缀（如私有化部署暴露额外公开端点），无需改代码重新编译。
 * <p>
 * 注意：条目是「前缀匹配」且会拼上 context-path，配置时保持与原常量相同的写法
 * （路径段带尾部 '/'，根资源写完整路径）。
 */
@Data
public class Auth {

    /**
     * TokenFilter 匿名放行路径前缀白名单 | Anonymous path-prefix whitelist for TokenFilter.
     */
    private List<String> excludeUrls = new ArrayList<>(DEFAULT_EXCLUDE_URLS);

    public static final List<String> DEFAULT_EXCLUDE_URLS = List.of(
            "/auth/",
            "/model/",
            "/user/avatar/",
            "/draw/public/",
            "/draw/detail/",
            "/draw/comment/list",
            "/knowledge-base/public/",
            "/workflow/public",
            // 二开（T9-01，2026-10-20）：工作流 webhook 匿名触发端点，鉴权由
            // WorkflowWebhookController 做（token 精确匹配 + 匿名限流，无效 403/超限 429）
            "/workflow/webhook/",
            "/mcp/public",
            "/sys/config/public/",
            "/ext/v1/",
            // 二开（T1-10，2026-10-19）：KB 的 MCP Server 端点，由 ExtApiAuthFilter 用
            // knowledge 资源级 ext key 鉴权（ExtApiAuthFilter 先于本过滤器执行并设置主体）
            "/mcp/kb/",
            // OPS-01：容器探活端点（application.yml 已限制只暴露 health 一个 actuator 端点）
            "/actuator/health"
    );
}
