package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.dto.KbEditReq;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.dto.KbRecallTestReq;
import com.moyz.adi.common.dto.KbRecallTestResp;
import com.moyz.adi.common.dto.KbSearchReq;
import com.moyz.adi.common.entity.KbCategory;
import com.moyz.adi.common.service.KbCategoryService;
import com.moyz.adi.common.service.KbRecallTestService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Knowledge Base
 */
@Tag(name = "知识库管理 | Knowledge Base Management", description = "知识库管理 | Knowledge Base Management")
@RestController
@RequestMapping("/admin/kb")
@Validated

@AdminOnly
public class AdminKbController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KbRecallTestService kbRecallTestService;

    @Resource
    private KbCategoryService kbCategoryService;

    @Resource
    private com.moyz.adi.common.service.KbAclBackfillService kbAclBackfillService;

    // ===== 二开新增（2026-10-09）：知识库分类字典 CRUD =====

    @Operation(summary = "分类列表 | Category list")
    @GetMapping("/category/list")
    public List<KbCategory> categoryList() {
        return kbCategoryService.listAll();
    }

    @Operation(summary = "保存分类（新增/编辑） | Save category")
    @PostMapping("/category/save")
    public KbCategory saveCategory(@RequestBody KbCategory category) {
        return kbCategoryService.saveOrUpdate(category.getId(), category.getName(), category.getSort(), category.getRemark());
    }

    @Operation(summary = "删除分类，挂载的知识库重置为未分类 | Delete category, attached KBs reset to uncategorized")
    @PostMapping("/category/del/{id}")
    public boolean delCategory(@PathVariable Long id) {
        return kbCategoryService.softDelete(id);
    }

    @Operation(summary = "搜索知识库 | Search Knowledge Bases")
    @PostMapping("/search")
    public Page<KbInfoResp> search(@RequestBody KbSearchReq kbSearchReq, @NotNull @Min(1) Integer currentPage, @NotNull @Min(10) Integer pageSize) {
        return knowledgeBaseService.search(kbSearchReq, currentPage, pageSize);
    }

    @Operation(summary = "删除知识库 | Delete Knowledge Base")
    @PostMapping("/del/{uuid}")
    public boolean delete(@PathVariable String uuid) {
        return knowledgeBaseService.softDelete(uuid);
    }

    @Operation(summary = "编辑知识库 | Edit Knowledge Base")
    @PostMapping("/edit")
    public boolean edit(@RequestBody KbEditReq kbEditReq) {
        knowledgeBaseService.saveOrUpdate(kbEditReq);
        return true;
    }

    @Operation(summary = "重建关键词索引 | Rebuild Keyword Index")
    @PostMapping("/rebuildKeywordIndex/{uuid}")
    public int rebuildKeywordIndex(@PathVariable String uuid) {
        return knowledgeBaseService.rebuildKeywordIndex(uuid);
    }

    @Operation(summary = "知识库详情（含检索配置） | Knowledge base detail with retrieval settings")
    @GetMapping("/info/{uuid}")
    public KbInfoResp info(@PathVariable String uuid) {
        return knowledgeBaseService.info(uuid);
    }

    @Operation(summary = "召回测试 | Recall test",
            description = "按知识库配置（可临时覆盖参数）跑一遍召回，返回候选池、各阶段条数、耗时与命中明细")
    @PostMapping("/recallTest")
    public KbRecallTestResp recallTest(@Valid @RequestBody KbRecallTestReq req) {
        return kbRecallTestService.test(req);
    }

    // ===== 二开新增（T1-06，2026-10-16）：存量 chunk ACL 一键回填 =====

    @Operation(summary = "ACL 存量回填 | ACL backfill",
            description = "把库级 ACL 默认值回填到所有向量表的 chunk metadata；幂等，复跑影响 0 行；返回影响行数")
    @PostMapping("/acl/backfill")
    public long aclBackfill() {
        return kbAclBackfillService.backfill();
    }
}
