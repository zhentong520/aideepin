package com.moyz.adi.chat.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.dto.workflow.WfTriggerDto;
import com.moyz.adi.common.dto.workflow.WfTriggerSaveReq;
import com.moyz.adi.common.dto.workflow.WorkflowRunReq;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.service.WorkflowService;
import com.moyz.adi.common.service.WorkflowTriggerService;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.PrivilegeUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * T9-01：工作流触发配置管理 + 内部 API 触发（带权限，走 TokenFilter 登录态）。
 */
@Tag(name = "Workflow Trigger")
@RestController
@RequestMapping("/workflow/trigger")
@Validated
public class WorkflowTriggerController {

    @Resource
    private WorkflowTriggerService workflowTriggerService;

    @Resource
    private WorkflowService workflowService;

    @Operation(summary = "工作流触发配置列表（归属校验） | List triggers")
    @GetMapping("/list")
    public List<WfTriggerDto> list(@RequestParam String wfUuid) {
        return workflowTriggerService.listByWorkflowUuid(wfUuid);
    }

    @Operation(summary = "保存触发配置（新建/更新；schedule 校验 cron） | Save trigger")
    @PostMapping("/save")
    public WfTriggerDto save(@RequestBody @Validated WfTriggerSaveReq req) {
        return workflowTriggerService.save(req);
    }

    @Operation(summary = "启用/停用触发器 | Enable/disable trigger")
    @PostMapping("/enable/{uuid}")
    public void enable(@PathVariable String uuid, @RequestParam Boolean enable) {
        workflowTriggerService.enable(uuid, enable);
    }

    @Operation(summary = "删除触发器（软删） | Delete trigger")
    @PostMapping("/del/{uuid}")
    public void delete(@PathVariable String uuid) {
        workflowTriggerService.delete(uuid);
    }

    /**
     * T9-01：内部 API 触发——带登录态与归属权限，按工作流 uuid + 参数异步触发运行。
     * 触发记录（来源 api/参数/结果）进运行历史。
     */
    @Operation(summary = "内部 API 触发运行（带权限，异步） | Internal API trigger")
    @PostMapping("/run/{wfUuid}")
    public boolean apiRun(@PathVariable String wfUuid, @RequestBody(required = false) WorkflowRunReq runReq) {
        Workflow workflow = PrivilegeUtil.checkAndGetByUuid(wfUuid, workflowService.query(), ErrorEnum.A_WF_NOT_FOUND);
        ArrayNode inputs = null == runReq || null == runReq.getInputs() ? null : toArrayNode(runReq.getInputs());
        return workflowTriggerService.dispatchApiTrigger(workflow, inputs);
    }

    private ArrayNode toArrayNode(List<ObjectNode> inputs) {
        ArrayNode array = JsonUtil.createArrayNode();
        array.addAll(inputs);
        return array;
    }
}
