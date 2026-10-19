package com.moyz.adi.common.rag;

import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.service.DocumentSegmentService;
import com.moyz.adi.common.service.KbDocumentService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检索结果出处抬头的断言：只重写返回文本、不参与向量化，且在数据缺失或回表异常时降级而非中断。
 */
class SegmentExpandProcessorCitationTest {

    private static final String BODY = "第一条 为规范物业管理活动，制定本条例。";
    private static final String BODY_2 = "第二条 本条例所称物业管理，是指业主选聘物业服务企业。";

    private SegmentExpandProcessor processor;
    private DocumentSegmentService documentSegmentService;
    private KbDocumentService kbDocumentService;

    @BeforeEach
    void setUp() {
        processor = new SegmentExpandProcessor();
        documentSegmentService = mock(DocumentSegmentService.class);
        kbDocumentService = mock(KbDocumentService.class);
        ReflectionTestUtils.setField(processor, "documentSegmentService", documentSegmentService);
        ReflectionTestUtils.setField(processor, "kbDocumentService", kbDocumentService);
    }

    @Test
    void prependsDocumentTitleAndOneBasedPosition() {
        when(documentSegmentService.mapByIds(any())).thenReturn(Map.of(1L, segment(1L, "doc-1", 2)));
        when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of("doc-1", "物业管理条例"));

        List<Content> result = enrich(List.of(content(BODY, "1")));

        // position 从 0 开始存，展示给 LLM 与用户时按第 3 段
        assertEquals("【物业管理条例 · 第 3 段】\n" + BODY, text(result, 0));
        assertEquals("doc-1", metadata(result, 0).getString(AdiConstant.MetadataKey.DOC_UUID));
        assertEquals("2", metadata(result, 0).getString(AdiConstant.MetadataKey.POSITION));
        assertEquals("物业管理条例", metadata(result, 0).getString(AdiConstant.MetadataKey.DOC_TITLE));
    }

    @Test
    void keepsBodyIntactWhenSegmentRowIsMissing() {
        when(documentSegmentService.mapByIds(any())).thenReturn(Map.of());

        List<Content> result = enrich(List.of(content(BODY, "1")));

        assertEquals(BODY, text(result, 0), "段行缺失时必须原样返回，不能凭空造抬头");
    }

    @Test
    void skipsLookupWhenSegmentIdIsNotARelationalId() {
        // 关键词路与向量路正常都写数据库 id；若命中项带着向量库的段 uuid 占位，
        // 属于无法回表的脏数据，应直接跳过而不是发起一次必然落空的查询。
        List<Content> result = enrich(List.of(content(BODY, "8f3a2c1e-uuid")));

        assertEquals(BODY, text(result, 0));
        verify(documentSegmentService, never()).mapByIds(any());
        verify(kbDocumentService, never()).mapTitlesByUuids(any());
    }

    @Test
    void fallsBackToPlaceholderTitleWhenTitleIsBlank() {
        when(documentSegmentService.mapByIds(any())).thenReturn(Map.of(1L, segment(1L, "doc-1", 2)));
        // 标题为空串的文档不会进 map
        when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of());

        List<Content> result = enrich(List.of(content(BODY, "1")));

        assertEquals("【未命名文档 · 第 3 段】\n" + BODY, text(result, 0));
    }

    @Test
    void omitsPositionWhenSegmentHasNone() {
        when(documentSegmentService.mapByIds(any())).thenReturn(Map.of(1L, segment(1L, "doc-1", null)));
        when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of("doc-1", "物业管理条例"));

        List<Content> result = enrich(List.of(content(BODY, "1")));

        assertEquals("【物业管理条例】\n" + BODY, text(result, 0));
        assertNull(metadata(result, 0).getString(AdiConstant.MetadataKey.POSITION));
    }

    @Test
    void keepsRawContentsWhenLookupThrows() {
        when(documentSegmentService.mapByIds(any())).thenThrow(new IllegalStateException("db down"));

        List<Content> result = enrich(List.of(content(BODY, "1")));

        assertEquals(BODY, text(result, 0), "回表异常只能降级，不能把整轮检索打挂");
    }

    @Test
    void returnsInputUntouchedWhenEmpty() {
        assertTrue(enrich(List.of()).isEmpty());
    }

    @Test
    void returnsNullWhenGivenNull() {
        // 显式传 null 参数数组，否则可变参数会把 null 当成"零个参数"而找不到方法
        assertNull(ReflectionTestUtils.invokeMethod(processor, "withCitation", new Object[]{null}));
    }

    @Test
    void queriesOnceForMultipleHits() {
        when(documentSegmentService.mapByIds(any()))
                .thenReturn(Map.of(1L, segment(1L, "doc-1", 0), 2L, segment(2L, "doc-1", 1)));
        when(kbDocumentService.mapTitlesByUuids(any())).thenReturn(Map.of("doc-1", "物业管理条例"));

        List<Content> result = enrich(List.of(content(BODY, "1"), content(BODY_2, "2")));

        assertEquals(2, result.size());
        assertEquals("【物业管理条例 · 第 1 段】\n" + BODY, text(result, 0));
        assertEquals("【物业管理条例 · 第 2 段】\n" + BODY_2, text(result, 1));
        // 同一文档的两段命中必须共用一次回表，避免检索路径 N+1
        verify(documentSegmentService, times(1)).mapByIds(any());
        verify(kbDocumentService, times(1)).mapTitlesByUuids(any());
    }

    @SuppressWarnings("unchecked")
    private List<Content> enrich(List<Content> contents) {
        return (List<Content>) ReflectionTestUtils.invokeMethod(processor, "withCitation", contents);
    }

    private static Content content(String text, String segmentId) {
        return Content.from(TextSegment.from(text, Metadata.from(Map.<String, Object>of(
                AdiConstant.MetadataKey.SEGMENT_ID, segmentId,
                AdiConstant.MetadataKey.KB_UUID, "kb-1"))));
    }

    private static DocumentSegment segment(Long id, String docUuid, Integer position) {
        DocumentSegment segment = new DocumentSegment();
        segment.setId(id);
        segment.setDocUuid(docUuid);
        segment.setPosition(position);
        return segment;
    }

    private static String text(List<Content> contents, int index) {
        return contents.get(index).textSegment().text();
    }

    private static Metadata metadata(List<Content> contents, int index) {
        return contents.get(index).textSegment().metadata();
    }
}
