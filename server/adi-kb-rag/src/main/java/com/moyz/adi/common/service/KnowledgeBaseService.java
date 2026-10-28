package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.constant.RedisKeyConstant;
import com.moyz.adi.common.dto.KbEditReq;
import com.moyz.adi.common.dto.KbEmbeddingRebuildResp;
import com.moyz.adi.common.dto.KbInfoResp;
import com.moyz.adi.common.dto.KbQaDto;
import com.moyz.adi.common.dto.KbSearchReq;
import com.moyz.adi.common.dto.RefGraphDto;
import com.moyz.adi.common.entity.*;
import com.moyz.adi.common.enums.EmbeddingStatusEnum;
import com.moyz.adi.common.enums.LLMCallRecordSourceType;
import com.moyz.adi.common.enums.SegmentModeEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.file.FileOperatorContext;
import com.moyz.adi.common.helper.LLMContext;
import com.moyz.adi.common.helper.SseManager;
import com.moyz.adi.common.languagemodel.AbstractLLMService;
import com.moyz.adi.common.mapper.KnowledgeBaseMapper;
import com.moyz.adi.common.memory.shortterm.ChatMemoryStores;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.rag.*;
import com.moyz.adi.common.service.embedding.EmbeddingProviderInfo;
import com.moyz.adi.common.service.embedding.EmbeddingProviderService;
import com.moyz.adi.common.service.embedding.IKnowledgeEmbeddingService;
import com.moyz.adi.common.util.*;
import com.moyz.adi.common.util.NumberUtil;
import com.moyz.adi.common.vo.*;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.moyz.adi.common.constant.MetadataConstant.RetrieveContentFrom.KNOWLEDGE_BASE;
import static com.moyz.adi.common.constant.KbRagConstant.EMPTY_PARSE_FAIL_REASON;
import static com.moyz.adi.common.constant.KbRagConstant.OCR_EMPTY_FAIL_REASON;
import static com.moyz.adi.common.constant.SseConstant.SSE_TIMEOUT;
import static com.moyz.adi.common.constant.SysConfigConstant.SysConfigKey.QUOTA_BY_QA_ASK_DAILY;
import static com.moyz.adi.common.constant.RedisKeyConstant.KB_STATISTIC_RECALCULATE_SIGNAL;
import static com.moyz.adi.common.constant.RedisKeyConstant.USER_INDEXING;
import static com.moyz.adi.common.enums.ErrorEnum.*;
import static com.moyz.adi.common.util.LocalDateTimeUtil.PATTERN_YYYY_MM_DD;

@Slf4j
@Service
public class KnowledgeBaseService extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> {

    @Lazy
    @Resource
    private KnowledgeBaseService self;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private KbDocumentService kbDocumentService;

    @Resource
    private IndexTaskService indexTaskService;

    @Resource
    private KnowledgeBaseQaService knowledgeBaseQaRecordService;

    @Resource
    private KnowledgeBaseStarService knowledgeBaseStarRecordService;

    @Resource
    private FileService fileService;

    @Resource
    private SseManager sseManager;

    @Resource
    private UserDayCostService userDayCostService;

    @Resource
    private AiModelService aiModelService;

    @Resource
    private DocumentSegmentService documentSegmentService;

    @Resource
    private DocumentQaModeService documentQaModeService;

    @Resource
    private LLMCallRecordService llmCallRecordService;

    @Resource
    private OcrService ocrService;

    @Resource
    private DocumentTextCleaner documentTextCleaner;

    @Resource
    private KbAuditLogService kbAuditLogService;

    @Resource
    private KbShareService kbShareService;

    /**
     * S1-15：向量清理入口（pgvector 条件装配）。单测无容器时未注入，
     * 删库路径对其判空跳过——向量残留可由每日对账 job 兜底。
     */
    @Resource
    private IKnowledgeEmbeddingService iKnowledgeEmbeddingService;

    /**
     * 二开新增（T7-05）：embedding provider 解析。为 null 时（历史单测未注入）跳过
     * provider 记录，绝不因此让创建/重建主流程失败——记录是旁路，索溯源是目的。
     */
    @Resource
    private EmbeddingProviderService embeddingProviderService;

    @Resource
    private AdiProperties adiProperties;

    public KnowledgeBase saveOrUpdate(KbEditReq kbEditReq) {
        Long editId = resolveEditId(kbEditReq);
        boolean isEdit = null != editId && editId > 0;
        if (null == kbEditReq.getIngestModelId() || kbEditReq.getIngestModelId() <= 0) {
            // Editing requires an explicit model: silently substituting the first available
            // one would mask caller bugs; only creation keeps the auto default
            if (isEdit) {
                throw new BaseException(A_PARAMS_ERROR);
            }
        }
        KnowledgeBase knowledgeBase = new KnowledgeBase();
        BeanUtils.copyProperties(kbEditReq, knowledgeBase, "id", "uuid", "ingestTokenizer", "ingestEmbeddingModel");
        if (null != kbEditReq.getIngestModelId() && kbEditReq.getIngestModelId() > 0) {
            knowledgeBase.setIngestModelName(aiModelService.getByIdOrThrow(kbEditReq.getIngestModelId()).getName());
        } else {
            // No model specified on creation: default to the first available one
            LLMContext.getFirstEnableAndFree().ifPresent(llmService -> {
                knowledgeBase.setIngestModelName(llmService.getAiModel().getName());
                knowledgeBase.setIngestModelId(llmService.getAiModel().getId());
            });
        }
        if (StringUtils.isNotBlank(kbEditReq.getIngestTokenEstimator()) && AdiConstant.TokenEstimator.ALL.contains(kbEditReq.getIngestTokenEstimator())) {
            knowledgeBase.setIngestTokenEstimator(kbEditReq.getIngestTokenEstimator());
        }
        if (!isEdit) {
            User user = ThreadContext.getCurrentUser();
            knowledgeBase.setUuid(UuidUtil.createShort());
            knowledgeBase.setOwnerId(user.getId());
            knowledgeBase.setOwnerUuid(user.getUuid());
            knowledgeBase.setOwnerName(user.getName());
            // T7-05：创建即记录当前 embedding provider（新建库的向量只会由当前 provider 产出）。
            // 解析失败（远端模型未配维度等）不阻断创建——向量入库时本来就会失败，
            // 那时错误信息更贴近现场；这里静默跳过只损失溯源信息。
            recordCurrentProvider(knowledgeBase);
            baseMapper.insert(knowledgeBase);
        } else {
            checkWritePrivilege(editId, null);
            KnowledgeBase old = baseMapper.selectById(editId);
            knowledgeBase.setId(editId);
            baseMapper.updateById(knowledgeBase);
            // "变更即失效"：切段参数变更即失效该库全部可切段文档的段行，下次重新向量化按新参数重切
            if (old != null && splitParamsChanged(old, kbEditReq)) {
                kbDocumentService.invalidateSegmentsByKb(old.getUuid());
            }
        }
        String kbUuid = isEdit ? kbEditReq.getUuid() : knowledgeBase.getUuid();
        String action = isEdit ? KbAuditLogService.ACTION_KB_UPDATE : KbAuditLogService.ACTION_KB_CREATE;
        kbAuditLogService.record(kbUuid, knowledgeBase.getTitle(), action, KbAuditLogService.TARGET_KB, kbUuid, true, "");
        return knowledgeBase;
    }

    /**
     * 解析「这次是新增还是编辑」，返回要更新的主键（新增返回 null）。
     * <p>
     * 历史行为只看 {@code id}：调用方传了 {@code uuid} 却没传 {@code id} 时，服务端会当作**新增**，
     * 于是"保存知识库"这种本该是更新的调用会静默多插一条同名记录（uuid 被完全忽略），
     * 既不报错也不提示，属于会污染数据的哑失败。
     * 现在 id 缺失时按 uuid 回查既有记录：查得到按编辑处理，查不到明确报「数据不存在」。
     * </p>
     * Decide whether this call creates or updates, returning the id to update (null for create).
     * <p>
     * The old logic only looked at {@code id}: a caller passing {@code uuid} without {@code id} was
     * treated as a **create**, so an update-shaped call silently inserted a duplicate row (the uuid
     * was ignored entirely) - a silent failure that corrupts data. Now a missing id is resolved via
     * uuid: an existing row becomes an update, a missing one reports "data not found".
     * </p>
     */
    private Long resolveEditId(KbEditReq kbEditReq) {
        if (null != kbEditReq.getId() && kbEditReq.getId() > 0) {
            return kbEditReq.getId();
        }
        if (StringUtils.isBlank(kbEditReq.getUuid())) {
            return null;
        }
        KnowledgeBase exist = this.lambdaQuery()
                .eq(KnowledgeBase::getUuid, kbEditReq.getUuid())
                .eq(KnowledgeBase::getIsDeleted, false)
                .one();
        if (null == exist) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        return exist.getId();
    }

    private boolean splitParamsChanged(KnowledgeBase old, KbEditReq req) {
        return !Objects.equals(old.getIngestMaxSegmentSize(), req.getIngestMaxSegmentSize())
                || !Objects.equals(old.getIngestMaxOverlap(), req.getIngestMaxOverlap())
                || !Objects.equals(old.getIngestSplitStrategy(), req.getIngestSplitStrategy())
                || !Objects.equals(old.getIngestCustomSeparator(), req.getIngestCustomSeparator())
                || !Objects.equals(old.getIngestTokenEstimator(), req.getIngestTokenEstimator());
    }

    /**
     * 批量导入 QA 对（Dify 格式）：生成 qa 模式文档并触发向量化（含 USER_INDEXING 守卫）
     */
    public KbDocument uploadQa(String kbUuid, MultipartFile file) {
        checkWritePrivilege(null, kbUuid);
        KnowledgeBase knowledgeBase = getOrThrow(kbUuid);
        String fileName = file.getOriginalFilename();
        KbDocument doc = documentQaModeService.importQa(knowledgeBase, fileName == null || fileName.isBlank() ? "qa_import" : fileName, file);
        indexItems(List.of(doc.getUuid()), List.of(AdiConstant.DOC_INDEX_TYPE_EMBEDDING));
        stringRedisTemplate.opsForSet().add(KB_STATISTIC_RECALCULATE_SIGNAL, kbUuid);
        return doc;
    }

    public List<AdiFile> uploadDocs(String kbUuid, Boolean embedding, MultipartFile[] docs, List<String> indexTypes) {
        if (ArrayUtils.isEmpty(docs)) {
            return Collections.emptyList();
        }
        checkWritePrivilege(null, kbUuid);
        List<AdiFile> result = new ArrayList<>();
        KnowledgeBase knowledgeBase = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(KnowledgeBase::getUuid, kbUuid)
                .eq(KnowledgeBase::getIsDeleted, false)
                .oneOpt()
                .orElseThrow(() -> new BaseException(A_DATA_NOT_FOUND));
        // Failures used to be swallowed here: one bad file was logged and skipped, the response
        // just came back one item shorter and the user believed every file had landed. Now the
        // batch reports what failed and why, so "uploaded but not searchable" cannot happen silently.
        List<String> failures = new ArrayList<>();
        for (MultipartFile doc : docs) {
            String name = null == doc.getOriginalFilename() ? "" : doc.getOriginalFilename();
            try {
                result.add(uploadDoc(knowledgeBase, doc, embedding, indexTypes, SegmentModeEnum.TEXT, null));
            } catch (BaseException e) {
                log.warn("uploadDocs rejected,fileName:{},reason:{}", name, e.getInfo());
                failures.add(name + " - " + e.getInfo());
            } catch (Exception e) {
                log.warn("uploadDocs fail,fileName:{}", name, e);
                failures.add(name + " - " + SpringUtil.getMessage(A_UPLOAD_FAIL.getInfo()));
            }
        }
        if (!failures.isEmpty()) {
            // Successful files are already persisted; the caller sees both the partial result
            // (via the refreshed list) and the explicit failure list.
            throw new BaseException(A_UPLOAD_FAIL_BY_, String.join("；", failures));
        }
        return result;
    }

    /**
     * @param segmentMode       segment mode; qa parses the file as Q&A pair data (Dify-format xlsx/csv),
     *                          one Q&A document per file
     * @param childMaxChunkSize child chunk max token size, used by parent-child mode only
     */
    public AdiFile uploadDoc(String kbUuid, Boolean indexAfterUpload, MultipartFile doc, List<String> indexTypes,
                             SegmentModeEnum segmentMode, Integer childMaxChunkSize) {
        checkWritePrivilege(null, kbUuid);
        KnowledgeBase knowledgeBase = ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(KnowledgeBase::getUuid, kbUuid)
                .eq(KnowledgeBase::getIsDeleted, false)
                .oneOpt()
                .orElseThrow(() -> new BaseException(A_DATA_NOT_FOUND));
        return uploadDoc(knowledgeBase, doc, indexAfterUpload, indexTypes, segmentMode, childMaxChunkSize);
    }

    private AdiFile uploadDoc(KnowledgeBase knowledgeBase, MultipartFile doc, Boolean indexAfterUpload, List<String> indexTypes,
                              SegmentModeEnum segmentMode, Integer childMaxChunkSize) {
        try {
            String fileName = doc.getOriginalFilename();
            // Q&A mode: the file is the pair data; no source file record is kept (raw Q/A
            // text goes into remark) and questions are vectorized synchronously
            if (SegmentModeEnum.QA == segmentMode) {
                KbDocument qaDoc = documentQaModeService.importQa(knowledgeBase,
                        fileName == null || fileName.isBlank() ? "qa_import" : fileName, doc);
                // user-level in-flight key held for the duration of the synchronous vectorization.
                // Protocol matches the task executor (seed when absent, increment per in-flight
                // unit, decrement + delete at <=0): a plain SET/DELETE clobbers the executor's
                // live counter when a concurrently dispatched task is being counted
                String userIndexKey = MessageFormat.format(USER_INDEXING, knowledgeBase.getOwnerId());
                stringRedisTemplate.opsForValue().setIfAbsent(userIndexKey, "0", 10, TimeUnit.MINUTES);
                stringRedisTemplate.opsForValue().increment(userIndexKey);
                try {
                    documentQaModeService.vectorizePendingQaDoc(knowledgeBase, qaDoc, ThreadContext.getCurrentUser());
                } finally {
                    Long remaining = stringRedisTemplate.opsForValue().decrement(userIndexKey);
                    if (remaining != null && remaining <= 0) {
                        stringRedisTemplate.delete(userIndexKey);
                    }
                }
                stringRedisTemplate.opsForSet().add(KB_STATISTIC_RECALCULATE_SIGNAL, knowledgeBase.getUuid());
                kbAuditLogService.record(knowledgeBase.getUuid(), knowledgeBase.getTitle(),
                        KbAuditLogService.ACTION_KB_DOC_UPLOAD, KbAuditLogService.TARGET_DOC, qaDoc.getUuid(), true,
                        "file=" + (fileName == null ? "qa_import" : fileName));
                return null;
            }
            AdiFile adiFile = fileService.saveFile(doc, false);

            //解析文档
            // Parse the document. A scanned / image-only PDF (or a blank txt) gives the parser
            // nothing to work with and langchain4j throws BlankDocumentException outright — which
            // used to surface as a bare "upload failed" with no clue. Catch it here and let the
            // document land as FAILED with a readable reason instead.
            Document document = null;
            // blankParse: a parser exists for this type but found no text (scanned PDF, blank txt).
            // unsupportedType: no parser handles this type at all (images, archives, ...).
            boolean blankParse = false;
            boolean unsupportedType = false;
            try {
                document = FileOperatorContext.loadDocument(adiFile);
            } catch (BlankDocumentException e) {
                log.warn("Parser found no text in uploaded file {} ({})", fileName, adiFile.getExt());
                blankParse = true;
            }
            if (null == document && !blankParse) {
                unsupportedType = true;
            }
//Create knowledge base item
            //创建知识库条目
            String uuid = UuidUtil.createShort();
//PostgreSQL does not support \u0000
            //postgresql不支持\u0000
            String content = null == document ? "" : document.text().replace("\u0000", "");
            // Scanned / image-only PDFs (and screenshots, which no parser handles at all) carry no
            // text layer. Uploading one used to be a silent trap: the document was stored, an index
            // task ran and reported success, yet nothing was ever retrievable and nothing told the
            // user why. With an OCR backend configured we recover the text here and the document
            // behaves like a normal one; without OCR we flag it with a readable reason and skip the
            // index task that could only produce an empty index.
            boolean ocrTried = false;
            if ((unsupportedType || StringUtils.isBlank(content)) && ocrService.isEnabled() && ocrService.supports(adiFile.getExt())) {
                ocrTried = true;
                String ocrText = ocrService.recognize(adiFile);
                if (StringUtils.isNotBlank(ocrText)) {
                    content = ocrText.replace("\u0000", "");
                    unsupportedType = false;
                    log.info("OCR recovered {} chars from file {} ({})", content.length(), fileName, adiFile.getExt());
                }
            }
            // Types nobody can parse (images with OCR off, archives that slipped past the upload
            // blacklist, unknown extensions) used to be dropped here: one warn line, no document
            // row, no error — the upload looked successful and the content was simply unreachable.
            // Rejecting it is the whole point: the user must see which file and why.
            if (unsupportedType && !ocrTried) {
                log.warn("This file type:{} cannot be parsed, rejecting upload of {}", adiFile.getExt(), fileName);
                throw new BaseException(A_DOC_TYPE_UNSUPPORTED, null == adiFile.getExt() ? "" : adiFile.getExt());
            }
            // 清洗：解析输出 → 清洗 → 入库。remark 是切分的输入，raw_text 保留清洗前的原文，
            // 供分块预览对照「解析 → 清洗 → 切分」。清洗只做换行/空白/控制字符层面的整理，
            // 失败也不影响入库（clean 内部自行兜底）。
            // Cleanup: parsed text -> cleaned text -> stored. remark is what gets split, raw_text
            // keeps the pre-cleanup original so the chunk preview can show the whole chain.
            DocumentTextCleaner.CleanResult cleanResult = documentTextCleaner.clean(content);
            String rawText = content;
            content = cleanResult.text();
            if (cleanResult.applied() && cleanResult.removedChars() > 0) {
                log.info("Document cleaned, file:{}, {} -> {} chars, {} -> {} lines (merged:{} dropped:{})",
                        fileName, cleanResult.rawChars(), cleanResult.cleanedChars(),
                        cleanResult.rawLines(), cleanResult.cleanedLines(),
                        cleanResult.mergedLines(), cleanResult.droppedLines());
            }
            boolean noParsableText = StringUtils.isBlank(content);
            KbDocument knowledgeBaseItem = new KbDocument();
            knowledgeBaseItem.setUuid(uuid);
            knowledgeBaseItem.setKbId(knowledgeBase.getId());
            knowledgeBaseItem.setKbUuid(knowledgeBase.getUuid());
            knowledgeBaseItem.setSourceFileId(adiFile.getId());
            knowledgeBaseItem.setTitle(fileName);
            knowledgeBaseItem.setBrief(StringUtils.substring(content, 0, 200));
            knowledgeBaseItem.setRemark(content);
            knowledgeBaseItem.setRawText(rawText);
            // Segment mode is document-level, chosen by the upload entry; defaults to text
            knowledgeBaseItem.setSegmentMode(null == segmentMode ? SegmentModeEnum.TEXT : segmentMode);
            if (null != childMaxChunkSize) {
                if (childMaxChunkSize < AdiConstant.ChildChunkSize.MIN || childMaxChunkSize > AdiConstant.ChildChunkSize.MAX) {
                    throw new BaseException(A_PARAMS_ERROR);
                }
                knowledgeBaseItem.setChildMaxChunkSize(childMaxChunkSize);
            }
            if (noParsableText) {
                log.warn("Uploaded file {} ({}) has no parsable text, marking as failed", fileName, adiFile.getExt());
                knowledgeBaseItem.setEmbeddingStatus(EmbeddingStatusEnum.FAIL);
                // Reason differs by configuration: without OCR the user must pre-process the scan,
                // with OCR enabled but silent the hint is to check the OCR service itself.
                knowledgeBaseItem.setFailReason(ocrTried ? OCR_EMPTY_FAIL_REASON : EMPTY_PARSE_FAIL_REASON);
            }
            boolean success = kbDocumentService.save(knowledgeBaseItem);
            if (success) {
                kbAuditLogService.record(knowledgeBase.getUuid(), knowledgeBase.getTitle(),
                        KbAuditLogService.ACTION_KB_DOC_UPLOAD, KbAuditLogService.TARGET_DOC,
                        knowledgeBaseItem.getUuid(), true, "file=" + fileName);
            }
            if (success && Boolean.TRUE.equals(indexAfterUpload) && !noParsableText) {
                // default index type is embedding; empty types would enqueue nothing
                List<String> types = indexTypes.isEmpty() ? List.of(AdiConstant.DOC_INDEX_TYPE_EMBEDDING) : indexTypes;
                kbDocumentService.checkAndIndexing(knowledgeBase, List.of(uuid), types);
            }

            //Replace file path with url
            adiFile.setPath(FileOperatorContext.getFileUrl(adiFile));
            return adiFile;
        } catch (BaseException e) {
            // BaseException carries a user-readable message; rethrow as-is
            throw e;
        } catch (Exception e) {
            log.error("upload error", e);
            throw new BaseException(A_UPLOAD_FAIL);
        }
    }

    /**
     * 索引（向量化、图谱化）
     *
     * @param kbUuid     知识库uuid
     * @param indexTypes 索引类型，如embedding,graphical
     * @return 成功或失败
     */
    public boolean indexing(String kbUuid, List<String> indexTypes) {
        checkWritePrivilege(null, kbUuid);
        KnowledgeBase knowledgeBase = this.getOrThrow(kbUuid);
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KbDocument::getIsDeleted, false);
        // 按知识库uuid过滤该库下所有文档（原误用文档uuid列匹配kbUuid，导致全库索引恒为空）
        wrapper.eq(KbDocument::getKbUuid, kbUuid);
        // 整库重索引=fan-out 每文档一行任务（与任务队列粒度一致，可并行、独立失败隔离）
        User user = ThreadContext.getCurrentUser();
        BizPager.oneByOneWithAnchor(wrapper, kbDocumentService, KbDocument::getId,
                kbItem -> {
                    for (String indexType : indexTypes) {
                        indexTaskService.enqueueDocument(knowledgeBase.getUuid(), kbItem.getUuid(), indexType, user);
                    }
                });
        return true;
    }

    /**
     * 索引知识点（同一知识库下）
     *
     * @param itemUuids  知识点uuid列表
     * @param indexTypes 索引类型，如embedding,graphical
     * @return 成功或失败
     */
    public boolean indexItems(List<String> itemUuids, List<String> indexTypes) {
        if (CollectionUtils.isEmpty(itemUuids)) {
            return false;
        }
        KnowledgeBase knowledgeBase = baseMapper.getByItemUuid(itemUuids.get(0));
        if (knowledgeBase == null) {
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        String userIndexKey = MessageFormat.format(USER_INDEXING, knowledgeBase.getOwnerId());
        Boolean exist = stringRedisTemplate.hasKey(userIndexKey);
        if (Boolean.TRUE.equals(exist)) {
            log.warn("Document is being indexed, please avoid frequent operations, userId:{}", knowledgeBase.getOwnerId());
            throw new BaseException(A_DOC_INDEX_DOING);
        }
        try {
            return kbDocumentService.checkAndIndexing(knowledgeBase, itemUuids, indexTypes);
        } catch (Exception e) {
            log.error("indexItems error", e);
            throw e;
        }
    }

    /**
     * 检查当前用户下的索引任务是否已经结束
     *
     * @return 成功或失败
     */
    public boolean checkIndexIsFinish() {
        String userIndexKey = MessageFormat.format(USER_INDEXING, ThreadContext.getCurrentUserId());
        return Boolean.FALSE.equals(stringRedisTemplate.hasKey(userIndexKey));
    }

    public Page<KbInfoResp> searchMine(String keyword, Boolean includeOthersPublic, Long categoryId, Integer currentPage, Integer pageSize) {
        Page<KbInfoResp> result = new Page<>();
        User user = ThreadContext.getCurrentUser();
        Page<KnowledgeBase> knowledgeBasePage;
        if (Boolean.TRUE.equals(user.getIsAdmin())) {
            knowledgeBasePage = baseMapper.searchByAdmin(new Page<>(currentPage, pageSize), keyword, categoryId);
        } else {
            knowledgeBasePage = baseMapper.searchByUser(new Page<>(currentPage, pageSize), user.getId(), keyword, includeOthersPublic, categoryId);
        }
        return MPPageUtil.convertToPage(knowledgeBasePage, result, KbInfoResp.class, null);
    }

    public Page<KbInfoResp> search(KbSearchReq req, Integer currentPage, Integer pageSize) {
        Page<KbInfoResp> result = new Page<>();
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(req.getTitle())) {
            wrapper.like(KnowledgeBase::getTitle, req.getTitle());
        }
        if (StringUtils.isNotBlank(req.getOwnerName())) {
            wrapper.like(KnowledgeBase::getOwnerName, req.getOwnerName());
        }
        if (null != req.getIsPublic()) {
            wrapper.eq(KnowledgeBase::getIsPublic, req.getIsPublic());
        }
        // 二开新增（2026-10-09）：按分类过滤，null 表示不过滤，0 = 未分类
        if (null != req.getCategoryId()) {
            wrapper.eq(KnowledgeBase::getCategoryId, req.getCategoryId());
        }
        if (null != req.getMinItemCount()) {
            wrapper.ge(KnowledgeBase::getItemCount, req.getMinItemCount());
        }
        if (null != req.getMinEmbeddingCount()) {
            wrapper.ge(KnowledgeBase::getEmbeddingCount, req.getMinEmbeddingCount());
        }
        if (null != req.getCreateTime() && req.getCreateTime().length == 2) {
            wrapper.between(KnowledgeBase::getCreateTime, LocalDateTimeUtil.parse(req.getCreateTime()[0]), LocalDateTimeUtil.parse(req.getCreateTime()[1]));
        }
        if (null != req.getUpdateTime() && req.getUpdateTime().length == 2) {
            wrapper.between(KnowledgeBase::getUpdateTime, LocalDateTimeUtil.parse(req.getUpdateTime()[0]), LocalDateTimeUtil.parse(req.getUpdateTime()[1]));
        }
        wrapper.eq(KnowledgeBase::getIsDeleted, false);
        wrapper.orderByDesc(KnowledgeBase::getStarCount, KnowledgeBase::getUpdateTime);
        Page<KnowledgeBase> knowledgeBasePage = baseMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        return MPPageUtil.convertToPage(knowledgeBasePage, result, KbInfoResp.class, null);
    }

    public List<KbInfoResp> listByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return Collections.emptyList();
        }
        List<KnowledgeBase> knowledgeBases = baseMapper.selectByIds(ids);
        return MPPageUtil.convertToList(knowledgeBases, KbInfoResp.class);
    }

    /**
     * 单个知识库详情（含全部检索配置）。管理后台「召回测试」用它带出库上的默认参数。
     * <p>
     * 走 {@link #getReadableOrThrow(String)}：管理员旁路已在权限判断里处理，普通用户仍只能读自己
     * 有权限的库，避免把私有库的配置暴露给无关用户。
     */
    public KbInfoResp info(String uuid) {
        KnowledgeBase kb = getReadableOrThrow(uuid);
        KbInfoResp resp = new KbInfoResp();
        BeanUtils.copyProperties(kb, resp);
        return resp;
    }

    /**
     * T3-14（2026-10-19）：工作流导入时按 kbUuid 批量校验知识包是否存在（未删除即视为存在）。
     * 返回入参中实际存在的 uuid 集合；调用方用差集判定缺失并明确报错（不允许静默丢节点）。
     */
    public List<String> filterExistingUuids(Collection<String> uuids) {
        if (CollectionUtils.isEmpty(uuids)) {
            return List.of();
        }
        return ChainWrappers.lambdaQueryChain(baseMapper)
                .in(KnowledgeBase::getUuid, uuids)
                .eq(KnowledgeBase::getIsDeleted, false)
                .select(KnowledgeBase::getUuid)
                .list()
                .stream()
                .map(KnowledgeBase::getUuid)
                .toList();
    }

    public boolean softDelete(String uuid) {
        checkWritePrivilege(null, uuid);
        KnowledgeBase kb = getOrThrow(uuid);
        // S1-15: cascade in one DB transaction; remote store cleanup (vectors, graph) stays
        // OUTSIDE it — same pattern as the document-level delete (remote calls must not hold
        // a pooled DB connection; on failure the leftovers are unreachable and get purged by
        // the daily orphan-vector reconciliation job).
        if (!self.softDeleteCascadeDb(uuid)) {
            return false;
        }
        // best-effort vector cleanup for the whole KB in one statement (metadata kb_uuid key)
        try {
            iKnowledgeEmbeddingService.deleteByKbUuid(uuid);
        } catch (Exception e) {
            log.error("Delete KB vectors failed, kbUuid:{}", uuid, e);
        }
        // best-effort graph footprint cleanup, per document (ledger-driven, doc-scoped API)
        try {
            kbDocumentService.removeGraphFootprintsByKb(uuid);
        } catch (Exception e) {
            log.error("Remove KB graph footprints failed, kbUuid:{}", uuid, e);
        }
        kbAuditLogService.record(uuid, kb.getTitle(), KbAuditLogService.ACTION_KB_DELETE,
                KbAuditLogService.TARGET_KB, uuid, true, "cascade=true");
        return true;
    }

    /**
     * S1-15: DB part of the KB delete cascade (single transaction): soft-delete the KB row,
     * cancel all its unfinished index tasks, soft-delete every document, every segment row
     * (questions and child chunks follow), and the derived share / star records.
     * Defined on self-proxy so @Transactional applies when called from {@link #softDelete(String)}.
     */
    @Transactional
    public boolean softDeleteCascadeDb(String uuid) {
        boolean ok = ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(KnowledgeBase::getUuid, uuid)
                .set(KnowledgeBase::getIsDeleted, true)
                .update();
        if (!ok) {
            return false;
        }
        // cancel queued/running index tasks first: pending fails in place, running aborts at
        // its next checkpoint — otherwise an in-flight task would re-embed a deleted KB
        indexTaskService.cancelByKb(uuid);
        // soft-delete every document of the KB
        ChainWrappers.lambdaUpdateChain(kbDocumentService.getBaseMapper())
                .eq(KbDocument::getKbUuid, uuid)
                .set(KbDocument::getIsDeleted, true)
                .update();
        // segment rows (questions and child chunks cascade inside)
        documentSegmentService.deleteByKbUuid(uuid);
        // derived records: shares are physical rows (re-grantable), stars keep audit trail
        kbShareService.deleteByKb(uuid);
        knowledgeBaseStarRecordService.softDeleteByKb(uuid);
        return true;
    }

    /**
     * 共享知识库给指定用户（二开新增）。仅 owner 或 admin 可操作。
     */
    public void shareGrant(String kbUuid, Long targetId, String permission) {
        checkWritePrivilege(null, kbUuid);
        KnowledgeBase kb = getOrThrow(kbUuid);
        kbShareService.grant(kbUuid, targetId, permission);
        kbAuditLogService.record(kbUuid, kb.getTitle(), KbAuditLogService.ACTION_SHARE_GRANT,
                KbAuditLogService.TARGET_SHARE, String.valueOf(targetId), true, "permission=" + permission);
    }

    public void shareRevoke(String kbUuid, Long targetId) {
        checkWritePrivilege(null, kbUuid);
        KnowledgeBase kb = getOrThrow(kbUuid);
        kbShareService.revoke(kbUuid, targetId);
        kbAuditLogService.record(kbUuid, kb.getTitle(), KbAuditLogService.ACTION_SHARE_REVOKE,
                KbAuditLogService.TARGET_SHARE, String.valueOf(targetId), true, "");
    }

    public List<KnowledgeBaseShare> listShares(String kbUuid) {
        checkWritePrivilege(null, kbUuid);
        return kbShareService.listByKb(kbUuid);
    }

    public Page<KnowledgeBaseAuditLog> listAudit(String kbUuid, int currentPage, int pageSize) {
        checkWritePrivilege(null, kbUuid);
        return kbAuditLogService.pageByKb(kbUuid, currentPage, pageSize);
    }

    public SseEmitter sseAsk(String qaRecordUuid) {
        getReadableQaOrThrow(qaRecordUuid);
        checkRequestTimesOrThrow();
        String sseUuid = UuidUtil.createShort();
        SseEmitter sseEmitter = new SseEmitter(SSE_TIMEOUT);
        User user = ThreadContext.getCurrentUser();
        if (!sseManager.checkOrComplete(user, sseUuid, sseEmitter)) {
            return sseEmitter;
        }
        sseManager.startSse(user, sseUuid, sseEmitter, null);
        self.retrieveAndPushToLLM(user, sseUuid, qaRecordUuid);
        return sseEmitter;
    }

    /**
     * Blocking knowledge base Q&A: retrieve from knowledge base, then call LLM synchronously.
     *
     * @param user          current user
     * @param knowledgeBase knowledge base entity
     * @param qaDto         QA record DTO
     * @return JSON response with answer
     */
    public Map<String, Object> blockingAsk(User user, KnowledgeBase knowledgeBase, KbQaDto qaDto) {
        TokenEstimatorThreadLocal.setTokenEstimator(knowledgeBase.getIngestTokenEstimator());
        try {
            return doBlockingAsk(user, knowledgeBase, qaDto);
        } finally {
            TokenEstimatorThreadLocal.clearTokenEstimator();
        }
    }

    private Map<String, Object> doBlockingAsk(User user, KnowledgeBase knowledgeBase, KbQaDto qaDto) {
        checkRequestTimesOrThrow();
        KnowledgeBaseQa qaRecord = knowledgeBaseQaRecordService.getOrThrow(qaDto.getUuid());
        Long aiModelId = qaRecord.getAiModelId();
        AiModel aiModel = null != aiModelId && aiModelId > 0
                ? aiModelService.getByIdOrThrow(aiModelId)
                : LLMContext.getAiModel(null, null);
        //No model resolved (none configured / none enabled) must not 500 the whole request:
        //fall back to the knowledge base's own retrieve limit instead of sizing by token budget
        if (null == aiModel) {
            log.warn("No ai model resolved for blocking QA, kbUuid:{}, qaUuid:{}; falling back to kb retrieve settings",
                    qaRecord.getKbUuid(), qaRecord.getUuid());
        }
        int maxInputTokens = null == aiModel ? 0 : aiModel.getMaxInputTokens();
        int maxResults = knowledgeBase.getRetrieveMaxResults();
        if (maxResults < 1 && maxInputTokens > 0) {
            String memoryId = knowledgeBase.getUuid() + "_" + user.getUuid();
            int reservedTokens = computeReservedTokens(memoryId, knowledgeBase.getQuerySystemMessage());
            maxResults = EmbeddingRag.getRetrieveMaxResults(qaRecord.getQuestion(), maxInputTokens, reservedTokens);
        }
        if (maxResults < 1 && maxInputTokens == 0) {
            //No explicit setting and no token budget to derive from: use the default recall size.
            //When a budget existed and produced 0 the question is simply too long — keep 0 so the
            //strict-mode check below still applies
            maxResults = AdiConstant.RAG_RETRIEVE_NUMBER_MAX;
        }

        // Retrieve from knowledge base
        String knowledgeContext = "";
        List<ContentRetriever> retrievers = new ArrayList<>();
        if (maxResults == 0) {
            // User question too long, no retrieval possible
            if (Boolean.TRUE.equals(knowledgeBase.getIsStrict())) {
                throw new BaseException(A_PARAMS_ERROR);
            }
        }
        if (maxResults > 0) {
            ChatModel chatModel = LLMContext.getServiceById(knowledgeBase.getIngestModelId(), true).buildChatLLM(
                    ChatModelBuilderProperties.builder()
                            .temperature(knowledgeBase.getQueryLlmTemperature())
                            .build());
            Set<String> disabledDocUuids = new HashSet<>(kbDocumentService.listDisabledItemUuids(qaRecord.getKbUuid()));
            RetrieverCreateParam createParam = RetrieverCreateParam.builder()
                    .chatModel(chatModel)
                    .filter(new IsEqualTo(AdiConstant.MetadataKey.KB_UUID, qaRecord.getKbUuid()))
                    .maxResults(maxResults)
                    .minScore(knowledgeBase.getRetrieveMinScore())
                    .breakIfSearchMissed(knowledgeBase.getIsStrict())
                    .excludedItemUuids(disabledDocUuids)
                    .context(buildRetrieverContext(knowledgeBase, disabledDocUuids))
                    .kbUuid(qaRecord.getKbUuid())
                    .build();
            CompositeRag compositeRag = new CompositeRag(AdiConstant.RetrieveContentFrom.KNOWLEDGE_BASE);
            List<RetrieverWrapper> retrieverWrappers = compositeRag.createRetriever(createParam);
            // The wrapper's response list starts empty by design: retrieval is executed here, one
            // call per retriever. Iterating getResponse() without retrieving would silently yield
            // an empty context and make blocking QA answer from the model alone.
            StringBuilder sb = new StringBuilder();
            for (RetrieverWrapper wrapper : retrieverWrappers) {
                ContentRetriever retriever = wrapper.getRetriever();
                if (null == retriever) {
                    continue;
                }
                List<Content> contents;
                try {
                    contents = retriever.retrieve(dev.langchain4j.rag.query.Query.from(qaRecord.getQuestion()));
                } catch (BaseException e) {
                    // strict mode raises B_BREAK_SEARCH when nothing is found; propagate as-is
                    throw e;
                } catch (Exception e) {
                    log.error("Blocking QA retrieval failed, kbUuid:{}, retriever:{}",
                            qaRecord.getKbUuid(), wrapper.getContentFrom(), e);
                    continue;
                }
                wrapper.setResponse(contents);
                retrievers.add(retriever);
                if (null != contents) {
                    for (Content content : contents) {
                        if (null != content && null != content.textSegment()) {
                            sb.append(content.textSegment().text()).append("\n");
                        }
                    }
                }
            }
            knowledgeContext = sb.toString();
        }

        // Build prompt
        String prompt = PromptUtil.createPrompt(qaRecord.getQuestion(), "", knowledgeContext, "");

        // Call LLM in blocking mode
        String uuid = UuidUtil.createShort();
        AbstractLLMService llmService = aiModel != null
                ? LLMContext.getServiceOrDefault(null, aiModel.getName())
                : LLMContext.getServiceOrDefault(null, null);
        ChatModelBuilderProperties modelProperties = ChatModelBuilderProperties.builder()
                .temperature(knowledgeBase.getQueryLlmTemperature())
                .build();
        ChatModelRequest chatRequestParams = ChatModelRequest.builder()
                .memoryId(knowledgeBase.getUuid() + "_" + user.getUuid())
                .systemMessage(AssistantIdentityGuard.append(knowledgeBase.getQuerySystemMessage(), knowledgeBase.getTitle()))
                .userMessage(prompt)
                .build();
        SseAskParam sseAskParam = SseAskParam.builder()
                .user(user)
                .uuid(uuid)
                .modelProperties(modelProperties)
                .httpRequestParams(chatRequestParams)
                .build();

        long llmStartTime = System.currentTimeMillis();
        ChatResponse chatResponse = llmService.chat(sseAskParam);
        int llmDuration = NumberUtil.saturatedCastToInt(System.currentTimeMillis() - llmStartTime);

        // Update QA record
        KnowledgeBaseQa updateRecord = new KnowledgeBaseQa();
        updateRecord.setId(qaRecord.getId());
        updateRecord.setPrompt(prompt);
        updateRecord.setAnswer(chatResponse.aiMessage().text());
        knowledgeBaseQaRecordService.updateById(updateRecord);

        // Aggregate token usage across all LLM calls in this request (a single chat() may
        // recurse through multiple tool calls, each cached under the same uuid in Redis).
        Pair<Integer, Integer> tokenCost = LLMTokenUtil.calAllTokenCostByUuid(stringRedisTemplate, uuid);
        int totalInputTokens = tokenCost.getLeft();
        int totalOutputTokens = tokenCost.getRight();
        int allToken = totalInputTokens + totalOutputTokens;

        // Record references and hit counts, same as the streaming path: without this, blocking
        // QA never attributes citations and segment/document hit statistics stay at zero
        createRef(retrievers, user, qaRecord.getId());

        // Record token consumption and save LLM call record
        if (allToken > 0) {
            userDayCostService.appendCostToUser(user, allToken, false);
            LLMCallRecord callRecord = new LLMCallRecord();
            callRecord.setUuid(UuidUtil.createShort());
            callRecord.setSourceType(LLMCallRecordSourceType.KNOWLEDGE_BASE_QA.getValue());
            callRecord.setSourceId(qaRecord.getId());
            callRecord.setUserId(user.getId());
            // aiModel may be null when no model was resolved; guard instead of NPE-ing after
            // the answer has already been produced
            callRecord.setModelPlatform(null == aiModel ? null : aiModel.getPlatform());
            callRecord.setModelName(null == aiModel ? null : aiModel.getName());
            callRecord.setInputTokens(totalInputTokens);
            callRecord.setOutputTokens(totalOutputTokens);
            callRecord.setDuration(llmDuration);
            llmCallRecordService.saveAsync(callRecord);
        }

        // Build response
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("message_id", uuid);
        data.put("answer", chatResponse.aiMessage().text());
        if (allToken > 0) {
            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("prompt_tokens", totalInputTokens);
            usage.put("completion_tokens", totalOutputTokens);
            usage.put("total_tokens", allToken);
            data.put("usage", usage);
        }
        return data;
    }

    /**
     * Star or unstar
     *
     * @param user   用户
     * @param kbUuid 知识库uuid
     * @return true:star;false:unstar
     */
    @Transactional
    public boolean toggleStar(User user, String kbUuid) {

        KnowledgeBase knowledgeBase = self.getOrThrow(kbUuid);
        // starring requires a readable KB (owner or public): without the check a private KB's
        // existence can be probed and its star count inflated by strangers
        if (!Boolean.TRUE.equals(user.getIsAdmin())
                && !knowledgeBase.getOwnerId().equals(user.getId())
                && !Boolean.TRUE.equals(knowledgeBase.getIsPublic())) {
            // NOT_FOUND (same as checkReadPrivilege): an auth error would disclose that the
            // private KB exists
            throw new BaseException(A_DATA_NOT_FOUND);
        }
        boolean star;
        KnowledgeBaseStar oldRecord = knowledgeBaseStarRecordService.getRecord(user.getId(), kbUuid);
        if (null == oldRecord) {
            KnowledgeBaseStar starRecord = new KnowledgeBaseStar();
            starRecord.setUserId(user.getId());
            starRecord.setUserUuid(user.getUuid());
            starRecord.setKbId(knowledgeBase.getId());
            starRecord.setKbUuid(kbUuid);
            knowledgeBaseStarRecordService.save(starRecord);

            star = true;
        } else {
            //Deleted means unstar
            knowledgeBaseStarRecordService.lambdaUpdate()
                    .eq(KnowledgeBaseStar::getId, oldRecord.getId())
                    .set(KnowledgeBaseStar::getIsDeleted, !oldRecord.getIsDeleted())
                    .update();
            star = oldRecord.getIsDeleted();
        }
        int starCount = star ? knowledgeBase.getStarCount() + 1 : knowledgeBase.getStarCount() - 1;
        ChainWrappers.lambdaUpdateChain(baseMapper)
                .eq(KnowledgeBase::getId, knowledgeBase.getId())
                .set(KnowledgeBase::getStarCount, starCount)
                .update();
        return star;
    }

    /**
     * 知识库问答限额判断
     */
    private void checkRequestTimesOrThrow() {
        String key = MessageFormat.format(RedisKeyConstant.AQ_ASK_TIMES, ThreadContext.getCurrentUserId(), LocalDateTimeUtil.format(LocalDateTime.now(), PATTERN_YYYY_MM_DD));
        String askTimes = stringRedisTemplate.opsForValue().get(key);
        String askQuota = SysConfigService.getByKey(QUOTA_BY_QA_ASK_DAILY);
        if (null != askQuota && null != askTimes && Integer.parseInt(askTimes) >= Integer.parseInt(askQuota)) {
            throw new BaseException(A_QA_ASK_LIMIT);
        }
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, Duration.ofDays(1));
    }

    /**
     * 文档召回并将请求发送给LLM
     *
     * @param user         当前提问的用户
     * @param sseUuid      SSE 请求标识 / SSE request identifier
     * @param qaRecordUuid 知识库uuid
     */
    @Async
    public void retrieveAndPushToLLM(User user, String sseUuid, String qaRecordUuid) {
        log.info("retrieveAndPushToLLM,qaRecordUuid:{},userId:{}", qaRecordUuid, user.getId());
        KnowledgeBaseQa qaRecord = knowledgeBaseQaRecordService.getOrThrow(qaRecordUuid);
        KnowledgeBase knowledgeBase = getOrThrow(qaRecord.getKbUuid());
        AiModel aiModel = aiModelService.getByIdOrThrow(qaRecord.getAiModelId());

        TokenEstimatorThreadLocal.setTokenEstimator(knowledgeBase.getIngestTokenEstimator());

        try {
            int maxInputTokens = aiModel.getMaxInputTokens();
            int maxResults = knowledgeBase.getRetrieveMaxResults();
//maxResults < 1 means the system auto-calculates based on model maxInputTokens
            //maxResults < 1 表示由系统根据设置的模型maxInputTokens自动计算大小
            //系统提示词实际会追加身份护栏，预留 token 必须按拼接后的结果算，否则会低估占用
            //The system message actually sent carries the identity guard appended; the reserved
            //token budget must be computed on the merged text, otherwise it under-estimates usage.
            String effectiveSystemMessage = AssistantIdentityGuard.append(knowledgeBase.getQuerySystemMessage(), knowledgeBase.getTitle());
            if (maxResults < 1) {
                String memoryId = qaRecord.getKbUuid() + "_" + user.getUuid();
                int reservedTokens = computeReservedTokens(memoryId, effectiveSystemMessage);
                maxResults = EmbeddingRag.getRetrieveMaxResults(qaRecord.getQuestion(), maxInputTokens, reservedTokens);
            }

            SseAskParam sseAskParam = new SseAskParam();
            sseAskParam.setUuid(qaRecord.getUuid());
            sseAskParam.setHttpRequestParams(
                    ChatModelRequest.builder()
                            .memoryId(qaRecord.getKbUuid() + "_" + user.getUuid())
                            .systemMessage(effectiveSystemMessage)
                            .userMessage(qaRecord.getQuestion())
                            .build()
            );
            sseAskParam.setModelProperties(
                    ChatModelBuilderProperties.builder()
                            .temperature(knowledgeBase.getQueryLlmTemperature())
                            .build()
            );
            sseAskParam.setSseUuid(sseUuid);
            sseAskParam.setModelName(aiModel.getName());
            // consumed by the QA LLM call record (model_platform column); was never set before
            sseAskParam.setModelPlatform(aiModel.getPlatform());
            sseAskParam.setUser(user);
            if (maxResults == 0) {
                log.info("User question too long, no need to retrieve docs; strict mode returns error, relaxed mode continues to LLM");
                if (Boolean.TRUE.equals(knowledgeBase.getIsStrict())) {
                    sseManager.sendErrorAndComplete(user.getId(), sseUuid, "Question too long, max " + maxInputTokens + " tokens");
                } else {
                    long llmStartTime = System.currentTimeMillis();
                    sseManager.call(sseAskParam, (response, questionMeta, answerMeta) -> {
                                answerMeta.setDuration(NumberUtil.saturatedCastToInt(System.currentTimeMillis() - llmStartTime));
                                sseManager.sendComplete(user.getId(), sseUuid, questionMeta, answerMeta, null);
                                updateQaRecord(
                                        UpdateQaParam.builder()
                                                .user(user)
                                                .qaRecord(qaRecord)
                                                .retrievers(null)
                                                .sseAskParam(sseAskParam)
                                                .response(response.getContent())
                                                .duration(answerMeta.getDuration())
                                                .isTokenFree(aiModel.getIsFree())
                                                .build());
                            }
                    );
                }
            } else {
                log.info("Performing RAG request, maxResults:{}", maxResults);
                ChatModel chatModel = LLMContext.getServiceById(knowledgeBase.getIngestModelId(), true).buildChatLLM(
                        ChatModelBuilderProperties.builder()
                                .temperature(knowledgeBase.getQueryLlmTemperature())
                                .build());
                Set<String> disabledDocUuids = new HashSet<>(kbDocumentService.listDisabledItemUuids(qaRecord.getKbUuid()));
                RetrieverCreateParam createParam = RetrieverCreateParam.builder()
                        .chatModel(chatModel)
                        .filter(new IsEqualTo(AdiConstant.MetadataKey.KB_UUID, qaRecord.getKbUuid()))
                        .maxResults(maxResults)
                        .minScore(knowledgeBase.getRetrieveMinScore())
                        .breakIfSearchMissed(knowledgeBase.getIsStrict())
                        .excludedItemUuids(disabledDocUuids)
                        .context(buildRetrieverContext(knowledgeBase, disabledDocUuids))
                        .kbUuid(qaRecord.getKbUuid())
                        .build();
                CompositeRag compositeRag = new CompositeRag(KNOWLEDGE_BASE);
                List<RetrieverWrapper> retrieverWrappers = compositeRag.createRetriever(createParam);
                List<ContentRetriever> retrievers = retrieverWrappers.stream().map(RetrieverWrapper::getRetriever).toList();
                long llmStartTime = System.currentTimeMillis();
                compositeRag.ragChat(retrievers, sseAskParam, (response, promptMeta, answerMeta) -> {
                            answerMeta.setDuration(NumberUtil.saturatedCastToInt(System.currentTimeMillis() - llmStartTime));
                            sseManager.sendComplete(user.getId(), sseAskParam.getSseUuid(), promptMeta, answerMeta, null);
                            updateQaRecord(
                                    UpdateQaParam.builder()
                                            .user(user)
                                            .qaRecord(qaRecord)
                                            .retrievers(retrievers)
                                            .sseAskParam(sseAskParam)
                                            .response(response)
                                            .duration(answerMeta.getDuration())
                                            .isTokenFree(aiModel.getIsFree())
                                            .build());
                        }
                );
            }
        } finally {
            TokenEstimatorThreadLocal.clearTokenEstimator();
        }
    }

    /**
     * Calculates the number of tokens to reserve from the retrieval budget (chat history + system message).
     * <p>The tokenizer is taken from {@link TokenEstimatorThreadLocal} so it stays consistent with the one used
     * elsewhere in the same request. Reading short-term memory is best-effort: on failure it is treated as 0
     * and does not affect the main flow.</p>
     */
    private static int computeReservedTokens(String memoryId, String systemMessage) {
        TokenCountEstimator tokenizer = TokenEstimatorFactory.create(TokenEstimatorThreadLocal.getTokenEstimator());
        int tokens = 0;
        try {
            List<ChatMessage> messages = ChatMemoryStores.get().getMessages(memoryId);
            if (messages != null && !messages.isEmpty()) {
                tokens += tokenizer.estimateTokenCountInMessages(messages);
            }
        } catch (Exception e) {
            log.warn("Failed to load chat memory for token estimation, memoryId:{}", memoryId, e);
        }
        if (StringUtils.isNotBlank(systemMessage)) {
            tokens += tokenizer.estimateTokenCountInText(systemMessage);
        }
        return tokens;
    }

    private void updateQaRecord(UpdateQaParam updateQaParam) {

        Pair<Integer, Integer> inputOutputTokenCost = LLMTokenUtil.calAllTokenCostByUuid(stringRedisTemplate, updateQaParam.getSseAskParam().getUuid());

        KnowledgeBaseQa qaRecord = updateQaParam.getQaRecord();
        User user = updateQaParam.getUser();

        KnowledgeBaseQa updateRecord = new KnowledgeBaseQa();
        updateRecord.setId(qaRecord.getId());
        updateRecord.setPrompt(updateQaParam.getSseAskParam().getHttpRequestParams().getUserMessage());
        updateRecord.setAnswer(updateQaParam.getResponse());
        knowledgeBaseQaRecordService.updateById(updateRecord);

        //Save LLM call record
        LLMCallRecord callRecord = new LLMCallRecord();
        callRecord.setUuid(UuidUtil.createShort());
        callRecord.setSourceType(LLMCallRecordSourceType.KNOWLEDGE_BASE_QA.getValue());
        callRecord.setSourceId(qaRecord.getId());
        callRecord.setUserId(user.getId());
        callRecord.setModelPlatform(updateQaParam.getSseAskParam().getModelPlatform());
        callRecord.setModelName(updateQaParam.getSseAskParam().getModelName());
        callRecord.setInputTokens(inputOutputTokenCost.getLeft());
        callRecord.setOutputTokens(inputOutputTokenCost.getRight());
        callRecord.setDuration(updateQaParam.getDuration());
        llmCallRecordService.saveAsync(callRecord);

        createRef(updateQaParam.getRetrievers(), user, qaRecord.getId());
        //用户本次请求消耗的token数指的是整个RAG过程中消耗的token数量，其中可能涉及到多次LLM请求
        int allToken = inputOutputTokenCost.getLeft() + inputOutputTokenCost.getRight();
        log.info("User {} total token consumption for this request: {}", user.getName(), allToken);
        if (allToken > 0) {
            userDayCostService.appendCostToUser(user, allToken, updateQaParam.isTokenFree());
        }
    }

    /**
     * 创建引用记录
     *
     * @param retrievers 召回器
     * @param user       用户
     * @param qaId       问答id
     */
    private void createRef(List<ContentRetriever> retrievers, User user, Long qaId) {
        if (CollectionUtils.isEmpty(retrievers)) {
            return;
        }
        Set<String> embeddingHitItemUuids = new HashSet<>();
        Set<String> graphHitItemUuids = new HashSet<>();
        for (ContentRetriever retriever : retrievers) {
            if (retriever instanceof AdiEmbeddingStoreContentRetriever embeddingRetriever) {
                Map<String, Double> embeddingToScore = embeddingRetriever.getRetrievedEmbeddingToScore();
                knowledgeBaseQaRecordService.createEmbeddingRefs(user, qaId, embeddingToScore);
                // Increment segment-level hit count (text segment / question / child chunk, with propagation to answers/parents)
                List<String> embeddingIds = new ArrayList<>(embeddingToScore.keySet());
                documentSegmentService.incrementHitCounts(embeddingIds);
                // Collect document UUIDs for document-level embedding hit count (from the relational segment tables)
                embeddingHitItemUuids.addAll(documentSegmentService.selectDocUuidsByEmbeddingIds(embeddingIds));
            } else if (retriever instanceof GraphStoreContentRetriever graphRetriever) {
                RefGraphDto graphDto = graphRetriever.getGraphRef();
                knowledgeBaseQaRecordService.createGraphRefs(user, qaId, graphDto);
                // Collect document UUIDs from graph vertices metadata (may be comma-separated)
                if (graphDto.getVertices() != null) {
                    for (GraphVertex vertex : graphDto.getVertices()) {
                        if (vertex.getMetadata() != null) {
                            Object raw = vertex.getMetadata().get(AdiConstant.MetadataKey.KB_ITEM_UUID);
                            if (raw != null && StringUtils.isNotBlank(String.valueOf(raw)) && !"null".equals(String.valueOf(raw))) {
                                graphHitItemUuids.addAll(Arrays.stream(String.valueOf(raw).split(","))
                                        .map(String::trim)
                                        .filter(StringUtils::isNotBlank)
                                        .collect(Collectors.toSet()));
                            }
                        }
                    }
                }
            }
        }
        // Increment document-level hit counts (embedding and graph tracked independently)
        if (!embeddingHitItemUuids.isEmpty()) {
            kbDocumentService.incrementEmbeddingHitCount(new ArrayList<>(embeddingHitItemUuids));
        }
        if (!graphHitItemUuids.isEmpty()) {
            kbDocumentService.incrementGraphHitCount(new ArrayList<>(graphHitItemUuids));
        }
    }

    public KnowledgeBase getOrThrow(String kbUuid) {
        return ChainWrappers.lambdaQueryChain(baseMapper)
                .eq(KnowledgeBase::getUuid, kbUuid)
                .eq(KnowledgeBase::getIsDeleted, false)
                .oneOpt().orElseThrow(() -> new BaseException(A_DATA_NOT_FOUND));
    }

    /**
     * Resolve a knowledge base only after checking whether the current user may read it.
     * Keeping the lookup and authorization together prevents callers from accidentally
     * resolving a private knowledge base before applying the read check.
     */
    public KnowledgeBase getReadableOrThrow(String kbUuid) {
        KnowledgeBase knowledgeBase = getOrThrow(kbUuid);
        ensureReadPrivilege(knowledgeBase);
        return knowledgeBase;
    }

    /**
     * Resolve a QA record and authorize access through its owning knowledge base.
     * QA UUIDs are otherwise sufficient to reach private document references and answers.
     */
    public KnowledgeBaseQa getReadableQaOrThrow(String qaRecordUuid) {
        KnowledgeBaseQa qaRecord = knowledgeBaseQaRecordService.getOrThrow(qaRecordUuid);
        getReadableOrThrow(qaRecord.getKbUuid());
        return qaRecord;
    }

    /**
     * Read authorization for a knowledge base's content: allow the owner, an admin,
     * or anyone when the knowledge base is public. This mirrors the (owner-only)
     * write-side checkWritePrivilege so that the read endpoints are scoped too. Denials
     * are reported as A_DATA_NOT_FOUND to avoid leaking the existence of other
     * users' private knowledge bases.
     */
    public void checkReadPrivilege(String kbUuid) {
        ensureReadPrivilege(getOrThrow(kbUuid));
    }

    private void ensureReadPrivilege(KnowledgeBase kb) {
        if (Boolean.TRUE.equals(kb.getIsPublic())) {
            return;
        }
        User user = ThreadContext.getCurrentUser();
        if (null != user && (Boolean.TRUE.equals(user.getIsAdmin()) || user.getId().equals(kb.getOwnerId()))) {
            return;
        }
        // 共享授权：查询失败（如 018 迁移尚未执行、或数据库瞬时异常）时降级为「无共享授权」，
        // 避免每次读库都 500；迁移执行后自然生效。写权限检查（shareGrant 等）不在此降级范围内。
        boolean shared = false;
        try {
            shared = kbShareService.hasAccess(kb.getUuid(), user == null ? null : user.getId());
        } catch (Exception e) {
            log.warn("kb share access check failed,kbUuid:{}", kb.getUuid(), e);
        }
        if (shared) {
            return;
        }
        throw new BaseException(A_DATA_NOT_FOUND);
    }

    /**
     * Set update knowledge base stat signal
     *
     * @param kbUuid 知识库uuid
     */
    public void updateStatistic(String kbUuid) {
        stringRedisTemplate.opsForSet().add(KB_STATISTIC_RECALCULATE_SIGNAL, kbUuid);
    }

    public int countTodayCreated() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime beginTime = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), 0, 0, 0);
        LocalDateTime endTime = beginTime.plusDays(1);
        return baseMapper.countCreatedByTimePeriod(beginTime, endTime);
    }

    public int countAllCreated() {
        return baseMapper.countAllCreated();
    }

    /**
     * Update knowledge base stat
     */
    public void updateStatistic() {
        try {
            Set<String> kbUuidList = stringRedisTemplate.opsForSet().members(KB_STATISTIC_RECALCULATE_SIGNAL);
            if (CollectionUtils.isEmpty(kbUuidList)) {
                return;
            }
            for (String kbUuid : kbUuidList) {
                try {
                    int embeddingCount = documentSegmentService.countVectorizedByKbUuid(kbUuid);
                    baseMapper.updateStatByUuid(kbUuid, embeddingCount);
                } catch (Exception e) {
                    log.error("Failed to update knowledge base statistics, kbUuid:{}", kbUuid, e);
                } finally {
                    stringRedisTemplate.opsForSet().remove(KB_STATISTIC_RECALCULATE_SIGNAL, kbUuid);
                }
            }
        } catch (Exception e) {
            log.error("updateStatistic execution exception", e);
        }
    }

    private void checkWritePrivilege(Long kbId, String kbUuid) {
        if (null == kbId && StringUtils.isBlank(kbUuid)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        User user = ThreadContext.getCurrentUser();
        if (null == user) {
            throw new BaseException(A_USER_NOT_EXIST);
        }
        boolean privilege = user.getIsAdmin();
        if (privilege) {
            return;
        }
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getOwnerId, user.getId());
        if (null != kbId) {
            wrapper = wrapper.eq(KnowledgeBase::getId, kbId);
        } else if (StringUtils.isNotBlank(kbUuid)) {
            wrapper = wrapper.eq(KnowledgeBase::getUuid, kbUuid);
        }
        boolean exists = baseMapper.exists(wrapper);
        if (!exists) {
            throw new BaseException(A_USER_NOT_AUTH);
        }
    }

    /**
     * 二开新增（T7-05，2026-10-20）：按知识库全量重建 embedding 索引，并把本次使用的
     * provider 版本记录到知识库（换 embedding provider 的标准动作 + 回滚链路凭据）。
     * <p>
     * 背景：向量表按「模型 + 维度」分表（{@code adi_knowledge_base_embedding_<suffix>}），
     * 切换 {@code adi.embedding-model} 后新表为空、旧表成孤儿——问答不报错，只是永远
     * 召不回内容（严格模式一律拒答，普通模式纯幻觉）。唯一正确的动作是<b>全量重建</b>：
     * 本方法失效该库全部文档的段行与向量（含 QA 文档）后按当前 provider 重新入队，
     * 并把新旧 provider 版本写进审计日志。
     * <p>
     * 回滚链路：把 {@code adi.embedding-model} 切回 previous* 对应的配置 → 重启 →
     * 再调一次本方法。两次调用的审计记录（KB_REINDEX）完整还原「谁在何时从什么切到了什么」。
     * <p>
     * 开关：{@code adi.kb.embedding-rebuild-enabled=false} 时端点拒绝（A_KB_EMBEDDING_REBUILD_DISABLED）；
     * 关闭不影响任何自动索引链路。
     *
     * @param kbUuid 知识库 uuid
     * @return 重建结果（含回滚所需的新旧 provider 版本）
     */
    public KbEmbeddingRebuildResp rebuildEmbedding(String kbUuid) {
        if (StringUtils.isBlank(kbUuid)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        if (!adiProperties.getKb().isEmbeddingRebuildEnabled()) {
            throw new BaseException(A_KB_EMBEDDING_REBUILD_DISABLED);
        }
        KnowledgeBase kb = getOrThrow(kbUuid);
        // 写权限：管理员或库 owner（与保存/删除同一口径，共享只读用户不能触发重建）
        checkWritePrivilege(kb.getId(), null);
        EmbeddingProviderInfo provider = embeddingProviderService.currentProvider();
        String previousProvider = StringUtils.defaultString(kb.getEmbeddingProvider());
        String previousVersion = StringUtils.defaultString(kb.getEmbeddingProviderVersion());
        // 先失效入队、后记录版本：入队失败（如参数错误）时不留"已记录但未重建"的假象
        int docCount = kbDocumentService.rebuildEmbeddingByKb(kbUuid);
        kb.setEmbeddingProvider(provider.name());
        kb.setEmbeddingProviderVersion(provider.version());
        baseMapper.updateById(kb);
        stringRedisTemplate.opsForSet().add(KB_STATISTIC_RECALCULATE_SIGNAL, kbUuid);
        kbAuditLogService.record(kbUuid, kb.getTitle(), KbAuditLogService.ACTION_KB_REINDEX,
                KbAuditLogService.TARGET_KB, kbUuid, true,
                "provider:" + previousVersion + " -> " + provider.version() + ",docs:" + docCount);
        log.info("rebuildEmbedding, kbUuid:{}, provider:{} -> {}, docs:{}",
                kbUuid, previousVersion, provider.version(), docCount);
        return new KbEmbeddingRebuildResp(previousProvider, previousVersion,
                provider.name(), provider.version(), docCount);
    }

    /**
     * T7-05：把当前生效的 embedding provider 记录到知识库（创建路径）。
     * 旁路逻辑：provider 服务缺失或解析失败都只跳过并留痕，不阻断创建主流程。
     */
    private void recordCurrentProvider(KnowledgeBase kb) {
        if (null == embeddingProviderService) {
            return;
        }
        try {
            EmbeddingProviderInfo provider = embeddingProviderService.currentProvider();
            kb.setEmbeddingProvider(provider.name());
            kb.setEmbeddingProviderVersion(provider.version());
        } catch (Exception e) {
            log.warn("Record embedding provider skipped on kb create, reason: {}", e.getMessage());
        }
    }

    /**
     * 二开新增（2026-09-15）：重建某个知识库的关键词索引（写 search_text，search_tsv 由触发器维护）。
     * <p>
     * 用途：017 迁移只加列不回填历史数据，存量分段必须重建一次才能被关键词路召回。
     * 切分逻辑只此一处（Java 侧 {@link BigramTokenizer}），与入库写入保持同一口径，避免两套实现漂移。
     *
     * @param kbUuid 知识库 uuid
     * @return 已重建的分段条数
     */
    public int rebuildKeywordIndex(String kbUuid) {
        if (StringUtils.isBlank(kbUuid)) {
            throw new BaseException(A_PARAMS_ERROR);
        }
        int pageSize = 500;
        long lastId = 0;
        int total = 0;
        while (true) {
            LambdaQueryWrapper<DocumentSegment> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(DocumentSegment::getKbUuid, kbUuid)
                    .gt(DocumentSegment::getId, lastId)
                    .orderByAsc(DocumentSegment::getId)
                    .last("limit " + pageSize);
            List<DocumentSegment> batch = documentSegmentService.list(wrapper);
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (DocumentSegment segment : batch) {
                segment.setSearchText(BigramTokenizer.tokenize(segment.getContent()));
            }
            documentSegmentService.updateBatchById(batch);
            total += batch.size();
            lastId = batch.get(batch.size() - 1).getId();
        }
        log.info("rebuild keyword index done,kbUuid:{},segments:{}", kbUuid, total);
        return total;
    }

    /**
     * 二开新增（2026-09-15）：把知识库上的检索增强配置转换为检索链路使用的上下文。
     * <p>
     * 字段为 null 时取保守默认值（关闭增强 / RRF k=60 / 关键词 20 条 / 不截断 / 不过滤），
     * 保证迁移脚本执行前的老数据与未配置的知识库行为完全不变。
     */
    public RetrieverContext buildRetrieverContext(KnowledgeBase knowledgeBase, Set<String> excludedDocUuids) {
        if (knowledgeBase == null) {
            return RetrieverContext.none();
        }
        return RetrieverContext.builder()
                .kbUuid(knowledgeBase.getUuid())
                .hybridEnabled(Boolean.TRUE.equals(knowledgeBase.getRetrieveHybridEnabled()))
                .rrfK(knowledgeBase.getRetrieveRrfK() == null ? RrfFusion.DEFAULT_K : knowledgeBase.getRetrieveRrfK())
                .keywordLimit(knowledgeBase.getRetrieveKeywordLimit() == null ? 20 : knowledgeBase.getRetrieveKeywordLimit())
                .rerankEnabled(Boolean.TRUE.equals(knowledgeBase.getRetrieveRerankEnabled()))
                .rerankModelId(knowledgeBase.getRetrieveRerankModelId())
                .rerankTopN(knowledgeBase.getRetrieveRerankTopN() == null ? 0 : knowledgeBase.getRetrieveRerankTopN())
                .rerankMinScore(knowledgeBase.getRetrieveRerankMinScore() == null ? 0.0 : knowledgeBase.getRetrieveRerankMinScore())
                //Same list the vector path filters on: without it the keyword path would retrieve
                //segments of documents the customer explicitly disabled
                .excludedDocUuids(null == excludedDocUuids ? Set.of() : excludedDocUuids)
                .neighborEnabled(Boolean.TRUE.equals(knowledgeBase.getRetrieveNeighborEnabled()))
                //null (column added by migration 026 but not yet applied) must not silently disable
                //the window when the operator did turn the switch on -> fall back to the tested default
                .neighborWindow(null == knowledgeBase.getRetrieveNeighborWindow()
                        ? RetrieverContext.DEFAULT_NEIGHBOR_WINDOW : knowledgeBase.getRetrieveNeighborWindow())
                .build();
    }

}
