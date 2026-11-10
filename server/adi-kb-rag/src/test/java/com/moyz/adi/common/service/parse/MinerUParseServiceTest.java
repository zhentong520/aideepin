package com.moyz.adi.common.service.parse;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Parse;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.file.FileOperatorContext;
import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinerU sidecar 客户端单测：宽松响应解析（两层 md_content / images）、
 * base64 图片解码（裸 base64 与 data URI）、enabled/supports 判定、
 * 以及「sidecar 故障绝不抛出，只返回 null」的降级契约。
 */
class MinerUParseServiceTest {

    private AdiProperties props() {
        return new AdiProperties();
    }

    // ---------- extractResult：宽松解析 ----------

    @Test
    void extractResult_mdAtRoot() {
        MinerUParseService service = new MinerUParseService(props());
        MinerUParseService.MinerUResult result = service.extractResult("{\"md_content\":\"# 标题\\n正文\"}");
        assertNotNull(result);
        assertEquals("# 标题\n正文", result.mdContent());
        assertTrue(result.images().isEmpty());
    }

    @Test
    void extractResult_mdUnderData() {
        MinerUParseService service = new MinerUParseService(props());
        MinerUParseService.MinerUResult result = service.extractResult(
                "{\"data\":{\"md_content\":\"hello\",\"images\":{}},\"backend\":\"hybrid\"}");
        assertNotNull(result);
        assertEquals("hello", result.mdContent());
    }

    @Test
    void extractResult_markdownFallbackKey() {
        MinerUParseService service = new MinerUParseService(props());
        MinerUParseService.MinerUResult result = service.extractResult("{\"markdown\":\"alt-key\"}");
        assertNotNull(result);
        assertEquals("alt-key", result.mdContent());
    }

    @Test
    void extractResult_blankOrBrokenBodyReturnsNull() {
        MinerUParseService service = new MinerUParseService(props());
        assertNull(service.extractResult(null));
        assertNull(service.extractResult("  "));
        assertNull(service.extractResult("not-json{"));
        // 有 JSON 但没有 md_content：同样返回 null
        assertNull(service.extractResult("{\"data\":{\"pages\":3}}"));
    }

    @Test
    void extractResult_imagesDecoded_andInvalidSkipped() {
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G'};
        String b64 = Base64.getEncoder().encodeToString(pngBytes);
        String body = "{\"md_content\":\"![](images/0.jpg)\",\"images\":{"
                + "\"images/0.jpg\":\"" + b64 + "\","
                + "\"images/1.jpg\":\"data:image/jpeg;base64," + b64 + "\","
                + "\"images/2.jpg\":\"@@not-base64@@\"}}";
        MinerUParseService service = new MinerUParseService(props());
        MinerUParseService.MinerUResult result = service.extractResult(body);
        assertNotNull(result);
        assertEquals(2, result.images().size());
        assertArrayEquals(pngBytes, result.images().get("images/0.jpg"));
        assertArrayEquals(pngBytes, result.images().get("images/1.jpg"));
    }

    // ---------- decodeImage ----------

    @Test
    void decodeImage_bareAndDataUri() {
        byte[] raw = "hello-image".getBytes(StandardCharsets.UTF_8);
        String b64 = Base64.getEncoder().encodeToString(raw);
        assertArrayEquals(raw, MinerUParseService.decodeImage(b64));
        assertArrayEquals(raw, MinerUParseService.decodeImage("data:image/png;base64," + b64));
        assertNull(MinerUParseService.decodeImage("###"));
    }

    // ---------- isEnabled / supports ----------

    @Test
    void isEnabled_disabledByDefault() {
        MinerUParseService service = new MinerUParseService(props());
        assertTrue(!service.isEnabled());
    }

    @Test
    void isEnabled_requiresEndpoint() {
        AdiProperties props = props();
        props.getParse().setEnabled(true);
        MinerUParseService service = new MinerUParseService(props);
        assertTrue(!service.isEnabled(), "enabled but no endpoint must stay disabled");
        props.getParse().setEndpoint("http://127.0.0.1:8888");
        assertTrue(service.isEnabled());
    }

    @Test
    void supports_defaultWhitelist() {
        MinerUParseService service = new MinerUParseService(props());
        assertTrue(service.supports("pdf"));
        assertTrue(service.supports("PNG"));
        assertTrue(!service.supports("txt"));
        assertTrue(!service.supports(""));
        assertTrue(!service.supports(null));
    }

    @Test
    void supports_explicitWhitelistOverrides() {
        AdiProperties props = props();
        props.getParse().setEnabledExts(" txt , md ");
        MinerUParseService service = new MinerUParseService(props);
        assertTrue(service.supports("txt"));
        assertTrue(service.supports("MD"));
        assertTrue(!service.supports("pdf"), "whitelist set: default exts no longer apply");
    }

    // ---------- parse：不启用直接返回 null，绝不抛出 ----------

    @Test
    void parse_disabledReturnsNullWithoutHttp() {
        MinerUParseService service = new MinerUParseService(props());
        AdiFile file = new AdiFile();
        file.setUuid("abc");
        file.setExt("pdf");
        assertNull(service.parse(file));
    }

    @Test
    void parse_missingFileReturnsNull() {
        AdiProperties props = props();
        Parse parse = props.getParse();
        parse.setEnabled(true);
        parse.setEndpoint("http://127.0.0.1:9"); // nothing listens here
        MinerUParseService service = new MinerUParseService(props);
        AdiFile file = new AdiFile();
        file.setUuid("abc");
        file.setExt("pdf");
        file.setName("doc.pdf");
        // storageLocation 本地但路径不存在 → readBytes 返回 null → parse 返回 null
        file.setStorageLocation(0);
        file.setPath(java.nio.file.Paths.get("definitely", "not", "exist.pdf").toString());
        assertNull(service.parse(file));
    }

    // ---------- 与 DocumentParseService 的协作（验证 record 透传） ----------

    @Test
    void minerUResult_carriesImagesOrder() {
        Map<String, byte[]> images = new LinkedHashMap<>();
        images.put("images/1.jpg", new byte[]{1});
        images.put("images/0.jpg", new byte[]{2});
        MinerUParseService.MinerUResult result =
                new MinerUParseService.MinerUResult("md", images);
        assertEquals("md", result.mdContent());
        assertEquals(2, result.images().size());
        assertEquals(1, result.images().get("images/1.jpg")[0]);
    }
}
