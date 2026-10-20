package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.dto.workflow.WfVersionDto;
import com.moyz.adi.common.service.WorkflowVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T9-05：工作流版本历史与回滚。
 */
@Tag(name = "Workflow Version")
@RestController
@RequestMapping("/workflow/version")
@Validated
public class WorkflowVersionController {

    @Resource
    private WorkflowVersionService workflowVersionService;

    @Operation(summary = "版本历史（versionNo 降序，diff 概要） | Version history")
    @GetMapping("/list")
    public Page<WfVersionDto> list(@RequestParam String wfUuid,
                                   @NotNull @Min(1) Integer currentPage,
                                   @NotNull @Min(1) Integer pageSize) {
        return workflowVersionService.list(wfUuid, currentPage, pageSize);
    }

    @Operation(summary = "一键回滚到指定版本（回滚本身生成新版本，不覆盖历史） | Rollback")
    @PostMapping("/rollback/{wfUuid}")
    public WfVersionDto rollback(@PathVariable String wfUuid, @RequestParam @NotNull @Min(1) Integer versionNo) {
        return workflowVersionService.rollback(wfUuid, versionNo);
    }
}
