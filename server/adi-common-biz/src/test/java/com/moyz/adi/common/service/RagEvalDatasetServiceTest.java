package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.dto.RagToEvalReq;
import com.moyz.adi.common.dto.RagToEvalResp;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.JsonUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-01 验收：失败案例一键转评测题（kb-eval 候选池格式）。
 * <p>关键口径：
 * <ul>
 *   <li>题目结构与 qa-dataset.json 同格式（meta + items[]，item: id/type/question/expected/sourceDocs），
 *       且带 derivedFrom 来源标注（queryId/kbUuids/hitCount/topScore/elapsedMs/createTime/reason）；</li>
 *   <li>expected 留空——候选池金标必须人工补，直接并入主题库会被 QaEvalRunner 拒（防假绿）；</li>
 *   <li>幂等：同 queryId 重复转换只生成一题；</li>
 *   <li>无问句（V12 前老日志）跳过而非造空题。</li>
 * </ul>
 */
class RagEvalDatasetServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    private RagEvalDatasetService service;
    private RagQualityService ragQualityService;

    @BeforeEach
    void setUp() {
        service = new RagEvalDatasetService();
        ragQualityService = Mockito.mock(RagQualityService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "ragQualityService", ragQualityService);
        AdiProperties props = new AdiProperties();
        props.getRag().setEvalDatasetDir(tempDir.toString());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adiProperties", props);
    }

    private static RagQueryLog candidate(String queryId, String queryText, int hitCount, String hitSegmentsJson) {
        return RagQueryLogService.build(queryId, "u-1", List.of("kb-a", "kb-b"), "f",
                hitSegmentsJson, hitCount, 0, 321, "local:bge", queryText);
    }

    /** 带时间范围的基础请求（convert 对 begin/end 直接 parse，缺省会 NPE） */
    private static RagToEvalReq baseReq() {
        RagToEvalReq req = new RagToEvalReq();
        req.setBeginTime("2026-10-01 00:00:00");
        req.setEndTime("2026-10-20 23:59:59");
        return req;
    }

    @Test
    void generatedItemsFollowKbEvalFormatWithDerivedFrom() throws Exception {
        Mockito.when(ragQualityService.toEvalCandidates(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.eq(RagToEvalReq.SCOPE_LOW_SCORE), Mockito.anyInt()))
                .thenReturn(List.of(
                        candidate("q-low", "知识库怎么配 ACL？", 2,
                                "[{\"kb_uuid\":\"kb-a\",\"score\":0.31},{\"kb_uuid\":\"kb-a\",\"score\":0.42}]"),
                        candidate("q-miss", "从未命中的问题", 0, "[]")));

        RagToEvalReq req = baseReq();
        req.setBeginTime("2026-10-01 00:00:00");
        req.setEndTime("2026-10-20 23:59:59");
        RagToEvalResp resp = service.convert(req);

        assertEquals(2, resp.getGeneratedCount());
        assertEquals(0, resp.getSkippedCount());
        assertEquals(tempDir.resolve(RagEvalDatasetService.DERIVED_FILE_NAME).toAbsolutePath().toString(),
                resp.getDatasetFile());

        JsonNode root = MAPPER.readTree(Files.readString(Path.of(resp.getDatasetFile())));
        // meta：与 qa-dataset.json 同结构，minSize=0（候选池不设下限）
        assertEquals("aideepin-kb-qa-eval-derived", root.path("meta").path("name").asText());
        assertEquals(0, root.path("meta").path("minSize").asInt());
        assertTrue(root.path("meta").path("discipline").asText().contains("人工补"));

        JsonNode items = root.path("items");
        assertTrue(items.isArray() && items.size() == 2);

        JsonNode first = items.get(0);
        // kb-eval 题目骨架：id/type/question/expected/sourceDocs
        assertEquals("d-q-low", first.path("id").asText());
        assertEquals("fact", first.path("type").asText());
        assertEquals("知识库怎么配 ACL？", first.path("question").asText());
        assertTrue(first.path("expected").isArray() && first.path("expected").isEmpty(),
                "expected 金标必须留空待人工补（防假绿）");
        assertTrue(first.path("sourceDocs").isArray() && first.path("sourceDocs").isEmpty());
        // derivedFrom 来源标注
        assertEquals("q-low", first.path("derivedFrom").path("queryId").asText());
        assertEquals("kb-a", first.path("derivedFrom").path("kbUuids").get(0).asText());
        assertEquals("kb-b", first.path("derivedFrom").path("kbUuids").get(1).asText());
        assertEquals(2, first.path("derivedFrom").path("hitCount").asInt());
        // topScore = 命中里最大分（与聚合 SQL 同口径）
        assertEquals(0.42, first.path("derivedFrom").path("topScore").asDouble(), 1e-9);
        assertEquals(321, first.path("derivedFrom").path("elapsedMs").asInt());
        // reason：低分范围下 hitCount>0 → low_score；无命中 → missed
        assertEquals(RagToEvalReq.SCOPE_LOW_SCORE, first.path("derivedFrom").path("reason").asText());
        assertEquals(RagToEvalReq.SCOPE_MISSED, items.get(1).path("derivedFrom").path("reason").asText());
    }

    @Test
    void repeatedConversionIsDeduplicatedByQueryId() throws Exception {
        Mockito.when(ragQualityService.toEvalCandidates(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.anyInt()))
                .thenReturn(List.of(candidate("q-dup", "重复转换的问题", 1, "[{\"score\":0.3}]")));

        RagToEvalReq req = baseReq();
        RagToEvalResp first = service.convert(req);
        assertEquals(1, first.getGeneratedCount());

        RagToEvalResp second = service.convert(req);
        assertEquals(0, second.getGeneratedCount());
        assertEquals(1, second.getSkippedCount());

        JsonNode root = MAPPER.readTree(Files.readString(Path.of(first.getDatasetFile())));
        assertEquals(1, root.path("items").size(), "同一 queryId 不产生重复题");
    }

    @Test
    void blankQueryTextSkippedNotEmptyQuestion() throws Exception {
        Mockito.when(ragQualityService.toEvalCandidates(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.anyInt()))
                .thenReturn(List.of(candidate("q-blank", "", 0, "[]")));

        RagToEvalResp resp = service.convert(baseReq());

        assertEquals(0, resp.getGeneratedCount());
        assertEquals(1, resp.getSkippedCount(), "无问句跳过而非造空题（V12 前老日志无 query_text）");
        assertFalse(Files.exists(Path.of(resp.getDatasetFile())), "无新题时不落盘");
    }

    @Test
    void missingDatasetDirFailsLoudly() {
        AdiProperties props = new AdiProperties();
        props.getRag().setEvalDatasetDir(tempDir.resolve("no-such-dir").toString());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "adiProperties", props);

        // ⚠️ 目录不存在直接报错：不静默创建、不写丢——管理员看得见失败比丢数据好
        assertThrows(BaseException.class, () -> service.convert(baseReq()));
    }

    @Test
    void corruptedDerivedFileFailsInsteadOfOverwrite() throws Exception {
        Path derived = tempDir.resolve(RagEvalDatasetService.DERIVED_FILE_NAME);
        Files.writeString(derived, "{ not-json");

        Mockito.when(ragQualityService.toEvalCandidates(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.anyInt()))
                .thenReturn(List.of(candidate("q-x", "问题", 1, "[]")));

        // 文件损坏宁可失败，也不覆盖可能含人工补标的题库
        assertThrows(BaseException.class, () -> service.convert(baseReq()));
    }

    @Test
    void failedScopeMarksReasonFailed() throws Exception {
        Mockito.when(ragQualityService.toEvalCandidates(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.eq(RagToEvalReq.SCOPE_FAILED), Mockito.anyInt()))
                .thenReturn(List.of(candidate("q-fail", "差评的问题", 3, "[{\"score\":0.8}]")));

        RagToEvalReq req = baseReq();
        req.setScope(RagToEvalReq.SCOPE_FAILED);
        RagToEvalResp resp = service.convert(req);

        JsonNode items = MAPPER.readTree(Files.readString(Path.of(resp.getDatasetFile()))).path("items");
        assertEquals(RagToEvalReq.SCOPE_FAILED, items.get(0).path("derivedFrom").path("reason").asText());
    }

    @Test
    void candidatesAppendToExistingDerivedFile() throws Exception {
        // 预置一份已有题目的派生题库：验证 append 而不是覆盖
        Path derived = tempDir.resolve(RagEvalDatasetService.DERIVED_FILE_NAME);
        JsonUtil.getObjectMapper().writeValue(derived.toFile(),
                MAPPER.readTree("{\"meta\":{\"name\":\"aideepin-kb-qa-eval-derived\",\"version\":\"v1\",\"minSize\":0},"
                        + "\"items\":[{\"id\":\"d-q-old\",\"question\":\"老题\"}]}"));

        Mockito.when(ragQualityService.toEvalCandidates(Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.any(), Mockito.anyInt()))
                .thenReturn(List.of(candidate("q-new", "新问题", 1, "[]")));

        RagToEvalResp resp = service.convert(baseReq());
        assertEquals(1, resp.getGeneratedCount());

        JsonNode root = MAPPER.readTree(Files.readString(derived));
        assertEquals(2, root.path("items").size(), "append 而不是覆盖既有候选题");
        assertEquals("老题", root.path("items").get(0).path("question").asText());
    }
}
