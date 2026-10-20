package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.KbEditReq;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.dto.KbDocumentIndexBatchReq;
import com.moyz.adi.common.dto.KbSearchReq;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.entity.KbCategory;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.KnowledgeBaseAuditLog;
import com.moyz.adi.common.entity.KnowledgeBaseShare;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.service.KbCategoryService;
import com.moyz.adi.common.service.KbPackService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/knowledge-base")
@Validated
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KbCategoryService kbCategoryService;

    @Resource
    private KbPackService kbPackService;

    /**
     * 二开新增（2026-10-09）：知识库分类字典（只读），列表/选择器分组筛选用
     */
    @GetMapping("/category/list")
    public List<KbCategory> categoryList() {
        return kbCategoryService.listAll();
    }

    @PostMapping("/saveOrUpdate")
    public KnowledgeBase saveOrUpdate(@RequestBody @Valid KbEditReq kbEditReq) {
        return knowledgeBaseService.saveOrUpdate(kbEditReq);
    }

    @PostMapping(path = "/uploadDocs/{uuid}", headers = "content-type=multipart/form-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public boolean uploadDocs(@PathVariable String uuid,
                              @RequestParam(value = "indexAfterUpload", defaultValue = "true") Boolean indexAfterUpload,
                              @RequestParam(defaultValue = "") String indexTypes,
                              @RequestParam("files") MultipartFile[] docs) {
        List<String> indexTypeList = Arrays.stream(indexTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        knowledgeBaseService.uploadDocs(uuid, indexAfterUpload, docs, indexTypeList);
        return true;
    }

    /**
* Upload, parse and index documents
     * 上传、解析并索引文档
*
* @param uuid             知识库uuid / Knowledge base UUID
* @param indexAfterUpload 是否上传完接着索引文档 / Whether to index documents after upload
* @param doc              二进制文件 / Binary file
* @param segmentMode      segment mode (text/qa/parent_child); qa parses the file as Q&A pair data and vectorizes immediately
* @param childMaxChunkSize child chunk max token size, used by parent-child mode only
* @return the uploaded file info
     */
    @PostMapping(path = "/upload/{uuid}", headers = "content-type=multipart/form-data", produces = MediaType.APPLICATION_JSON_VALUE)
    public AdiFile upload(@PathVariable String uuid,
                          @RequestParam(value = "indexAfterUpload", defaultValue = "true") Boolean indexAfterUpload,
                          @RequestParam(defaultValue = "") String indexTypes,
                          @RequestParam(value = "segmentMode", required = false) SegmentModeEnum segmentMode,
                          @RequestParam(value = "childMaxChunkSize", required = false) Integer childMaxChunkSize,
                          @RequestParam("file") MultipartFile doc) {
        List<String> indexTypeList = Arrays.stream(indexTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        return knowledgeBaseService.uploadDoc(uuid, indexAfterUpload, doc, indexTypeList, segmentMode, childMaxChunkSize);
    }

    /**
* Search my knowledge bases
     * 搜索我的知识库
     *
* @param keyword             搜索关键词 / Search keyword
* @param includeOthersPublic 是否包含其他人公开的知识库 / Whether to include other users' public knowledge bases
* @param currentPage         当前页数 / Current page number
* @param pageSize            每页数量 / Page size
     * @return 我的知识库列表
     */
    @GetMapping("/mine/search")
    public Page<KbInfoResp> searchMine(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "false") Boolean includeOthersPublic,
                                       @RequestParam(required = false) Long categoryId,
                                       @NotNull @Min(1) Integer currentPage,
                                       @NotNull @Min(1) @Max(100) Integer pageSize) {
        // 二开新增（2026-10-09）：categoryId 可选过滤，null 表示不过滤，0 = 未分类
        return knowledgeBaseService.searchMine(keyword, includeOthersPublic, categoryId, currentPage, pageSize);
    }

    /**
* Search public knowledge bases
     * 搜索公开的知识库
     *
* @param keyword     搜索关键词 / Search keyword
* @param currentPage 当前页数 / Current page number
* @param pageSize    每页数量 / Page size
     * @return 知识库列表
     */
    @GetMapping("/public/search")
    public Page<KbInfoResp> searchPublic(@RequestParam(defaultValue = "") String keyword,
                                         @RequestParam(required = false) Long categoryId,
                                         @NotNull @Min(1) Integer currentPage,
                                         @NotNull @Min(1) @Max(100) Integer pageSize) {
        // 二开新增（2026-10-09）：categoryId 可选过滤，null 表示不过滤，0 = 未分类
        return knowledgeBaseService.search(KbSearchReq.builder().isPublic(true).title(keyword).categoryId(categoryId).build(), currentPage, pageSize);
    }

    /**
     * Knowledge base details
     * 知识库详情
     *
     * @param uuid 知识库uuid / Knowledge base UUID
     * @return 知识库详情
     */
    @GetMapping("/info/{uuid}")
    public KnowledgeBase info(@PathVariable String uuid) {
        // read privilege like every other KB-scoped endpoint: without it any authenticated
        // user can read a private KB's full entity (owner, prompts, retrieval config)
        knowledgeBaseService.checkReadPrivilege(uuid);
        return knowledgeBaseService.lambdaQuery()
                .eq(KnowledgeBase::getUuid, uuid)
                .eq(KnowledgeBase::getIsDeleted, false)
                .one();
    }

    /**
     * 二开新增（T7-05，2026-10-20）：按知识库全量重建 embedding 索引（库 owner 或管理员）。
     * <p>
     * 换 embedding provider（如 bge-small-zh-v1.5 → bge-m3）后的标准动作：失效该库全部
     * 文档段行与向量（含 QA 文档）后按当前 provider 重新入队，并把 provider 版本记录到
     * 知识库。回滚 = 把 adi.embedding-model 切回 previousVersion 对应配置并重启，再调一次。
     */
    @PostMapping("/rebuildEmbedding/{uuid}")
    public com.moyz.adi.common.dto.KbEmbeddingRebuildResp rebuildEmbedding(@PathVariable String uuid) {
        return knowledgeBaseService.rebuildEmbedding(uuid);
    }

    /**
     * 二开新增（T7-06，2026-10-20）：导出知识库技能配置包（分享能力不分享数据）。
     * <p>
     * ZIP 内只有 SKILL.md + config.json + pack.json：配置与引用清单，不含任何文档内容与向量。
     * 读权限即可导出（能读库的人导出配置不构成越权）；开关 adi.kb.pack-sharing-enabled=false 时拒绝。
     */
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

    /**
     * 二开新增（T7-06，2026-10-20）：导入知识库技能配置包，以当前用户为 owner 新建知识库。
     * <p>
     * 共享的是能力不是数据：新库只有配置，文档由导入方自行灌入；包内引用的数据按导入方
     * 自身 ACL 重新校验，任一无权即整体拒绝（A_KB_PACK_IMPORT_REF_DENIED），绝不静默穿透。
     */
    @PostMapping("/pack/import")
    public KnowledgeBase importPack(@RequestParam("file") MultipartFile file,
                                    @RequestParam(value = "title", required = false) String title) {
        return kbPackService.importPack(file, title);
    }

    /**
     * 删除知识库
     *
     * @param uuid 知识库uuid / Knowledge base UUID
     * @return 成功或失败
     */
    @PostMapping("/del/{uuid}")
    public boolean softDelete(@PathVariable String uuid) {
        return knowledgeBaseService.softDelete(uuid);
    }

    /**
* Index entire knowledge base
     * 索引整个知识库
     *
* @param uuid 知识库uuid / Knowledge base UUID
     * @return 成功或失败
     */
    @PostMapping("/indexing/{uuid}")
    public boolean indexing(@PathVariable String uuid, @RequestParam(defaultValue = "") String indexTypes) {
        List<String> indexTypeList = Arrays.stream(indexTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
        return knowledgeBaseService.indexing(uuid, indexTypeList);
    }

    /**
* Batch index knowledge points
     * 批量索引知识点
     *
* @param req 知识点列表 / Knowledge point list
     * @return 成功或失败
     */
    @PostMapping("/item/indexing-list")
    public boolean indexItems(@RequestBody @Valid KbDocumentIndexBatchReq req) {
        return knowledgeBaseService.indexItems(List.of(req.getUuids()), List.of(req.getIndexTypes()));
    }

    /**
* Check if knowledge base indexing is complete
     * 检查知识库是否已经索引完成
     *
     * @return 成功或失败
     */
    @GetMapping("/indexing/check")
    public boolean checkIndex() {
        return knowledgeBaseService.checkIndexIsFinish();
    }

    /**
* Like/Star
     * 点赞
     *
     * @return true:star;false:unstar
     */
    @PostMapping("/star/toggle")
    public boolean star(@RequestParam @NotBlank String kbUuid) {
        return knowledgeBaseService.toggleStar(ThreadContext.getCurrentUser(), kbUuid);
    }

    // ===================== 二开新增：细粒度共享 + 操作审计 =====================

    @PostMapping("/share/grant")
    public void shareGrant(@RequestParam String kbUuid,
                          @RequestParam Long targetId,
                          @RequestParam(defaultValue = "READ") String permission) {
        knowledgeBaseService.shareGrant(kbUuid, targetId, permission);
    }

    @PostMapping("/share/revoke")
    public void shareRevoke(@RequestParam String kbUuid,
                           @RequestParam Long targetId) {
        knowledgeBaseService.shareRevoke(kbUuid, targetId);
    }

    @GetMapping("/share/list/{kbUuid}")
    public List<KnowledgeBaseShare> listShares(@PathVariable String kbUuid) {
        return knowledgeBaseService.listShares(kbUuid);
    }

    @GetMapping("/audit/{kbUuid}")
    public Page<KnowledgeBaseAuditLog> listAudit(@PathVariable String kbUuid,
                                                @RequestParam(defaultValue = "1") @Min(1) Integer currentPage,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer pageSize) {
        return knowledgeBaseService.listAudit(kbUuid, currentPage, pageSize);
    }
}
