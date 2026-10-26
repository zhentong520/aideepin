package com.moyz.adi.common.eval;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * T6-15（2026-10-20）：多 Agent 委派评测计分器。
 * <p>
 * 委派题集 JSON 中每题给出「任务描述 → 应委派给的 agent（含别名）」，
 * 被测端点返回预测 agent 名后，由本计分器判定命中。
 * <p>
 * 归一化规则：trim → 去首尾引号 → 全小写；预测名等于期望名或期望别名之一即命中。
 * Python 跑分脚本（dev-scripts/eval/delegation-eval/run_delegation_eval.py）保持同一语义。
 */
public final class DelegationScorer {

    private DelegationScorer() {
    }

    /**
     * 归一化 agent 名：trim、去首尾成对引号、小写。
     */
    public static String normalize(String agent) {
        String s = StringUtils.trimToEmpty(agent);
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')
                    || (first == '“' && last == '”') || (first == '«' && last == '»')) {
                s = s.substring(1, s.length() - 1);
            }
        }
        return s.toLowerCase(Locale.ROOT);
    }

    /**
     * 判定单题命中。
     *
     * @param expectedAgent 应委派给的 agent 名
     * @param aliases       期望 agent 的可接受别名（可空）
     * @param predicted     被测端点返回的 agent 名
     * @return true=委派正确
     */
    public static boolean isCorrect(String expectedAgent, List<String> aliases, String predicted) {
        String expected = normalize(expectedAgent);
        String actual = normalize(predicted);
        if (expected.isEmpty() || actual.isEmpty()) {
            return false;
        }
        if (expected.equals(actual)) {
            return true;
        }
        if (aliases == null) {
            return false;
        }
        // 预测名命中期望别名之一即正确
        return aliases.stream().map(DelegationScorer::normalize).anyMatch(actual::equals);
    }

    /**
     * 汇总：委派正确率 = 命中数 / 总题数（总题数为 0 时返回 -1 表示无有效评测，绝不输出 100% 假绿）。
     */
    public static EvalSummary score(List<CaseResult> results) {
        EvalSummary summary = new EvalSummary();
        if (results == null || results.isEmpty()) {
            summary.setTotal(0);
            summary.setCorrect(0);
            summary.setAccuracy(-1);
            return summary;
        }
        int correct = 0;
        for (CaseResult r : results) {
            if (r.correct()) {
                correct++;
            }
        }
        summary.setTotal(results.size());
        summary.setCorrect(correct);
        summary.setAccuracy((double) correct / results.size());
        return summary;
    }

    /**
     * 单题评测结果。
     */
    public record CaseResult(String caseId, String expectedAgent, String predicted, boolean correct) {
    }

    @lombok.Data
    public static class EvalSummary {
        private int total;
        private int correct;
        /** 命中率 0~1；无有效题时 -1 */
        private double accuracy;
    }
}
