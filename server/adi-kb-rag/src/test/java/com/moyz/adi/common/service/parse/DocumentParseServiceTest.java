package com.moyz.adi.common.service.parse;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.AdiFile;
import com.moyz.adi.common.file.FileOperatorContext;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文档解析降级链单测（P1）：
 * <ul>
 *   <li>开关关闭（默认）：退化为旧版 langchain4j 单层行为，sidecar 永不调用；</li>
 *   <li>开关开启：langchain4j 有文本直返，blank/不支持才升级 sidecar，sidecar 失败保持原结果；</li>
 *   <li>force 模式：即使有文本也 sidecar 优先，失败落回 langchain4j；</li>
 *   <li>图片按 maxImagesPerDoc 截断。</li>
 * </ul>
 * FileOperatorContext.loadDocument 是静态方法，用 mockStatic 拦截，避免依赖真实文件。
 */
class DocumentParseServiceTest {

    private AdiProperties adiProperties;
    private MinerUParseService minerUParseService;
    private DocumentParseService documentParseService;

    private MockedStatic<FileOperatorContext> fileOperatorMock;

    @BeforeEach
    void setUp() {
        adiProperties = new AdiProperties();
        minerUParseService = mock(MinerUParseService.class);
        documentParseService = new DocumentParseService(adiProperties, minerUParseService,
                new ParseCacheService(mock(com.moyz.adi.common.mapper.KbParseCacheMapper.class), adiProperties));
        fileOperatorMock = mockStatic(FileOperatorContext.class);
    }

    @AfterEach
    void tearDown() {
        fileOperatorMock.close();
    }

    private AdiFile file(String ext) {
        AdiFile file = new AdiFile();
        file.setUuid("f123");
        file.setExt(ext);
        file.setName("doc." + ext);
        return file;
    }

    private void mockLangchain4jText(String text) throws BlankDocumentException {
        Document document = mock(Document.class);
        when(document.text()).thenReturn(text);
        fileOperatorMock.when(() -> FileOperatorContext.loadDocument(any(AdiFile.class))).thenReturn(document);
    }

    private void mockLangchain4jBlank() throws BlankDocumentException {
        fileOperatorMock.when(() -> FileOperatorContext.loadDocument(any(AdiFile.class)))
                .thenThrow(new BlankDocumentException());
    }

    private void enableSidecar(String ext) {
        adiProperties.getParse().setEnabled(true);
        adiProperties.getParse().setEndpoint("http://127.0.0.1:8888");
        when(minerUParseService.isEnabled()).thenReturn(true);
        when(minerUParseService.supports(ext)).thenReturn(true);
    }

    // ---------- 开关关闭（默认） ----------

    @Test
    void disabled_langchain4jSuccessReturnedDirectly() throws Exception {
        mockLangchain4jText("正文内容");
        ParseResult result = documentParseService.parse(file("txt"));
        assertEquals(ParseResult.SOURCE_LANGCHAIN4J, result.getSource());
        assertEquals("正文内容", result.getText());
        assertFalse(result.isUnsupported());
        verify(minerUParseService, never()).parse(any());
    }

    @Test
    void disabled_blankText_keepsOldSemantics() throws Exception {
        // 扫描件/空文本：与旧版一致——text 空、unsupported=false（类型本身有解析器），
        // OCR 兜底由 KnowledgeBaseService 的 isBlank(content) 条件触发
        mockLangchain4jBlank();
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals("", result.textOrEmpty());
        assertFalse(result.isUnsupported());
        verify(minerUParseService, never()).parse(any());
    }

    @Test
    void disabled_unsupportedType() throws Exception {
        fileOperatorMock.when(() -> FileOperatorContext.loadDocument(any(AdiFile.class)))
                .thenThrow(new RuntimeException("no parser"));
        ParseResult result = documentParseService.parse(file("zip"));
        assertEquals("", result.textOrEmpty());
        assertTrue(result.isUnsupported());
        verify(minerUParseService, never()).parse(any());
    }

    // ---------- 开关开启：按需升级 ----------

    @Test
    void enabled_langchain4jHasText_sidecarNotCalled() throws Exception {
        enableSidecar("pdf");
        mockLangchain4jText("有文本层的 pdf");
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals(ParseResult.SOURCE_LANGCHAIN4J, result.getSource());
        assertEquals("有文本层的 pdf", result.getText());
        verify(minerUParseService, never()).parse(any());
    }

    @Test
    void enabled_blankText_escalatesToSidecar() throws Exception {
        enableSidecar("pdf");
        mockLangchain4jBlank();
        byte[] img = new byte[]{1, 2, 3};
        when(minerUParseService.parse(any(AdiFile.class))).thenReturn(new MinerUParseService.MinerUResult(
                "![](images/0.jpg)\\n正文", java.util.Map.of("images/0.jpg", img)));
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals(ParseResult.SOURCE_MINERU, result.getSource());
        assertTrue(result.getText().contains("正文"));
        assertFalse(result.isUnsupported());
        assertEquals(1, result.getImages().size());
        assertEquals("images/0.jpg", result.getImages().get(0).refKey());
        assertEquals("0.jpg", result.getImages().get(0).fileName(), "dir prefix stripped into baseName");
    }

    @Test
    void enabled_sidecarFails_fallsBackToLangchain4jResult() throws Exception {
        enableSidecar("pdf");
        mockLangchain4jBlank();
        when(minerUParseService.parse(any(AdiFile.class))).thenReturn(null);
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals(ParseResult.SOURCE_LANGCHAIN4J, result.getSource());
        assertEquals("", result.textOrEmpty());
        // sidecar 没产出文本时无图片，OCR 兜底条件交给上层
        assertFalse(result.hasImages());
    }

    @Test
    void enabled_unsupportedType_escalatesToSidecar() throws Exception {
        enableSidecar("png");
        fileOperatorMock.when(() -> FileOperatorContext.loadDocument(any(AdiFile.class)))
                .thenThrow(new RuntimeException("no parser for png"));
        when(minerUParseService.parse(any(AdiFile.class))).thenReturn(new MinerUParseService.MinerUResult(
                "截图文字", java.util.Map.of()));
        ParseResult result = documentParseService.parse(file("png"));
        assertEquals(ParseResult.SOURCE_MINERU, result.getSource());
        assertEquals("截图文字", result.getText());
    }

    // ---------- force 模式 ----------

    @Test
    void force_sidecarFirst_evenWithTextLayer() throws Exception {
        enableSidecar("pdf");
        adiProperties.getParse().setForceForEnabledExts(true);
        mockLangchain4jText("原生文本层");
        when(minerUParseService.parse(any(AdiFile.class))).thenReturn(new MinerUParseService.MinerUResult(
                "版式还原的 markdown", java.util.Map.of()));
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals(ParseResult.SOURCE_MINERU, result.getSource());
        assertEquals("版式还原的 markdown", result.getText());
        fileOperatorMock.verify(() -> FileOperatorContext.loadDocument(any(AdiFile.class)), never());
    }

    @Test
    void force_sidecarFails_landsBackOnLangchain4j() throws Exception {
        enableSidecar("pdf");
        adiProperties.getParse().setForceForEnabledExts(true);
        mockLangchain4jText("原生文本层");
        when(minerUParseService.parse(any(AdiFile.class))).thenReturn(null);
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals(ParseResult.SOURCE_LANGCHAIN4J, result.getSource());
        assertEquals("原生文本层", result.getText());
    }

    // ---------- 图片截断 ----------

    @Test
    void imagesCappedByMaxImagesPerDoc() throws Exception {
        enableSidecar("pdf");
        adiProperties.getParse().setMaxImagesPerDoc(2);
        mockLangchain4jBlank();
        java.util.Map<String, byte[]> images = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            images.put("images/" + i + ".jpg", new byte[]{(byte) i});
        }
        when(minerUParseService.parse(any(AdiFile.class)))
                .thenReturn(new MinerUParseService.MinerUResult("md", images));
        ParseResult result = documentParseService.parse(file("pdf"));
        assertEquals(2, result.getImages().size());
        assertEquals(List.of("images/0.jpg", "images/1.jpg"),
                result.getImages().stream().map(ParseResult.ExtractedImage::refKey).toList());
    }

    // ---------- guessImageExt ----------

    @Test
    void guessImageExt_fromRefKeyThenMagicThenPng() {
        assertEquals("jpeg", DocumentParseService.guessImageExt("images/3.jpeg", new byte[]{1}));
        assertEquals("png", DocumentParseService.guessImageExt("images/0", new byte[]{(byte) 0x89, 'P', 'N', 'G'}));
        assertEquals("jpg", DocumentParseService.guessImageExt("images/0", new byte[]{(byte) 0xFF, (byte) 0xD8, 0, 0}));
        assertEquals("png", DocumentParseService.guessImageExt("images/0", new byte[]{1, 2, 3}));
    }
}
