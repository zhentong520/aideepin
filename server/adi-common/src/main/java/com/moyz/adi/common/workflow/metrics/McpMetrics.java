package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * MCP 工具调用节点可观测指标 | MCP tool-call node observability metrics
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class McpMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 实际命中的 MCP 服务标题 | Title of the MCP server that exposed the tool
     */
    private String mcpTitle;

    /**
     * 调用的工具名 | Tool name invoked
     */
    private String toolName;

    /**
     * 返回内容长度（字符） | Length of the returned text (chars)
     */
    private Integer resultLength;
}
