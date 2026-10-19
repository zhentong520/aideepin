package com.moyz.adi.chat.controller;

import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.service.AiAgentService;
import com.moyz.adi.common.service.SceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端智能体接口：**只读**。
 * <p>
 * Agent API for end users: <b>read-only</b>. Agents are created and configured in the admin
 * console; the user side never creates or edits them, and never sees the bound model.
 * </p>
 */
@Tag(name = "智能体（只读） | Agent (read-only)")
@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AiAgentService aiAgentService;

    @Resource
    private SceneService sceneService;

    @Operation(summary = "智能体列表 | Agent list")
    @GetMapping("/list")
    public List<Agent> list() {
        return aiAgentService.listMine();
    }

    @Operation(summary = "智能体绑定的工作流 | Workflows bound to the agent")
    @GetMapping("/workflows")
    public List<Workflow> workflows(@RequestParam String agentUuid) {
        return aiAgentService.listWorkflows(agentUuid);
    }

    /**
     * 取「当前用户 × 智能体」自己的会话 uuid，没有就建一个。
     * <p>
     * Resolve the calling user's own conversation for an agent, creating it on first use.
     * </p>
     * <p>
     * 之所以要这一步而不是直接用 {@code agent.characterUuid}：智能体上的会话是「模板」，
     * 全局共享会导致 ①按 uuid 单条查询命中多行抛异常 ②短期记忆按 characterUuid 串内容。
     * </p>
     */
    @Operation(summary = "解析我的会话 | Resolve my conversation for the agent")
    @GetMapping("/resolveConv")
    public String resolveConv(@RequestParam String agentUuid) {
        return sceneService.resolveUserCharacterUuid(agentUuid);
    }
}
