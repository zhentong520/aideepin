package com.moyz.adi.common.service;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.extapi.ExtKbRetrieveResp;
import com.moyz.adi.common.entity.KbDocument;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.rag.KbAclFilterBuilder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_DATA_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;
import static com.moyz.adi.common.enums.ErrorEnum.A_USER_NOT_AUTH;

/**
 * KB → MCP Server 的工具实现（T1-10，2026-10-19）。
 * <p>
 * 供 {@code com.moyz.adi.chat.controller.McpKbController}（{@code /mcp/kb/{kbUuid}}）调用：
 * <ul>
 *   <li>{@link #kbSearch}：检索工具 kb_search——<b>复用 {@link ExtKnowledgeSearchService}
 *       （进而复用 EmbeddingRag.createRetriever → AdiEmbeddingStoreContentRetriever 内核）</b>，
 *       与 T1-08 / 问答 / 工作流同一条检索链路，不旁路；</li>
 *   <li>{@link #kbGetDocument}：取文档原文，校验文档归属与主体权限。</li>
 * </ul>
 * <b>权限语义与 T1-08 的 skipped 区分</b>（任务书 T1-10 验收）：MCP 场景 key 在 URL 里
 * <b>显式点名</b>了一个库，无权/不存在 → 抛明确错误（JSON-RPC error），不是静默空结果。
 * 访问控制 = 复用 /ext/v1/knowledge 的 knowledge 资源级 ext key：key 绑定唯一知识库
 * （{@link ThreadContext#getExtApiEntityUuid()}），路径 kbUuid 必须一致。
 */
@Slf4j
@Service
public class KbMcpToolService {

    @Resource
    private ExtKnowledgeSearchService extKnowledgeSearchService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    @Resource
    private KbDocumentService kbDocumentService;

    /**
     * kb_search：单库纯检索（不调 LLM），返回与 T1-08 完全一致的结果结构（同内核）。
     */
    public List<ExtKbRetrieveResp.Item> kbSearch(User user, String kbUuid, String query, Integer topK) {
        checkAccess(user, kbUuid);
        if (StringUtils.isBlank(query)) {
            throw new BaseException(A_PARAMS_ERROR.getCode(), "query is required");
        }
        ExtKbRetrieveResp resp = extKnowledgeSearchService.retrieve(user, List.of(kbUuid), query, topK);
        if (null != resp.getSkippedKbUuids() && !resp.getSkippedKbUuids().isEmpty()) {
            // T1-10 语义区分：MCP 显式点名库，无权/不存在必须报明确错误，而非静默空结果。
            // 用 (code, info) 构造器携带自定义文案（ErrorEnum 的 info 无 {0} 占位时会被丢弃）
            throw new BaseException(A_USER_NOT_AUTH.getCode(),
                    "kb not accessible: " + kbUuid + " (not found or permission denied)");
        }
        return resp.getItems();
    }

    /**
     * kb_get_document：按 docUuid 取文档原文（清洗前 rawText 优先，缺失回退 remark）。
     */
    public Map<String, Object> kbGetDocument(User user, String kbUuid, String docUuid) {
        checkAccess(user, kbUuid);
        if (StringUtils.isBlank(docUuid)) {
            throw new BaseException(A_PARAMS_ERROR.getCode(), "doc_uuid is required");
        }
        KnowledgeBase kb = knowledgeBaseService.getOrThrow(kbUuid);
        if (!KbAclFilterBuilder.toAclInfo(kb).visibleTo(
                user.getUuid(), Boolean.TRUE.equals(user.getIsAdmin()), null)) {
            throw new BaseException(A_USER_NOT_AUTH.getCode(), "kb not accessible: " + kbUuid);
        }
        KbDocument doc = kbDocumentService.getEnable(docUuid);
        if (null == doc || !kbUuid.equals(doc.getKbUuid())) {
            // 文档不属于该库：同样明确报错，避免借 docUuid 跨库探取
            throw new BaseException(A_DATA_NOT_FOUND.getCode(),
                    "document not found in kb " + kbUuid + ": " + docUuid);
        }
        String content = StringUtils.isNotBlank(doc.getRawText()) ? doc.getRawText() : doc.getRemark();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("uuid", doc.getUuid());
        result.put("kb_uuid", doc.getKbUuid());
        result.put("title", doc.getTitle());
        result.put("content", content);
        return result;
    }

    /**
     * 访问控制：主体存在 + ext key 绑定库与路径 kbUuid 一致。
     * 鉴权链路完全复用 /ext/v1/knowledge（ExtApiAuthFilter → ExtApiService.validateApiKey）。
     */
    private void checkAccess(User user, String kbUuid) {
        if (null == user) {
            throw new BaseException(A_USER_NOT_AUTH);
        }
        String boundKbUuid = ThreadContext.getExtApiEntityUuid();
        if (StringUtils.isBlank(boundKbUuid) || !kbUuid.equals(boundKbUuid)) {
            log.warn("MCP kb access denied, path kbUuid:{}, key bound kbUuid:{}", kbUuid, boundKbUuid);
            throw new BaseException(A_USER_NOT_AUTH.getCode(),
                    "ext key is not bound to kb " + kbUuid + " (bound: " + boundKbUuid + ")");
        }
    }
}
