package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.workflow.*;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.service.WorkflowComponentService;
import com.moyz.adi.common.service.WorkflowService;
import com.moyz.adi.common.service.WorkflowVariableService;
import com.moyz.adi.common.workflow.WorkflowStarter;
import com.moyz.adi.common.workflow.node.switcher.OperatorEnum;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/workflow")
@Validated
public class WorkflowController {

    @Resource
    private WorkflowStarter workflowStarter;

    @Resource
    private WorkflowService workflowService;

    @Resource
    private WorkflowComponentService workflowComponentService;

    @Resource
    private WorkflowVariableService workflowVariableService;

    @PostMapping("/add")
    public WorkflowResp add(@RequestBody @Validated WfAddReq addReq) {
        return workflowService.add(addReq.getTitle(), addReq.getRemark(), addReq.getIsPublic());
    }

    @PostMapping("/copy/{wfUuid}")
    public WorkflowResp copy(@PathVariable String wfUuid) {
        return workflowService.copy(wfUuid);
    }

    @PostMapping("/set-public/{wfUuid}")
    public void setPublic(@PathVariable String wfUuid, @RequestParam(defaultValue = "true") Boolean isPublic) {
        workflowService.setPublic(wfUuid, isPublic);
    }

    @PostMapping("/update")
    public WorkflowResp update(@RequestBody @Validated WorkflowUpdateReq req) {
        return workflowService.update(req);
    }

    @PostMapping("/del/{uuid}")
    public void delete(@PathVariable String uuid) {
        workflowService.softDelete(uuid);
    }

    @PostMapping("/enable/{uuid}")
    public void enable(@PathVariable String uuid, @RequestParam Boolean enable) {
        workflowService.enable(uuid, enable);
    }

    @PostMapping("/base-info/update")
    public WorkflowResp updateBaseInfo(@RequestBody @Validated WfBaseInfoUpdateReq req) {
        return workflowService.updateBaseInfo(req.getUuid(), req.getTitle(), req.getRemark(), req.getIsPublic());
    }

    @Operation(summary = "流式响应 | SSE Response")
    @PostMapping(value = "/run/{wfUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseAsk(@PathVariable String wfUuid, @RequestBody WorkflowRunReq runReq) {
        return workflowStarter.streaming(ThreadContext.getCurrentUser(), wfUuid, runReq.getInputs());
    }

    @Operation(summary = "T3-10 单节点试跑：从指定节点起执行（不写正式执行记录） | Single-node test run")
    @PostMapping(value = "/run-test/{wfUuid}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sseTestRun(@PathVariable String wfUuid, @RequestBody @Validated WorkflowTestRunReq req) {
        return workflowStarter.streamingTestRun(ThreadContext.getCurrentUser(), wfUuid, req);
    }

    @Operation(summary = "T3-13 指定节点的可用变量列表（含类型/示例值，字段级展开 2 层） | Available vars")
    @GetMapping("/available-vars")
    public List<WfAvailableVarDto> availableVars(@RequestParam String wfUuid,
                                                 @RequestParam @NotBlank String nodeUuid) {
        return workflowVariableService.listAvailableVars(wfUuid, nodeUuid);
    }

    @Operation(summary = "T3-13 模板表达式校验：{{var.path}} 引用写错立即提示 | Validate template")
    @PostMapping("/validate-template")
    public WfTemplateValidateResp validateTemplate(@RequestBody @Validated WfTemplateValidateReq req) {
        return workflowVariableService.validateTemplate(req.getWfUuid(), req.getNodeUuid(), req.getTemplate());
    }

    @Operation(summary = "T3-14 导出工作流 JSON | Export workflow")
    @GetMapping("/{wfUuid}/export")
    public WfExportResp export(@PathVariable String wfUuid) {
        return workflowService.export(wfUuid);
    }

    @Operation(summary = "T3-14 导入工作流 JSON（kbUuid 校验，缺失即报错） | Import workflow")
    @PostMapping("/import")
    public WorkflowResp importWorkflow(@RequestBody @Validated WfExportResp req) {
        return workflowService.importWorkflow(req);
    }

    @GetMapping("/mine/search")
    public Page<WorkflowResp> searchMine(@RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(required = false) Boolean isPublic,
                                         @NotNull @Min(1) Integer currentPage,
                                         @NotNull @Min(10) Integer pageSize) {
        return workflowService.search(keyword, isPublic, null, currentPage, pageSize);
    }

    /**
* Search public workflows
     * 搜索公开工作流
     *
* @param keyword     搜索关键词 / Search keyword
* @param currentPage 当前页数 / Current page number
* @param pageSize    每页数量 / Page size
     * @return 工作流列表
     */
    @GetMapping("/public/search")
    public Page<WorkflowResp> searchPublic(@RequestParam(defaultValue = "") String keyword,
                                           @NotNull @Min(1) Integer currentPage,
                                           @NotNull @Min(10) Integer pageSize) {
        return workflowService.searchPublic(keyword, currentPage, pageSize);
    }

    @GetMapping("/public/operators")
    public List<Map<String, String>> searchPublic() {
        List<Map<String, String>> result = new ArrayList<>();
        for (OperatorEnum operator : OperatorEnum.values()) {
            result.add(Map.of("name", operator.getName(), "desc", operator.getDesc()));
        }
        return result;
    }

    @GetMapping("/public/component/list")
    public List<WorkflowComponent> component() {
        return workflowComponentService.listAvailable();
    }
}
