package com.moyz.adi.admin.controller;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.AgentTraceSpan;
import com.moyz.adi.common.service.AgentTraceSpanService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * T6-14：管理端 Agent 全链路 trace 查询。
 * <p>时间线 = 按 traceId 取全部 span 按 start_time 升序。</p>
 */
@RestController
@RequestMapping("/admin/agent-trace")
@Validated
@AdminOnly
public class AgentTraceController {

    @Resource
    private AgentTraceSpanService agentTraceSpanService;

    @Operation(summary = "按 traceId 查时间线 | Timeline spans of one trace")
    @GetMapping("/spans")
    public List<AgentTraceSpan> spans(@RequestParam String traceId) {
        return agentTraceSpanService.listByTraceId(traceId);
    }

    @Operation(summary = "最近 trace 列表 | Recent traces, first span per trace")
    @GetMapping("/recent")
    public List<AgentTraceSpan> recent(@RequestParam(defaultValue = "20") Integer limit) {
        return agentTraceSpanService.listRecent(null == limit ? 20 : limit);
    }
}
