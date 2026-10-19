package com.moyz.adi.common.workflow.node.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 数据库节点配置。
 * <p>
 * Database node configuration — pick a datasource and run a read-only SQL.
 * </p>
 */
@Data
public class DatabaseNodeConfig {

    /**
     * 数据源 uuid（必填）
     * Datasource uuid (required).
     */
    @NotBlank
    @JsonProperty("datasource_uuid")
    private String datasourceUuid;

    /**
     * 只读 SQL（必填），可使用 {{input}} 引用上游输入
     * Read-only SQL (required); use {{input}} to reference upstream input.
     */
    @NotBlank
    private String sql;

    /**
     * 输出格式：text / json
     * Output format: text or json.
     */
    @JsonProperty("output_format")
    private String outputFormat = "text";

    /**
     * 返回行数上限
     * Max rows returned.
     */
    private Integer limit = 100;

    /**
     * 是否将上游输入替换到 SQL 的 {{input}} 占位符
     * Replace the {{input}} placeholder with upstream input.
     */
    @JsonProperty("replace_input")
    private Boolean replaceInput = false;
}
