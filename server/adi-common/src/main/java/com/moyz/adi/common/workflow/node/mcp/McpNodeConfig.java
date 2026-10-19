package com.moyz.adi.common.workflow.node.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * MCP 工具调用节点配置。
 * <p>
 * MCP tool-call node configuration — pick a tool exposed by the current user's MCP servers
 * and invoke it once, then pass the result downstream.
 * </p>
 * <p>
 * nodeConfig 示例：
 * <pre>{@code
 * {
 *   "mcp_ids": [12, 15],                       // 可选：候选 MCP 服务；留空 = 当前用户全部已启用服务
 *   "tool_name": "maps_weather",                // 必填：要调用的工具名
 *   "arguments": "{\"city\": \"{input}\"}",     // 工具入参 JSON 模板，占位符 {name} 引用上游参数
 *   "output_format": "text"                     // text / json
 * }
 * }</pre>
 * </p>
 */
@Data
public class McpNodeConfig {

    /**
     * 候选 MCP 服务 id 列表（可选）。留空表示在当前用户全部已启用的 MCP 服务里查找该工具。
     * Candidate MCP server ids (optional). Empty means: search all MCP servers enabled
     * by the current user.
     */
    @JsonProperty("mcp_ids")
    private List<Long> mcpIds;

    /**
     * 要调用的工具名（必填），来自 MCP 服务暴露的 tools。
     * Tool name to invoke (required), as exposed by the MCP server.
     */
    @NotBlank
    @JsonProperty("tool_name")
    private String toolName;

    /**
     * 工具入参 JSON 模板，支持 {name} 占位符引用上游参数；替换值会做 JSON 转义。
     * Tool arguments as a JSON template; {name} placeholders reference upstream params and
     * are substituted with JSON-escaped values.
     */
    private String arguments = "{}";

    /**
     * 输出格式：text / json
     * Output format: text or json.
     */
    @JsonProperty("output_format")
    private String outputFormat = "text";
}
