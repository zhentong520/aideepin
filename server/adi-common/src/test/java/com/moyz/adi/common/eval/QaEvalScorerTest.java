package com.moyz.adi.common.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 计分器单测（铁律 9）：分数的每一条算术路径都用 mock 检索结果固定验证，
 * 与真实嵌入/生成完全解耦。「有内容即通过」在这里不可能发生——
 * 覆盖判定是逐字包含，来源判定要求文档归属匹配，拒答判定逐条话术核对。
 */
class QaEvalScorerTest {

    private static QaEvalItem factItem(String id, String question, List<String> expected, List<String> sourceDocs) {
        QaEvalItem item = new QaEvalItem();
        item.setId(id);
        item.setType(QaEvalItem.TYPE_FACT);
        item.setQuestion(question);
        item.setExpected(expected);
        item.setSourceDocs(sourceDocs);
        return item;
    }

    private static QaEvalItem refusalItem(String id) {
        QaEvalItem item = new QaEvalItem();
        item.setId(id);
        item.setType(QaEvalItem.TYPE_REFUSAL);
        item.setQuestion("范围外问题");
        item.setRefusalExpected(true);
        return item;
    }

    // ---------- 覆盖与来源 ----------

    @Test
    void fullCoverageWithMatchingSourceIsAHit() {
        QaEvalItem item = factItem("f1", "q",
                List.of("子块负责精准命中", "返回其父段作为回答语境"),
                List.of("segment.md", "segment.md"));
        GatewayResult result = new GatewayResult(List.of(
                new RetrievedChunk("【分段说明 · 第 1 段】\n子块负责精准命中，返回其父段作为回答语境。", "guide/knowledge-base/segment.md"),
                new RetrievedChunk("【其他文档 · 第 2 段】\n无关内容", "guide/other/doc.md")),
                null, false, null);

        QuestionResult r = QaEvalScorer.score(item, result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);

        assertEquals(List.of(true, true), r.getSnippetCovered());
        assertEquals(1.0, r.getContextRecall());
        assertEquals(Boolean.TRUE, r.getSourceHit());
        assertFalse(r.isRefused());
        assertTrue(r.isRefusalCorrect());
    }

    @Test
    void coveredButWrongSourceIsNotAHit() {
        // 内容被覆盖，但覆盖它的块来自错误文档：「命中 gold 来源」必须为 false——
        // 这是与「有内容即通过」的本质区别
        QaEvalItem item = factItem("f2", "q",
                List.of("子块负责精准命中"), List.of("segment.md"));
        GatewayResult result = new GatewayResult(List.of(
                new RetrievedChunk("子块负责精准命中，返回其父段作为回答语境。", "guide/other/wrong-doc.md")),
                null, false, null);

        QuestionResult r = QaEvalScorer.score(item, result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);

        assertEquals(1.0, r.getContextRecall(), "召回率只看内容覆盖");
        assertEquals(Boolean.FALSE, r.getSourceHit(), "来源不匹配不得算命中");
    }

    @Test
    void missingSnippetLowersRecallAndMissesAccuracy() {
        QaEvalItem item = factItem("h1", "q",
                List.of("片段甲", "片段乙"), List.of("docA.md", "docB.md"));
        GatewayResult result = new GatewayResult(List.of(
                new RetrievedChunk("这里包含片段甲的内容", "guide/docA.md")),
                null, false, null);

        QuestionResult r = QaEvalScorer.score(item, result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);

        assertEquals(List.of(true, false), r.getSnippetCovered());
        assertEquals(0.5, r.getContextRecall());
        assertEquals(Boolean.FALSE, r.getSourceHit());
        assertTrue(r.getDetail().contains("[1]"), "缺失片段要在 detail 里可追溯");
    }

    @Test
    void coverageIsVerbatimNotFuzzy() {
        // 金标是逐字串：改写、加空格截断都不算覆盖——模糊匹配只会制造假阳性
        QaEvalItem item = factItem("f3", "q", List.of("最大召回数量"), List.of("kb-qa.md"));
        GatewayResult rewritten = new GatewayResult(List.of(
                new RetrievedChunk("最大召 回数量的含义", "kb-qa.md")), null, false, null);
        assertEquals(Boolean.FALSE, QaEvalScorer.score(item, rewritten, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS).getSourceHit());
    }

    // ---------- 拒答判定 ----------

    @Test
    void refusalExpectedAndRefusedIsCorrect() {
        GatewayResult result = new GatewayResult(List.of(), "抱歉，我不知道这个问题的答案。", null, null);
        QuestionResult r = QaEvalScorer.score(refusalItem("r1"), result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);
        assertTrue(r.isRefused());
        assertTrue(r.isRefusalCorrect());
        assertNull(r.getContextRecall(), "拒答题不进 recall 均值");
        assertNull(r.getSourceHit(), "拒答题不进 accuracy 均值");
    }

    @Test
    void refusalExpectedButAnsweredIsWrong() {
        GatewayResult result = new GatewayResult(List.of(
                new RetrievedChunk("法国的首都是巴黎。", "guide/x.md")), "法国的首都是巴黎。", null, null);
        QuestionResult r = QaEvalScorer.score(refusalItem("r2"), result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);
        assertFalse(r.isRefused());
        assertFalse(r.isRefusalCorrect(), "应拒答却作答，必须扣分");
    }

    @Test
    void answerableQuestionMustNotBeRefused() {
        QaEvalItem item = factItem("f4", "q", List.of("片段甲"), List.of("docA.md"));
        GatewayResult refusedWrongly = new GatewayResult(List.of(
                new RetrievedChunk("片段甲在这里", "docA.md")), "[无答案]", null, null);
        QuestionResult r = QaEvalScorer.score(item, refusedWrongly, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);
        assertTrue(r.isRefused());
        assertFalse(r.isRefusalCorrect(), "语料里有答案却说无答案，拒答判定必须扣分");
    }

    @Test
    void explicitRefusedFlagWinsOverInference() {
        QaEvalItem item = factItem("f5", "q", List.of("片段甲"), List.of("docA.md"));
        GatewayResult result = new GatewayResult(List.of(), null, true, null);
        assertTrue(QaEvalScorer.score(item, result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS).isRefused(),
                "网关显式拒绝（如严格模式抛出）优先于推断");
    }

    @Test
    void emptyRetrievalWithoutGenerationCountsAsRefusal() {
        // 纯检索评测：无检索结果 + 无答案 = 无法作答 → 判拒答
        GatewayResult result = new GatewayResult(List.of(), null, null, null);
        assertTrue(QaEvalScorer.score(refusalItem("r3"), result, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS).isRefusalCorrect());
    }

    // ---------- 网关异常与聚合 ----------

    @Test
    void gatewayErrorScoresAsFailureNotSilentSkip() {
        QaEvalItem item = factItem("f6", "q", List.of("片段甲"), List.of("docA.md"));
        GatewayResult errored = new GatewayResult(List.of(), null, null, "IllegalStateException: embedding down");
        QuestionResult r = QaEvalScorer.score(item, errored, QaEvalScorer.DEFAULT_REFUSAL_PATTERNS);
        assertTrue(r.isErrored());
        assertEquals(0.0, r.getContextRecall());
        assertEquals(Boolean.FALSE, r.getSourceHit());
        assertFalse(r.isRefusalCorrect(), "基础设施故障不得被吞成通过");
    }

    @Test
    void aggregateMathIsExactOnAFixedCase() {
        // 构造 4 题：2 fact（1 命中 1 半召回）+ 1 multi_hop（0 命中）+ 1 refusal（判定正确）
        QaEvalItem f1 = factItem("a", "q", List.of("片段甲"), List.of("docA.md"));
        QaEvalItem f2 = factItem("b", "q", List.of("片段甲", "片段乙"), List.of("docA.md", "docB.md"));
        QaEvalItem h1 = factItem("c", "q", List.of("片段丙"), List.of("docC.md"));
        h1.setType(QaEvalItem.TYPE_MULTI_HOP);
        QaEvalItem r1 = refusalItem("d");

        List<QuestionResult> results = List.of(
                QaEvalScorer.score(f1, new GatewayResult(List.of(new RetrievedChunk("片段甲", "docA.md")), null, false, null),
                        QaEvalScorer.DEFAULT_REFUSAL_PATTERNS),
                QaEvalScorer.score(f2, new GatewayResult(List.of(new RetrievedChunk("片段甲", "docA.md")), null, false, null),
                        QaEvalScorer.DEFAULT_REFUSAL_PATTERNS),
                QaEvalScorer.score(h1, new GatewayResult(List.of(), null, false, null),
                        QaEvalScorer.DEFAULT_REFUSAL_PATTERNS),
                QaEvalScorer.score(r1, new GatewayResult(List.of(), "[无答案]", null, null),
                        QaEvalScorer.DEFAULT_REFUSAL_PATTERNS));

        EvalReport.Metrics m = EvalReportWriter.aggregate(results);
        assertEquals(3, m.getFactCount() + m.getMultiHopCount());
        assertEquals(1, m.getRefusalCount());
        // accuracy = 命中来源的非拒答题数 / 非拒答题数 = 1/3
        assertEquals(1.0 / 3, m.getAccuracy(), 1e-9);
        // recall = (1.0 + 0.5 + 0.0) / 3
        assertEquals(0.5, m.getRecall(), 1e-9);
        // refusalAccuracy = 4 题里 1 题拒答判定正确（r1）——注意 a/b/c 题网关 false、判定正确？
        // a/b/c refusalExpected=false 且 refused=false → 正确；d refusalExpected=true 且 refused=true → 正确。
        // 4/4
        assertEquals(1.0, m.getRefusalAccuracy(), 1e-9);
        assertEquals(2, m.getPerType().get("fact").getCount());
        assertEquals(1, m.getPerType().get("multi_hop").getCount());
        // fact 题型内 recall = (1.0 + 0.5) / 2 = 0.75
        assertEquals(0.75, m.getPerType().get("fact").getRecall(), 1e-9);
    }

    @Test
    void aggregateExcludesRefusalItemsFromAccuracyDenominator() {
        // 全部拒答题判定正确的极端：accuracy/recall 分母只有非拒答题，不得被拒答题稀释
        QaEvalItem r1 = refusalItem("r");
        EvalReport.Metrics m = EvalReportWriter.aggregate(List.of(
                QaEvalScorer.score(r1, new GatewayResult(List.of(), "[无答案]", null, null),
                        QaEvalScorer.DEFAULT_REFUSAL_PATTERNS)));
        assertEquals(0.0, m.getAccuracy(), "无非拒答题时 accuracy 定义为 0（分母 0）");
        assertEquals(0.0, m.getRecall());
        assertEquals(1.0, m.getRefusalAccuracy());
    }
}
