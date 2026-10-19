package com.moyz.adi.chat.controller.externalapi.v1;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.KbQaDto;
import com.moyz.adi.common.dto.QARecordReq;
import com.moyz.adi.common.dto.extapi.ExtApiKbQaReq;
import com.moyz.adi.common.dto.extapi.ExtApiKbRetrieveReq;
import com.moyz.adi.common.dto.extapi.ExtKbRetrieveResp;
import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.AiModelService;
import com.moyz.adi.common.service.ExtKnowledgeSearchService;
import com.moyz.adi.common.service.KnowledgeBaseQaService;
import com.moyz.adi.common.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.moyz.adi.common.enums.ErrorEnum.A_MODEL_NOT_AVAILABLE;

@Tag(name = "External API - Knowledge Base")
@RestController
@RequestMapping("/ext/v1/knowledge")
@Validated
public class ExtKnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KnowledgeBaseQaService knowledgeBaseQaService;

    @Resource
    private AiModelService aiModelService;

    @Resource
    private ExtKnowledgeSearchService extKnowledgeSearchService;

    @Operation(summary = "Knowledge base Q&A")
    @PostMapping
    public Object knowledgeBaseQa(@RequestBody @Validated ExtApiKbQaReq req) {
        User user = ThreadContext.getCurrentUser();
        String kbUuid = ThreadContext.getExtApiEntityUuid();

        KnowledgeBase knowledgeBase = knowledgeBaseService.getOrThrow(kbUuid);

        QARecordReq qaRecordReq = new QARecordReq();
        qaRecordReq.setQuestion(req.getQuery());

        if (StringUtils.isNotBlank(req.getModel())) {
            AiModel aiModel = aiModelService.getByName(req.getModel());
            if (null == aiModel || !aiModel.getIsEnable()) {
                throw new BaseException(A_MODEL_NOT_AVAILABLE);
            }
            qaRecordReq.setModelName(req.getModel());
        }

        KbQaDto qaDto = knowledgeBaseQaService.add(knowledgeBase, qaRecordReq);

        if ("blocking".equalsIgnoreCase(req.getResponseMode())) {
            return knowledgeBaseService.blockingAsk(user, knowledgeBase, qaDto);
        }

        return knowledgeBaseService.sseAsk(qaDto.getUuid());
    }

    /**
     * 纯检索不调 LLM（T1-08，2026-10-19）。
     * <p>
     * 多库：kbUuids 数组；无权或库不存在 → 静默跳过进 skippedKbUuids（不报错）。
     * 检索走 {@link ExtKnowledgeSearchService} → EmbeddingRag.createRetriever →
     * AdiEmbeddingStoreContentRetriever（全仓库唯一底层检索入口），与问答/工作流同内核；
     * 不旁路另写 embeddingStore.search。
     * <p>
     * 返回 items（content/score/docUuid/kbUuid/白名单 metadata），不含任何生成内容。
     * 限流与配额（T1-09）由 ExtApiRateLimitFilter 前置生效。
     */
    @Operation(summary = "Pure retrieval without LLM | 纯检索（不调用 LLM，无生成内容）")
    @PostMapping("/retrieve")
    public ExtKbRetrieveResp retrieve(@RequestBody @Validated ExtApiKbRetrieveReq req) {
        User user = ThreadContext.getCurrentUser();
        return extKnowledgeSearchService.retrieve(user, req.getKbUuids(), req.getQuery(), req.getTopK());
    }
}
