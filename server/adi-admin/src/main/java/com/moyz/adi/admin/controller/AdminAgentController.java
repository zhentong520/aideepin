package com.moyz.adi.admin.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.service.AiAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 智能体管理（管理端）。
 * <p>
 * Admin agent management — this is where an administrator picks the model that the agent
 * answers with, writes the system prompt, and binds knowledge bases. End users never see any
 * of it: the chat page only shows scenes, and the scene's default agent decides the model.
 * </p>
 */
@Tag(name = "智能体管理 | Agent Management", description = "智能体管理（含模型绑定，用户端不可见） | Agent Management (incl. model binding, invisible to users)")
@RestController
@RequestMapping("/admin/agent")

@AdminOnly
public class AdminAgentController {

    @Resource
    private AiAgentService aiAgentService;

    @Operation(summary = "智能体列表（含停用） | List agents, including disabled")
    @GetMapping("/list")
    public List<Agent> list() {
        return aiAgentService.listAll();
    }

    @Operation(summary = "新增或更新智能体 | Create or update agent")
    @PostMapping("/saveOrUpdate")
    public Agent saveOrUpdate(@RequestBody Agent agent) {
        return aiAgentService.saveOrUpdateAgent(agent);
    }

    @Operation(summary = "启用/停用智能体 | Enable or disable agent")
    @PostMapping("/toggle/{uuid}")
    public Boolean toggle(@PathVariable String uuid, @RequestParam Boolean isEnable) {
        Agent agent = aiAgentService.getByUuid(uuid);
        agent.setIsEnable(isEnable);
        return aiAgentService.updateById(agent);
    }

    @Operation(summary = "删除智能体 | Delete agent")
    @PostMapping("/delete")
    public Boolean delete(@RequestParam String uuid) {
        aiAgentService.softDelete(uuid);
        return true;
    }

    @Operation(summary = "智能体绑定的工作流 | Workflows bound to the agent")
    @GetMapping("/workflows")
    public List<Workflow> workflows(@RequestParam String agentUuid) {
        return aiAgentService.listWorkflows(agentUuid);
    }

    @Operation(summary = "绑定工作流 | Bind workflows")
    @PostMapping("/bindWorkflows")
    public Boolean bindWorkflows(@RequestParam String agentUuid,
                                 @RequestParam(required = false) String defaultWorkflowUuid,
                                 @RequestBody @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> workflowUuids) {
        aiAgentService.bindWorkflows(agentUuid, workflowUuids, defaultWorkflowUuid);
        return true;
    }
}
