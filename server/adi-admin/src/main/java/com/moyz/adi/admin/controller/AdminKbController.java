package com.moyz.adi.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.dto.KbEditReq;
import com.moyz.adi.common.dto.KbEmbeddingRebuildResp;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.dto.KbRecallTestReq;
import com.moyz.adi.common.dto.KbRecallTestResp;
import com.moyz.adi.common.dto.KbSearchReq;
import com.moyz.adi.common.entity.KbCategory;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.service.KbCategoryService;
import com.moyz.adi.common.service.KbPackService;
import com.moyz.adi.common.service.KbRecallTestService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Resource
    private KbPackService kbPackService;

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

    // ===== 二开新增（T7-05，2026-10-20）：按知识库全量重建 embedding 索引 =====

    @Operation(summary = "重建 embedding 索引 | Rebuild embedding index",
            description = "换 embedding provider（如切 bge-m3）后的标准动作：失效该库全部文档段行与向量（含 QA 文档），"
                    + "按当前 adi.embedding-model 重新入队，并把 provider 版本记录到知识库。"
                    + "回滚 = 把 adi.embedding-model 切回 previousVersion 对应配置并重启，再调一次本接口。"
                    + "开关 adi.kb.embedding-rebuild-enabled=false 时拒绝")
    @PostMapping("/rebuildEmbedding/{uuid}")
    public KbEmbeddingRebuildResp rebuildEmbedding(@PathVariable String uuid) {
        return knowledgeBaseService.rebuildEmbedding(uuid);
    }

    // ===== 二开新增（T7-06，2026-10-20）：知识库技能配置包导出/导入（分享能力不分享数据）=====

    @Operation(summary = "导出知识包 | Export knowledge pack",
            description = "把知识库技能配置打包为 ZIP（SKILL.md + config.json + pack.json）。"
                    + "共享的是能力不是数据：包内只有配置与引用清单，不含任何文档内容与向量；"
                    + "导入方按自身 ACL 重新校验引用。开关 adi.kb.pack-sharing-enabled=false 时拒绝")
    @GetMapping("/pack/export/{uuid}")
    public ResponseEntity<byte[]> exportPack(@PathVariable String uuid) {
        byte[] data = kbPackService.exportPack(uuid);
        String encoded = URLEncoder.encode("kb-pack.zip", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encoded + "\"; filename*=UTF-8''" + encoded)
                .contentLength(data.length)
                .body(data);
    }

    @Operation(summary = "导入知识包 | Import knowledge pack",
            description = "解析知识包 ZIP 并以当前用户为 owner 新建知识库（配置来自包内 config.json）。"
                    + "包内引用的数据按导入方自身 ACL 重新校验：任一无权即整体拒绝（A_KB_PACK_IMPORT_REF_DENIED），"
                    + "绝不静默穿透。新库不含任何数据，文档由导入方自行灌入")
    @PostMapping("/pack/import")
    public KnowledgeBase importPack(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "title", required = false) String title) {
        return kbPackService.importPack(file, title);
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
