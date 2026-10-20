package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 二开新增（T6-14，2026-10-20）：Agent 全链路 trace span。
 * <p>
 * 一次对话调用 = 一个 traceId + 若干按时间排序的 span（入口/检索/LLM/工具），
 * 异步写入（见 {@code AgentTraceSpanService#saveAsync}），管理端按 traceId 出时间线。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_agent_trace_span", autoResultMap = true)
@Schema(title = "Agent trace span | Agent链路span")
public class AgentTraceSpan extends BaseEntity {

    @Schema(title = "全链路traceId | Full-chain trace id")
    @TableField("trace_id")
    private String traceId;

    @Schema(title = "span唯一id | Span unique id")
    @TableField("span_id")
    private String spanId;

    @Schema(title = "父span id，根为空 | Parent span id, empty for root")
    @TableField("parent_span_id")
    private String parentSpanId;

    @Schema(title = "span名，如 agent.invoke / rag.retrieve / llm.chat / tools.attach")
    @TableField("name")
    private String name;

    @Schema(title = "span类型：entry/retrieval/llm/tool/other")
    @TableField("span_type")
    private String spanType;

    @Schema(title = "OK 或 ERROR")
    @TableField("status")
    private String status;

    @Schema(title = "开始时间 | Start time")
    @TableField("start_time")
    private LocalDateTime startTime;

    @Schema(title = "结束时间 | End time")
    @TableField("end_time")
    private LocalDateTime endTime;

    @Schema(title = "耗时毫秒 | Duration ms")
    @TableField("duration_ms")
    private Long durationMs;

    @Schema(title = "补充信息json（token数/模型名等） | Extra detail json")
    @TableField(value = "detail", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode detail;
}
