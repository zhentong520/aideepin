package com.moyz.adi.chat.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.Scene;
import com.moyz.adi.common.service.SceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端场景接口：**只读**。
 * <p>
 * 场景与智能体由管理员在后台统一配置（见 {@code AdminSceneController} / {@code AdminAgentController}），
 * 用户端只消费「场景列表 → 该场景默认智能体 → 该智能体绑定的模型」这条链路，
 * 页面不出现任何模型/厂商，也不提供创建入口。
 * </p>
 * <p>
 * Scene API for end users: <b>read-only</b>. Scenes and agents are configured backstage;
 * users only consume the list.
 * </p>
 */
@Tag(name = "场景（只读） | Scene (read-only)")
@RestController
@RequestMapping("/scene")
public class SceneController {

    @Resource
    private SceneService sceneService;

    @Operation(summary = "场景列表（全部用户共用） | Scene list shared by all users")
    @GetMapping("/list")
    public List<Scene> list() {
        return sceneService.listMine();
    }

    @Operation(summary = "场景下的智能体 | Agents of the scene")
    @GetMapping("/agents")
    public List<Agent> agents(@RequestParam String sceneUuid) {
        return sceneService.listAgents(sceneUuid);
    }
}
