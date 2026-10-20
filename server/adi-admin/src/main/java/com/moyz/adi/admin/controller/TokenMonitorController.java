package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.dto.CostDashboardResp;
import com.moyz.adi.common.dto.LLMCallRecordDto;
import com.moyz.adi.common.dto.LLMCallRecordSearchReq;
import com.moyz.adi.common.service.LLMCallRecordService;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/token-monitor")
@Validated

@AdminOnly
public class TokenMonitorController {

    @Resource
    private LLMCallRecordService llmCallRecordService;

    @Operation(summary = "LLM 调用记录分页查询 | LLM call record paginated search")
    @PostMapping("/search")
    public Page<LLMCallRecordDto> search(@RequestBody LLMCallRecordSearchReq req,
                                         @NotNull @Min(1) Integer currentPage,
                                         @NotNull @Min(10) Integer pageSize) {
        return llmCallRecordService.search(req, currentPage, pageSize);
    }

    /**
     * T6-13 模型成本看板：按模型/渠道聚合 token 与费用。
     * <p>status 区分 OK / NO_DATA（时间范围内无记录）/ UNAVAILABLE（数据源故障）。</p>
     */
    @Operation(summary = "模型成本看板聚合 | Cost dashboard aggregation")
    @PostMapping("/cost-dashboard")
    public CostDashboardResp costDashboard(@RequestParam String beginTime,
                                           @RequestParam String endTime) {
        return llmCallRecordService.costDashboard(
                LocalDateTimeUtil.parse(beginTime), LocalDateTimeUtil.parse(endTime));
    }
}
