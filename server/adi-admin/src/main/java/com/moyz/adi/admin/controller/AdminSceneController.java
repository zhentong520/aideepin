package com.moyz.adi.admin.controller;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.Scene;
import com.moyz.adi.common.service.SceneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景管理（管理端）。
 * <p>
 * Admin scene management. Scenes are configured here and shared by every end user;
 * the user-facing chat page only lists them and cannot create or edit anything.
 * </p>
 */
@Tag(name = "场景管理 | Scene Management", description = "场景管理（全局共享，用户端只读） | Scene Management (globally shared, read-only for users)")
@RestController
@RequestMapping("/admin/scene")

@AdminOnly
public class AdminSceneController {

    @Resource
    private SceneService sceneService;

    @Operation(summary = "场景列表 | List scenes")
    @GetMapping("/list")
    public List<Scene> list() {
        return sceneService.listAll();
    }

    @Operation(summary = "新增或更新场景 | Create or update scene")
    @PostMapping("/saveOrUpdate")
    public Scene saveOrUpdate(@RequestBody Scene scene) {
        return sceneService.saveOrUpdateScene(scene);
    }

    @Operation(summary = "启用/停用场景 | Enable or disable scene")
    @PostMapping("/toggle/{uuid}")
    public Boolean toggle(@PathVariable String uuid, @RequestParam Boolean isEnable) {
        Scene scene = sceneService.getByUuid(uuid);
        scene.setIsEnable(isEnable);
        return sceneService.updateById(scene);
    }

    @Operation(summary = "删除场景 | Delete scene")
    @PostMapping("/delete")
    public Boolean delete(@RequestParam String uuid) {
        sceneService.softDelete(uuid);
        return true;
    }

    @Operation(summary = "场景下的智能体（含停用） | Agents of the scene, including disabled")
    @GetMapping("/agents")
    public List<Agent> agents(@RequestParam String sceneUuid) {
        return sceneService.listAgentsForAdmin(sceneUuid);
    }

    @Operation(summary = "绑定智能体（全量覆盖，顺序即默认优先级） | Bind agents (full replace; order = priority)")
    @PostMapping("/bindAgents")
    public Boolean bindAgents(@RequestParam String sceneUuid,
                              @RequestBody @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY) List<String> agentUuids) {
        sceneService.bindAgents(sceneUuid, agentUuids);
        return true;
    }
}
