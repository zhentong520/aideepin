package com.moyz.adi.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.KbMcpToolService;
import com.moyz.adi.common.util.JsonUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_PARAMS_ERROR;

/**
 * KB → MCP Server（T1-10，2026-10-19）：把单个知识库以 MCP 工具集形态暴露。
 * <p>
 * 端点：{@code POST /mcp/kb/{kbUuid}}，MCP Streamable HTTP 传输（JSON-RPC 2.0，
 * 单响应 application/json；不支持 server-initiated SSE，GET 返回 405，符合规范，
 * 官方 python SDK / 任意 Streamable HTTP 客户端可直接接入）。
 * <p>
 * 工具集：
 * <ul>
 *   <li>{@code kb_search}：检索（复用 {@link KbMcpToolService} → {@link com.moyz.adi.common.service.ExtKnowledgeSearchService}
 *       → EmbeddingRag.createRetriever → AdiEmbeddingStoreContentRetriever 内核，与 T1-08 同链路）；</li>
 *   <li>{@code kb_get_document}：按 docUuid 取文档原文。</li>
 * </ul>
 * 鉴权：复用 /ext/v1/knowledge 的 knowledge 资源级 ext key（{@code Authorization: ext-...}，
 * 由 ExtApiAuthFilter 校验，key 绑定唯一知识库）。无权/不存在的库 → 明确 JSON-RPC 错误
 * （code -32000），与 T1-08 的「静默跳过」语义刻意区分（MCP 场景 key 显式点名了库）。
 * <p>
 * 限流：/mcp/kb/** 与 /ext/v1/** 共用 ExtApiRateLimitFilter（T1-09），超限 HTTP 429。
 */
@Slf4j
@Tag(name = "MCP - Knowledge Base")
@RestController
@RequestMapping("/mcp/kb/{kbUuid}")
public class McpKbController {

    /** 本服务兜底的协议版本（客户端 initialize 时回显客户端版本，保证兼容） */
    private static final String DEFAULT_PROTOCOL_VERSION = "2025-03-26";

    private static final String SERVER_NAME = "adi-kb-mcp";
    private static final String SERVER_VERSION = "1.0.0";

    private static final String TOOL_KB_SEARCH = "kb_search";
    private static final String TOOL_KB_GET_DOCUMENT = "kb_get_document";

    @Resource
    private KbMcpToolService kbMcpToolService;

    @Operation(summary = "MCP Streamable HTTP endpoint for one knowledge base")
    @PostMapping
    public ResponseEntity<Map<String, Object>> handle(@PathVariable String kbUuid,
                                                      @RequestBody(required = false) JsonNode body) {
        if (null == body || !body.isObject()) {
            return ResponseEntity.badRequest().body(error(null, -32700, "Parse error: JSON-RPC object required"));
        }
        String method = body.path("method").asText(null);
        JsonNode id = body.get("id");
        boolean isNotification = !body.hasNonNull("id");
        try {
            if (null == method) {
                return ResponseEntity.badRequest().body(error(id, -32600, "Invalid Request: missing method"));
            }
            return switch (method) {
                case "initialize" -> ResponseEntity.ok()
                        .header("Mcp-Session-Id", java.util.UUID.randomUUID().toString())
                        .body(result(id, initializeResult(body)));
                case "notifications/initialized" -> ResponseEntity.accepted().build();
                case "ping" -> ResponseEntity.ok(result(id, Map.of()));
                case "tools/list" -> ResponseEntity.ok(result(id, Map.of("tools", toolDefinitions())));
                case "tools/call" -> ResponseEntity.ok(result(id, dispatchTool(kbUuid, body)));
                default -> ResponseEntity.ok(error(id, -32601, "Method not found: " + method));
            };
        } catch (BaseException e) {
            // 业务错误（无权 / 不存在 / 参数）→ 明确 JSON-RPC 错误，绝不静默空结果
            log.info("MCP kb tool business error, kbUuid:{}, msg:{}", kbUuid, e.getMessage());
            return ResponseEntity.ok(error(id, -32000, e.getMessage()));
        } catch (Exception e) {
            log.error("MCP kb tool internal error, kbUuid:{}", kbUuid, e);
            return ResponseEntity.ok(error(id, -32603, "Internal error"));
        }
    }

    @Operation(summary = "SSE stream not supported (Streamable HTTP single-response mode)")
    @GetMapping
    public ResponseEntity<Void> streamNotSupported() {
        // MCP Streamable HTTP 规范：服务器未实现 server-initiated SSE 时对 GET 返回 405，
        // 客户端（官方 python SDK）据此降级为纯 POST，连接照常工作
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).build();
    }

    // ------------------------------------------------------------------
    // tools
    // ------------------------------------------------------------------

    private Map<String, Object> dispatchTool(String kbUuid, JsonNode body) {
        String name = body.path("params").path("name").asText("");
        JsonNode args = body.path("params").path("arguments");
        User user = ThreadContext.getCurrentUser();
        return switch (name) {
            case TOOL_KB_SEARCH -> {
                String query = args.path("query").asText(null);
                Integer topK = readTopK(args);
                List<?> items = kbMcpToolService.kbSearch(user, kbUuid, query, topK);
                yield textContent(JsonUtil.toJson(items), false);
            }
            case TOOL_KB_GET_DOCUMENT -> {
                String docUuid = args.path("doc_uuid").asText(null);
                if (StringUtils.isBlank(docUuid)) {
                    docUuid = args.path("docUuid").asText(null);
                }
                Map<String, Object> doc = kbMcpToolService.kbGetDocument(user, kbUuid, docUuid);
                yield textContent(JsonUtil.toJson(doc), false);
            }
            default -> throw new BaseException(A_PARAMS_ERROR.getCode(), "Unknown tool: " + name);
        };
    }

    private Map<String, Object> initializeResult(JsonNode body) {
        Map<String, Object> result = new LinkedHashMap<>();
        String clientVersion = body.path("params").path("protocolVersion").asText(null);
        result.put("protocolVersion", StringUtils.isNotBlank(clientVersion) ? clientVersion : DEFAULT_PROTOCOL_VERSION);
        result.put("capabilities", Map.of("tools", Map.of()));
        result.put("serverInfo", Map.of("name", SERVER_NAME, "version", SERVER_VERSION));
        result.put("instructions", "Knowledge base MCP server bound to a single KB. "
                + "Tools: kb_search (query[, top_k]), kb_get_document (doc_uuid). "
                + "Access is limited to the knowledge base the ext key is bound to.");
        return result;
    }

    private List<Map<String, Object>> toolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        Map<String, Object> searchSchema = new LinkedHashMap<>();
        searchSchema.put("type", "object");
        searchSchema.put("properties", Map.of(
                "query", Map.of("type", "string", "description", "Search question, required"),
                "top_k", Map.of("type", "integer", "description", "Max results per search, optional (server caps it)")));
        searchSchema.put("required", List.of("query"));
        tools.add(Map.of(
                "name", TOOL_KB_SEARCH,
                "description", "Retrieve relevant passages from the knowledge base (no LLM involved)",
                "inputSchema", searchSchema));

        Map<String, Object> docSchema = new LinkedHashMap<>();
        docSchema.put("type", "object");
        docSchema.put("properties", Map.of(
                "doc_uuid", Map.of("type", "string", "description", "Document uuid, required")));
        docSchema.put("required", List.of("doc_uuid"));
        tools.add(Map.of(
                "name", TOOL_KB_GET_DOCUMENT,
                "description", "Fetch the original text of one document in the knowledge base by uuid",
                "inputSchema", docSchema));
        return tools;
    }

    // ------------------------------------------------------------------
    // JSON-RPC envelopes
    // ------------------------------------------------------------------

    private Map<String, Object> result(JsonNode id, Object value) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        if (null != id) {
            envelope.put("id", id);
        }
        envelope.put("result", value);
        return envelope;
    }

    private Map<String, Object> error(JsonNode id, int code, String message) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("jsonrpc", "2.0");
        if (null != id) {
            envelope.put("id", id);
        }
        envelope.put("error", Map.of("code", code, "message", message));
        return envelope;
    }

    private Map<String, Object> textContent(String text, boolean isError) {
        Map<String, Object> content = Map.of("type", "text", "text", text);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", List.of(content));
        result.put("isError", isError);
        return result;
    }

    private Integer readTopK(JsonNode args) {
        JsonNode node = args.get("top_k");
        if (null == node || node.isNull()) {
            node = args.get("topK");
        }
        return null != node && node.isNumber() ? node.asInt() : null;
    }
}
