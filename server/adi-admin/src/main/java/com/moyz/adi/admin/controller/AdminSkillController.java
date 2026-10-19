package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.dto.SkillBatchEnableReq;
import com.moyz.adi.common.dto.SkillBatchExportReq;
import com.moyz.adi.common.dto.SkillBatchIdsReq;
import com.moyz.adi.common.dto.SkillFileRenameReq;
import com.moyz.adi.common.dto.SkillFileSaveReq;
import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 技能管理端（照 AdminMcpController）：CRUD/启停 + 标准技能包 zip 导入
 */
@Tag(name = "Skill management | 技能管理", description = "Agent Skills management")
@RestController
@RequestMapping("/admin/skill")
@Validated

@AdminOnly
public class AdminSkillController {

    @Resource
    private SkillService skillService;

    @Operation(summary = "搜索技能 | Search skills")
    @PostMapping("/search")
    public Page<Skill> search(@RequestParam(required = false) String keyword,
                              @NotNull @Min(1) Integer currentPage,
                              @NotNull @Min(10) Integer pageSize) {
        return skillService.search(keyword, currentPage, pageSize);
    }

    @Operation(summary = "技能详情 | Skill detail")
    @GetMapping("/{uuid}")
    public Skill detail(@PathVariable String uuid) {
        return skillService.getByUuid(uuid);
    }

    @Operation(summary = "手工创建技能 | Create skill manually")
    @PostMapping("/add")
    public Skill add(@NotBlank @RequestParam String name,
                     @RequestParam(required = false) String title,
                     @NotBlank @RequestParam String description,
                     @RequestParam(required = false) String version,
                     @NotBlank @RequestParam String content) {
        return skillService.saveSkill(null, name, title, description, version, content);
    }

    @Operation(summary = "编辑技能 | Edit skill")
    @PostMapping("/edit")
    public Skill edit(@NotNull @RequestParam Long id,
                      @NotBlank @RequestParam String name,
                      @RequestParam(required = false) String title,
                      @NotBlank @RequestParam String description,
                      @RequestParam(required = false) String version,
                      @NotBlank @RequestParam String content) {
        return skillService.saveSkill(id, name, title, description, version, content);
    }

    @Operation(summary = "导入标准技能包 zip（含 SKILL.md） | Import skill package zip")
    @PostMapping("/importZip")
    public Skill importZip(@RequestParam("file") MultipartFile file,
                           @RequestParam(value = "title", required = false) String title) {
        return skillService.importZip(file, title);
    }

    @Operation(summary = "技能包文件清单 | Files inside the skill package")
    @GetMapping("/{uuid}/files")
    public List<SkillService.SkillFileItem> files(@PathVariable String uuid) {
        return skillService.listFileItems(skillService.getByUuid(uuid));
    }

    @Operation(summary = "读取技能包内文件内容 | Read a file inside the skill package")
    @GetMapping("/{uuid}/file")
    public String fileContent(@PathVariable String uuid, @RequestParam String path) {
        return skillService.readFileContent(skillService.getByUuid(uuid), path);
    }

    @Operation(summary = "保存技能包内文件（在线编辑/新建） | Save a file inside the skill package")
    @PostMapping("/{uuid}/file")
    public SkillService.SkillFileItem saveFile(@PathVariable String uuid, @RequestBody SkillFileSaveReq req) {
        return skillService.writeFileContent(skillService.getByUuid(uuid), req.getPath(), req.getContent());
    }

    @Operation(summary = "重命名/移动技能包内文件 | Rename or move a file inside the skill package")
    @PostMapping("/{uuid}/file/rename")
    public SkillService.SkillFileItem renameFile(@PathVariable String uuid, @RequestBody SkillFileRenameReq req) {
        return skillService.renameFile(skillService.getByUuid(uuid), req.getPath(), req.getNewPath());
    }

    @Operation(summary = "删除技能包内文件 | Delete a file inside the skill package")
    @DeleteMapping("/{uuid}/file")
    public void deleteFile(@PathVariable String uuid, @RequestParam String path) {
        skillService.deleteFile(skillService.getByUuid(uuid), path);
    }

    @Operation(summary = "导出技能包 zip | Export skill package as zip")
    @GetMapping("/{uuid}/export")
    public ResponseEntity<byte[]> export(@PathVariable String uuid) {
        Skill skill = skillService.getByUuid(uuid);
        byte[] data = skillService.exportZip(skill);
        // 文件名只保留安全字符：技能 name 来自 SKILL.md frontmatter，不保证是纯 ASCII
        String base = skill.getName() == null ? "skill" : skill.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(base + ".zip", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentLength(data.length)
                .body(data);
    }

    @Operation(summary = "批量导出技能包 zip（每个技能一个子目录） | Batch export skills as one zip")
    @PostMapping("/exportBatch")
    public ResponseEntity<byte[]> exportBatch(@RequestBody SkillBatchExportReq req) {
        List<Skill> skills = skillService.listByUuids(req.getUuids());
        byte[] data = skillService.exportZipBatch(skills);
        String encoded = URLEncoder.encode("skills.zip", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentLength(data.length)
                .body(data);
    }

    @Operation(summary = "启停 | Enable or disable")
    @PostMapping("/enable")
    public void enable(@RequestParam Long id, @RequestParam Boolean isEnable) {
        skillService.lambdaUpdate()
                .eq(Skill::getId, id)
                .set(Skill::getIsEnable, isEnable)
                .update();
    }

    @Operation(summary = "批量启停 | Batch enable or disable")
    @PostMapping("/enableBatch")
    public void enableBatch(@RequestBody SkillBatchEnableReq req) {
        skillService.enableBatch(req.getIds(), Boolean.TRUE.equals(req.getIsEnable()));
    }

    @Operation(summary = "批量删除技能 | Batch delete skills")
    @PostMapping("/delBatch")
    public void delBatch(@RequestBody SkillBatchIdsReq req) {
        skillService.softDeleteBatch(req.getIds());
    }

    @Operation(summary = "删除技能 | Delete skill")
    @PostMapping("/del/{id}")
    public void del(@PathVariable Long id) {
        skillService.softDelete(id);
    }
}
