package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.UserMcpDto;
import com.moyz.adi.common.dto.mcp.UserMcpUpdateReq;
import com.moyz.adi.common.service.UserMcpService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/mcp")
public class UserMcpController {

    @Resource
    private UserMcpService userMcpService;

    @Operation(summary = "当前登录用户启用的MCP列表 | Current User's Enabled MCP List")
    @GetMapping(value = "/list")
    public Page<UserMcpDto> listByUserId(@NotNull @Min(1) Integer currentPage, @NotNull @Min(10) Integer pageSize) {
        return userMcpService.searchByUserId(ThreadContext.getCurrentUserId(), currentPage, pageSize);
    }

    @PostMapping("/saveOrUpdate")
    public UserMcpDto saveOrUpdate(@Validated @RequestBody UserMcpUpdateReq userMcpUpdateReq) {
        return userMcpService.saveOrUpdate(userMcpUpdateReq);
    }

    @Operation(summary = "当前登录用户已启用的MCP服务 | Current User's Enabled MCP Servers")
    @GetMapping(value = "/enabled")
    public List<UserMcpDto> enabledList() {
        return userMcpService.listEnabledWithInfo(ThreadContext.getCurrentUserId());
    }

    @Operation(summary = "当前用户可用的MCP工具清单 | Tools Exposed by Current User's MCP Servers")
    @GetMapping(value = "/tools")
    public List<UserMcpService.McpToolInfo> tools(@RequestParam(required = false) List<Long> mcpIds) {
        return userMcpService.listTools(ThreadContext.getCurrentUserId(), mcpIds);
    }

}
