package com.moyz.adi.common.multiagent;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * T7-02（2026-10-20）：多 Agent 委派契约（delegation contract）。
 * <p>
 * 一个 Agent 把子任务委派给另一个专门 Agent 时，必须携带的四要素结构化记录：
 * <ul>
 *   <li>{@code objective}：要委派完成的子任务目标（必填，长度/复杂度同时是 effort-scaling 的难度信号）；</li>
 *   <li>{@code output_format}：期望的产出格式说明（如「JSON 数组」「不超过 200 字的结论」）；</li>
 *   <li>{@code context_summary}：交给目标 Agent 的背景摘要（父 Agent 已掌握的信息，避免目标重复劳动）；</li>
 *   <li>{@code must_not_do}：显式禁止事项（防越权/防跑题，如「不要访问用户知识库」）。</li>
 * </ul>
 * 契约四要素全部落库（adi_agent_delegation_log），保证每次委派可追溯。
 * <p>
 * The structured delegation contract: objective / output_format / context_summary /
 * must_not_do. All four fields are persisted per delegation for traceability.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegationContract {

    /** 委派目标（必填）。Delegation objective (required). */
    private String objective;

    /** 期望产出格式（可空）。Expected output format (optional). */
    private String outputFormat;

    /** 背景摘要（可空）。Context summary handed to the target agent (optional). */
    private String contextSummary;

    /** 显式禁止事项（可空）。Explicit prohibitions (optional). */
    private String mustNotDo;

    /**
     * 从 JSON 节点解析契约；objective 为空时抛参数错误（契约缺目标 = 无法执行也无法审计）。
     * 容错：缺字段按空串处理，只有 objective 必填。
     */
    public static DelegationContract fromJson(ObjectNode node) {
        if (null == node) {
            throw new IllegalArgumentException("delegation contract is missing");
        }
        String objective = StringUtils.trimToEmpty(node.path("objective").asText(""));
        if (objective.isEmpty()) {
            throw new IllegalArgumentException("delegation contract requires a non-empty objective");
        }
        return DelegationContract.builder()
                .objective(objective)
                .outputFormat(StringUtils.trimToNull(node.path("output_format").asText("")))
                .contextSummary(StringUtils.trimToNull(node.path("context_summary").asText("")))
                .mustNotDo(StringUtils.trimToNull(node.path("must_not_do").asText("")))
                .build();
    }

    /**
     * 渲染成给目标 Agent 的输入文本：四要素按固定结构拼接，
     * 使目标 Agent 无需额外系统提示就能理解任务边界。
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("【委派目标】").append(objective);
        if (StringUtils.isNotBlank(outputFormat)) {
            sb.append("\n【产出格式】").append(outputFormat);
        }
        if (StringUtils.isNotBlank(contextSummary)) {
            sb.append("\n【背景摘要】").append(contextSummary);
        }
        if (StringUtils.isNotBlank(mustNotDo)) {
            sb.append("\n【禁止事项】").append(mustNotDo);
        }
        return sb.toString();
    }

    /**
     * 难度信号（effort-scaling 的输入）：objective 长度 + 结构复杂度。
     * 信号包括：长度、句子数（多步任务通常多句）、问号数（澄清诉求）、
     * 列举/编号行数（多子项任务）。
     */
    public static final class DifficultySignal {
        private final int objectiveLength;
        private final int sentenceCount;
        private final int questionMarkCount;
        private final int listLineCount;

        public DifficultySignal(String objective) {
            String text = StringUtils.defaultString(objective);
            this.objectiveLength = text.length();
            this.sentenceCount = countSentences(text);
            this.questionMarkCount = StringUtils.countMatches(text, "?") + StringUtils.countMatches(text, "？");
            this.listLineCount = countListLines(text);
        }

        private static int countSentences(String text) {
            int count = 0;
            for (char c : text.toCharArray()) {
                if (c == '.' || c == '。' || c == '!' || c == '！' || c == ';' || c == '；' || c == '\n') {
                    count++;
                }
            }
            return count;
        }

        private static int countListLines(String text) {
            int count = 0;
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("-") || trimmed.startsWith("*") || trimmed.startsWith("·")
                        || trimmed.matches("^\\d+[.、)）].*")) {
                    count++;
                }
            }
            return count;
        }

        public int getObjectiveLength() {
            return objectiveLength;
        }

        public int getSentenceCount() {
            return sentenceCount;
        }

        public int getQuestionMarkCount() {
            return questionMarkCount;
        }

        public int getListLineCount() {
            return listLineCount;
        }

        /**
         * 汇总成 0~100 的复杂度分值（权重：长度主导，句数/列举/问号加成）。
         * 供 effort-scaling 分档使用；纯函数，可单测。
         */
        public int complexityScore() {
            int score = 0;
            // 长度：每 50 字 1 分，封顶 40
            score += Math.min(40, objectiveLength / 50);
            // 句数：每句 4 分，封顶 25
            score += Math.min(25, sentenceCount * 4);
            // 列举行：每行 5 分，封顶 20
            score += Math.min(20, listLineCount * 5);
            // 问号：每个 3 分，封顶 15
            score += Math.min(15, questionMarkCount * 3);
            return Math.min(100, score);
        }

        public List<String> describeSignals() {
            List<String> signals = new ArrayList<>();
            signals.add("objectiveLength=" + objectiveLength);
            signals.add("sentenceCount=" + sentenceCount);
            signals.add("questionMarkCount=" + questionMarkCount);
            signals.add("listLineCount=" + listLineCount);
            signals.add("complexityScore=" + complexityScore());
            return signals;
        }
    }
}
