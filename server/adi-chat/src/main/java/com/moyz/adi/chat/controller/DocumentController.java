package com.moyz.adi.chat.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.IndexFailureDto;
import com.moyz.adi.common.dto.KbDocumentAttachmentDto;
import com.moyz.adi.common.dto.KbDocumentDto;
import com.moyz.adi.common.dto.KbDocumentEditReq;
import com.moyz.adi.common.dto.KbDocumentToggleStatusReq;
import com.moyz.adi.common.dto.KbDocApiReq;
import com.moyz.adi.common.dto.KbDocUrlReq;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.DocumentQaModeService;
import com.moyz.adi.common.service.IndexTaskService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.service.KbDocSourceService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

@RestController
@RequestMapping("/document")
@Validated
public class DocumentController {

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private DocumentQaModeService documentQaModeService;

    @Resource
    private IndexTaskService indexTaskService;

    @Resource
    private KbDocSourceService kbDocSourceService;

    @PostMapping("/saveOrUpdate")
    public KbDocument saveOrUpdate(@RequestBody @Valid KbDocumentEditReq itemEditReq) {
        return kbDocumentService.saveOrUpdate(itemEditReq);
    }

    /**
     * Save the form together with an optional Q&A file (multipart: form fields + file part):
     * the file is parsed and validated first, then the document is created/updated, the pairs
     * imported and vectorized synchronously; mutually exclusive with post-save AI generation
     * (the generate flag is ignored when a file is present)
     */
    @PostMapping("/saveOrUpdateWithFile")
    public KbDocument saveOrUpdateWithFile(@Valid KbDocumentEditReq itemEditReq,
                                           @RequestParam(value = "file", required = false) MultipartFile file) {
        return kbDocumentService.saveOrUpdateWithQaFile(itemEditReq, file);
    }

    @GetMapping("/search")
    public Page<KbDocumentDto> search(String kbUuid, String keyword, @NotNull @Min(1) Integer currentPage, @NotNull @Min(1) @Max(100) Integer pageSize) {
        knowledgeBaseService.checkReadPrivilege(kbUuid);
        return kbDocumentService.search(kbUuid, keyword, currentPage, pageSize);
    }

    @GetMapping("/info/{uuid}")
    public KbDocument info(@PathVariable String uuid) {
        return kbDocumentService.info(uuid);
    }

    /**
     * Source file of a file-converted document: display name plus access url
     */
    @GetMapping("/attachment/{uuid}")
    public KbDocumentAttachmentDto attachment(@PathVariable String uuid) {
        return kbDocumentService.getAttachment(uuid);
    }

    @PostMapping("/del/{uuid}")
    public boolean softDelete(@PathVariable String uuid) {
        return kbDocumentService.softDelete(uuid);
    }

    @PostMapping("/toggle-status")
    public boolean toggleStatus(@RequestBody @Valid KbDocumentToggleStatusReq req) {
        return kbDocumentService.toggleStatus(req.getUuid(), req.getIsEnabled());
    }

    /**
     * 失败重试：qa 生成失败走重新生成，索引失败按维度重新入队（服务端路由，前端不感知内部规则）
     */
    @PostMapping("/retryIndex/{uuid}")
    public boolean retryIndex(@PathVariable String uuid,
                              @RequestParam(value = "force", required = false, defaultValue = "false") boolean force) {
        return kbDocumentService.retryIndex(uuid, force);
    }

    /**
     * 扫描件补 OCR 后重索引：适用于"解析为空"失败的文档（无文本层的扫描件/截图），
     * 启用 OCR 后无需重新上传即可补齐内容
     */
    @PostMapping("/retryOcr/{uuid}")
    public boolean retryOcr(@PathVariable String uuid) {
        return kbDocumentService.retryOcr(uuid);
    }

    /**
     * 从网页 URL 建文档：抓取页面正文入库并按需索引（可配自动同步周期）
     * <p>
     * Create a document from a web page: the page text becomes the document body and, when
     * fetchIntervalMinutes is set, the source is re-fetched on that schedule.
     */
    @PostMapping("/fromUrl")
    public KbDocument createFromUrl(@RequestBody @Valid KbDocUrlReq req) {
        return kbDocSourceService.createFromUrl(req);
    }

    /**
     * 从 HTTP API 建文档：按配置的 method/headers/body 调用接口，JSON 响应按 contentPath 取正文
     * <p>
     * Create a document from an HTTP API: the response (optionally narrowed by contentPath)
     * becomes the document body.
     */
    @PostMapping("/fromApi")
    public KbDocument createFromApi(@RequestBody @Valid KbDocApiReq req) {
        return kbDocSourceService.createFromApi(req);
    }

    /**
     * 重新抓取：按文档自带的来源配置再抓一次，更新正文并重建索引（自动同步之外的手动入口）
     * <p>
     * Re-fetch a url/api document now, regardless of its schedule.
     */
    @PostMapping("/refetch/{uuid}")
    public boolean refetch(@PathVariable String uuid) {
        return kbDocSourceService.refetch(uuid);
    }

    /**
     * Latest failure per index dimension for the detail page failure list
     * (the doc row keeps only one fail_reason)
     */
    @GetMapping("/indexFailures/{uuid}")
    public List<IndexFailureDto> indexFailures(@PathVariable String uuid) {
        kbDocumentService.checkReadPrivilege(uuid);
        return indexTaskService.listDocumentFailures(uuid);
    }

    /**
     * Whether the doc has a queued or running index task (a dimension is marked DOING at
     * enqueue, so this distinguishes still-working from finally failed)
     */
    @GetMapping("/indexProgress/{uuid}")
    public boolean indexProgress(@PathVariable String uuid) {
        kbDocumentService.checkReadPrivilege(uuid);
        return indexTaskService.hasUnfinishedByDoc(uuid);
    }

    /**
     * （重新）生成问答对：无段行直接生成；已有问答对替换式重新生成（服务端守卫在跑/生成中并清理重建）
     */
    @PostMapping("/autoGenerateQa/{uuid}")
    public boolean autoGenerateQa(@PathVariable String uuid) {
        kbDocumentService.checkWritePrivilege(uuid);
        KbDocument doc = kbDocumentService.getEnable(uuid);
        if (null == doc) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        if (SegmentModeEnum.QA != doc.getSegmentMode()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        documentQaModeService.autoGenerateQa(ThreadContext.getCurrentUser(), doc);
        return true;
    }

    /**
     * 导入问答对到已有文档（追加：相同答案并入既有段、问题去重；服务端守卫生成中/在跑任务）
     */
    @PostMapping("/importQa/{uuid}")
    public boolean importQa(@PathVariable String uuid, @RequestParam("file") MultipartFile file) {
        kbDocumentService.checkWritePrivilege(uuid);
        KbDocument doc = kbDocumentService.getEnable(uuid);
        if (null == doc) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        if (SegmentModeEnum.QA != doc.getSegmentMode()) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        documentQaModeService.importQaToDocument(doc, file);
        return true;
    }

    /**
     * 批量导入 QA 对（Dify 格式 xlsx/csv：首行表头 question,answer，其后每行一对），
     * 生成 segment_mode=qa 的文档并触发向量化
     */
    @PostMapping("/uploadQa/{kbUuid}")
    public KbDocument uploadQa(@PathVariable String kbUuid, @RequestParam("file") MultipartFile file) {
        return knowledgeBaseService.uploadQa(kbUuid, file);
    }

    /**
     * QA 导入模板下载
     */
    @GetMapping("/qaImportTemplate")
    public ResponseEntity<byte[]> qaImportTemplate() {
        byte[] body = DocumentQaModeService.QA_IMPORT_TEMPLATE_CSV.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=qa_import_template.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}
