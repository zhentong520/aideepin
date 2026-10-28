package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_agent")
@Schema(title = "Agent definition with work design | 智能体定义（含工作设计）")
public class Agent extends BaseEntity {

    @Schema(title = "Agent uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Agent title")
    @TableField("title")
    private String title;

    @Schema(title = "Description")
    @TableField("remark")
    private String remark;

    @Schema(title = "Avatar url")
    @TableField("avatar_url")
    private String avatarUrl;

    @Schema(title = "System prompt / role setting")
    @TableField("system_message")
    private String systemMessage;

    @Schema(title = "Work design JSON: goal, steps, constraints, output format")
    @TableField("work_design")
    private String workDesign;

    @Schema(title = "Model platform name")
    @TableField("model_platform")
    private String modelPlatform;

    @Schema(title = "Model name")
    @TableField("model_name")
    private String modelName;

    @Schema(title = "LLM temperature")
    @TableField("llm_temperature")
    private Double llmTemperature;

    @Schema(title = "Bound knowledge base id list (comma separated)")
    @TableField("kb_ids")
    private String kbIds;

    @Schema(title = "Bound MCP id list (comma separated)")
    @TableField("mcp_ids")
    private String mcpIds;

    @Schema(title = "Enabled biz-tool plugin codes (comma separated, e.g. mall)")
    @TableField("plugin_ids")
    private String pluginIds;

    @Schema(title = "Enable RAG retrieval")
    @TableField("enable_rag")
    private Boolean enableRag;

    @Schema(title = "Enable MCP tools")
    @TableField("enable_mcp")
    private Boolean enableMcp;

    @Schema(title = "Enable web search")
    @TableField("enable_web_search")
    private Boolean enableWebSearch;

    @Schema(title = "Runtime character uuid used by chat")
    @TableField("character_uuid")
    private String characterUuid;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
