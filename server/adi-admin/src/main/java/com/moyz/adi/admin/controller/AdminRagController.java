package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.service.RagQueryLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 二开新增（T1-01，2026-10-16）：RAG 检索日志查询入口。
 * <p>
 * ACL（T1-05）上线后若过滤失效，靠这份日志按用户/库/时间定位。
 */
@Tag(name = "RAG检索日志 | RAG Query Log", description = "RAG检索日志查询 | RAG Query Log Management")
@RestController
@RequestMapping("/admin/rag")
@Validated
@AdminOnly
public class AdminRagController {

    @Resource
    private RagQueryLogService ragQueryLogService;

    @Operation(summary = "检索日志分页 | Query log page",
            description = "按时间/用户/知识库筛选，时间格式 yyyy-MM-dd HH:mm:ss | Filter by time/user/kb")
    @GetMapping("/query-log")
    public Page<RagQueryLog> queryLog(@RequestParam(defaultValue = "") String userUuid,
                                      @RequestParam(defaultValue = "") String kbUuid,
                                      @RequestParam(defaultValue = "") String beginTime,
                                      @RequestParam(defaultValue = "") String endTime,
                                      @NotNull @Min(1) Integer currentPage,
                                      @NotNull @Min(1) Integer pageSize) {
        return ragQueryLogService.pageQuery(userUuid, kbUuid, beginTime, endTime, currentPage, pageSize);
    }
}
