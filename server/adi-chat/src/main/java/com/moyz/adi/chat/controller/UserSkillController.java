package com.moyz.adi.chat.controller;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.UserSkill;
import com.moyz.adi.common.service.UserSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 我的技能（用户端）：安装记录与启停
 */
@Tag(name = "User skills")
@RestController
@RequestMapping("/user/skill")
public class UserSkillController {

    @Resource
    private UserSkillService userSkillService;

    @Operation(summary = "我的技能列表 | My installed skills")
    @GetMapping("/list")
    public List<UserSkill> list() {
        return userSkillService.listByUserId(ThreadContext.getCurrentUserId());
    }

    @Operation(summary = "安装/更新启停 | Install or toggle enable")
    @PostMapping("/saveOrUpdate")
    public UserSkill saveOrUpdate(@RequestParam Long skillId, @RequestParam Boolean isEnable) {
        userSkillService.saveOrUpdate(ThreadContext.getCurrentUserId(), skillId, isEnable);
        return userSkillService.getByUserAndSkill(ThreadContext.getCurrentUserId(), skillId);
    }

    @Operation(summary = "卸载 | Uninstall")
    @PostMapping("/remove")
    public void remove(@RequestParam Long skillId) {
        userSkillService.remove(ThreadContext.getCurrentUserId(), skillId);
    }
}
