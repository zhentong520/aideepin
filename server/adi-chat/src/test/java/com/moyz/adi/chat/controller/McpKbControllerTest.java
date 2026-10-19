package com.moyz.adi.chat.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.dto.extapi.ExtKbRetrieveResp;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.KbMcpToolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * McpKbController 的 JSON-RPC 层测试（T1-10）。
 * <p>
 * 直接调 {@code handle()}（鉴权由 ExtApiAuthFilter 负责，不在本测试范围），
 * 覆盖 MCP Streamable HTTP 客户端会用到的全部方法：
 * initialize / notifications/initialized / tools/list / tools/call / 未知方法 / GET 405。
 * tools/call 的成功与「无权库明确报错」用例与 Python 冒烟脚本（mcp-smoke/mcp_smoke.py）一一对应。
 */
@ExtendWith(MockitoExtension.class)
class McpKbControllerTest {

    private static final String KB_UUID = "kb-1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private KbMcpToolService kbMcpToolService;

    private McpKbController controller;

    @BeforeEach
    void setUp() {
        controller = new McpKbController();
        ReflectionTestUtils.setField(controller, "kbMcpToolService", kbMcpToolService);
        // 模拟 ExtApiAuthFilter 完成鉴权后的 ThreadContext
        User user = new User();
        user.setUuid("key-owner");
        user.setIsAdmin(false);
        ThreadContext.setCurrentUser(user);
        ThreadContext.setExtApiContext(true, KB_UUID, "knowledge");
    }

    @AfterEach
    void tearDown() {
        ThreadContext.unload();
    }

    @DisplayName("initialize：返回协议版本与 tools 能力，客户端版本原样回显")
    @Test
    void initializeEchoesProtocolVersion() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":0,"method":"initialize",
                 "params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"t","version":"0"}}}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);

        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getHeaders().getFirst("Mcp-Session-Id"), "initialize 必须返回会话头");
        JsonNode result = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("result");
        assertEquals("2025-06-18", result.path("protocolVersion").asText());
        assertTrue(result.path("capabilities").has("tools"));
        assertEquals("adi-kb-mcp", result.path("serverInfo").path("name").asText());
    }

    @DisplayName("notifications/initialized：202 无响应体（通知不回 JSON-RPC）")
    @Test
    void initializedNotificationAccepted() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);
        assertEquals(202, resp.getStatusCode().value());
    }

    @DisplayName("tools/list：暴露 kb_search 与 kb_get_document 两个工具及入参 schema")
    @Test
    void toolsListContainsBothTools() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list"}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);

        JsonNode result = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("result");
        JsonNode tools = result.path("tools");
        assertEquals(2, tools.size());
        assertEquals("kb_search", tools.get(0).path("name").asText());
        assertEquals("kb_get_document", tools.get(1).path("name").asText());
        assertTrue(tools.get(0).path("inputSchema").path("properties").has("query"));
        assertTrue(tools.get(1).path("inputSchema").path("properties").has("doc_uuid"));
    }

    @DisplayName("tools/call kb_search：成功返回 text content 且内容为检索结果 JSON")
    @Test
    void toolsCallKbSearchReturnsContent() throws Exception {
        when(kbMcpToolService.kbSearch(any(User.class), eq(KB_UUID), eq("what is adi"), eq(5)))
                .thenReturn(List.of(ExtKbRetrieveResp.Item.builder()
                        .content("some passage").score(0.88).kbUuid(KB_UUID).build()));
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"kb_search","arguments":{"query":"what is adi","top_k":5}}}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);

        JsonNode result = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("result");
        assertFalse(result.path("isError").asBoolean(), "成功调用 isError 必须为 false");
        JsonNode content = result.path("content").get(0);
        assertEquals("text", content.path("type").asText());
        JsonNode items = MAPPER.readTree(content.path("text").asText());
        assertEquals(1, items.size());
        assertEquals("some passage", items.get(0).path("content").asText());
    }

    @DisplayName("tools/call kb_search：无权库 → JSON-RPC 明确错误（不是静默空结果）")
    @Test
    void toolsCallDeniedKbIsExplicitError() throws Exception {
        when(kbMcpToolService.kbSearch(any(User.class), eq(KB_UUID), any(), any()))
                .thenThrow(new BaseException(com.moyz.adi.common.enums.ErrorEnum.A_USER_NOT_AUTH.getCode(),
                        "kb not accessible: " + KB_UUID));
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call",
                 "params":{"name":"kb_search","arguments":{"query":"q"}}}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);

        JsonNode errorNode = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("error");
        assertTrue(errorNode.has("code"), "无权必须返回 JSON-RPC error 对象");
        assertEquals(-32000, errorNode.path("code").asInt());
        assertTrue(errorNode.path("message").asText().contains("not accessible"));
    }

    @DisplayName("tools/call kb_get_document：返回文档原文 JSON 文本")
    @Test
    void toolsCallGetDocument() throws Exception {
        when(kbMcpToolService.kbGetDocument(any(User.class), eq(KB_UUID), eq("doc-1")))
                .thenReturn(Map.of("uuid", "doc-1", "kb_uuid", KB_UUID, "title", "t", "content", "raw"));
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call",
                 "params":{"name":"kb_get_document","arguments":{"doc_uuid":"doc-1"}}}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);

        JsonNode result = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("result");
        JsonNode doc = MAPPER.readTree(result.path("content").get(0).path("text").asText());
        assertEquals("raw", doc.path("content").asText());
    }

    @DisplayName("未知方法 → -32601 Method not found")
    @Test
    void unknownMethodIsJsonRpcError() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":6,"method":"resources/list"}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);
        JsonNode errorNode = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("error");
        assertEquals(-32601, errorNode.path("code").asInt());
    }

    @DisplayName("未知工具 → -32000 错误并指明工具名")
    @Test
    void unknownToolIsJsonRpcError() throws Exception {
        JsonNode body = MAPPER.readTree("""
                {"jsonrpc":"2.0","id":7,"method":"tools/call",
                 "params":{"name":"no_such_tool","arguments":{}}}
                """);
        ResponseEntity<Map<String, Object>> resp = controller.handle(KB_UUID, body);
        JsonNode errorNode = MAPPER.readTree(MAPPER.writeValueAsString(resp.getBody())).path("error");
        assertTrue(errorNode.path("message").asText().contains("no_such_tool"));
    }

    @DisplayName("GET：405（未实现 server-initiated SSE，官方 SDK 据此降级为纯 POST）")
    @Test
    void getReturns405() {
        ResponseEntity<Void> resp = controller.streamNotSupported();
        assertEquals(405, resp.getStatusCode().value());
    }
}
