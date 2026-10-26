package com.moyz.adi.common.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6-15 验收：委派评测计分器。
 */
class DelegationScorerTest {

    @Test
    void exactMatch() {
        assertTrue(DelegationScorer.isCorrect("translator", null, "translator"));
    }

    @Test
    void caseAndWhitespaceInsensitive() {
        assertTrue(DelegationScorer.isCorrect("Translator", null, "  translator "));
        assertTrue(DelegationScorer.isCorrect("TRANSLATOR", null, "translator"));
    }

    @Test
    void quotedPredictionsStripped() {
        assertTrue(DelegationScorer.isCorrect("translator", null, "\"translator\""));
        assertTrue(DelegationScorer.isCorrect("translator", null, "「translator」".replace("「", "“").replace("」", "”")));
    }

    @Test
    void aliasAccepted() {
        assertTrue(DelegationScorer.isCorrect("translator", List.of("翻译助手", "translation-agent"), "翻译助手"));
        assertFalse(DelegationScorer.isCorrect("translator", List.of("翻译助手"), "coder"));
    }

    @Test
    void wrongAgentFails() {
        assertFalse(DelegationScorer.isCorrect("translator", null, "coder"));
        // 相似但不同名不算命中：避免前缀/子串误判
        assertFalse(DelegationScorer.isCorrect("translator", null, "translator-v2"));
    }

    @Test
    void blankPredictedFails() {
        assertFalse(DelegationScorer.isCorrect("translator", null, ""));
        assertFalse(DelegationScorer.isCorrect("translator", null, null));
        assertFalse(DelegationScorer.isCorrect("", null, "translator"));
    }

    @Test
    void summaryAccuracy() {
        List<DelegationScorer.CaseResult> results = List.of(
                new DelegationScorer.CaseResult("DEL-001", "a", "a", true),
                new DelegationScorer.CaseResult("DEL-002", "b", "c", false),
                new DelegationScorer.CaseResult("DEL-003", "d", "d", true),
                new DelegationScorer.CaseResult("DEL-004", "e", "e", true));
        DelegationScorer.EvalSummary summary = DelegationScorer.score(results);
        assertEquals(4, summary.getTotal());
        assertEquals(3, summary.getCorrect());
        assertEquals(0.75, summary.getAccuracy(), 1e-9);
    }

    @Test
    void emptyResultsReturnMinusOneNeverFakeGreen() {
        DelegationScorer.EvalSummary summary = DelegationScorer.score(List.of());
        assertEquals(0, summary.getTotal());
        assertEquals(-1, summary.getAccuracy(), 1e-9);
        // 空题集不允许输出 100%——铁律 9：不伪造分数
        assertFalse(summary.getAccuracy() == 1.0);
    }
}
