package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 技能市场（用户端）：浏览公开技能
 */
@Tag(name = "Skill marketplace")
@RestController
@RequestMapping("/skill")
public class SkillController {

    @Resource
    private SkillService skillService;

    @Operation(summary = "搜索技能市场 | Search skill marketplace")
    @GetMapping(value = "/public/search")
    public Page<Skill> search(@RequestParam(required = false) String keyword,
                              @NotNull @Min(1) Integer currentPage,
                              @NotNull @Min(10) Integer pageSize) {
        return skillService.search(keyword, currentPage, pageSize);
    }

    @Operation(summary = "技能详情 | Skill detail")
    @GetMapping("/public/{uuid}")
    public Skill detail(@PathVariable String uuid) {
        return skillService.getByUuid(uuid);
    }

    @Operation(summary = "技能目录文件清单 | Files inside the skill directory")
    @GetMapping("/{uuid}/files")
    public List<String> files(@PathVariable String uuid) {
        Skill skill = skillService.getByUuid(uuid);
        // 私有技能只有归属者可查看目录结构，避免暴露他人技能的文件布局
        // A private skill's layout is only visible to its owner.
        Long currentUserId = ThreadContext.getCurrentUserId();
        if (!Boolean.TRUE.equals(skill.getIsPublic())
                && (null == skill.getUserId() || !skill.getUserId().equals(currentUserId))) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return skillService.listFiles(skill);
    }
}
