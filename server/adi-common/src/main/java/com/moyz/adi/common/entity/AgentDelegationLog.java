package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二开新增（T7-02，2026-10-20）：Agent 委派记录（delegation log）。
 * <p>
 * 每次 delegate_agent 委派落一行：委派契约四要素（objective / output_format /
 * context_summary / must_not_do）+ effort 档位 + 调用链快照（chain_depth/chain_path，
 * 深度限制与防环审计的依据）+ 执行结果。只增不更新，保证委派行为可追溯。
 * <p>
 * Delegation log: one row per delegate_agent call, persisting the four contract fields,
 * the effort tier and the call-chain snapshot for depth/cycle auditing.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_agent_delegation_log", autoResultMap = true)
@Schema(title = "Agent 委派记录 | Agent delegation log")
public class AgentDelegationLog extends BaseEntity {

    @Schema(title = "记录uuid | Record uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "全链路traceId | Full-chain trace id")
    @TableField("trace_id")
    private String traceId;

    @Schema(title = "发起委派的Agent uuid，空=用户直接发起 | Delegating agent uuid, empty when user-initiated")
    @TableField("source_agent_id")
    private String sourceAgentId;

    @Schema(title = "发起委派的Agent 名称 | Delegating agent name")
    @TableField("source_agent_name")
    private String sourceAgentName;

    @Schema(title = "目标Agent uuid | Target agent uuid")
    @TableField("target_agent_id")
    private String targetAgentId;

    @Schema(title = "目标Agent 名称 | Target agent name")
    @TableField("target_agent_name")
    private String targetAgentName;

    @Schema(title = "委派目标 | Delegation objective")
    @TableField("objective")
    private String objective;

    @Schema(title = "期望产出格式 | Expected output format")
    @TableField("output_format")
    private String outputFormat;

    @Schema(title = "背景摘要 | Context summary")
    @TableField("context_summary")
    private String contextSummary;

    @Schema(title = "禁止事项 | Explicit prohibitions")
    @TableField("must_not_do")
    private String mustNotDo;

    @Schema(title = "投入档位 LOW/MEDIUM/HIGH | Effort tier")
    @TableField("effort_level")
    private String effortLevel;

    @Schema(title = "调用链深度 | Call chain depth")
    @TableField("chain_depth")
    private Integer chainDepth;

    @Schema(title = "调用链快照 | Call chain snapshot")
    @TableField("chain_path")
    private String chainPath;

    @Schema(title = "执行结果 SUCCESS/FAIL/REJECTED | Result status")
    @TableField("status")
    private String status;

    @Schema(title = "错误信息 | Error message")
    @TableField("error_message")
    private String errorMessage;

    @Schema(title = "耗时毫秒 | Duration ms")
    @TableField("duration_ms")
    private Long durationMs;

    @Schema(title = "输入token | Input tokens")
    @TableField("input_tokens")
    private Integer inputTokens;

    @Schema(title = "输出token | Output tokens")
    @TableField("output_tokens")
    private Integer outputTokens;

    @Schema(title = "发起用户id | Delegating user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "补充信息json（难度信号/预算参数等） | Extra detail json")
    @TableField(value = "detail", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode detail;
}
