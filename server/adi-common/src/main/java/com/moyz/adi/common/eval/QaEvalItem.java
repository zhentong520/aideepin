package com.moyz.adi.common.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 端到端 QA 评测题库条目（T6-01）。
 * <p>
 * 字段与 {@code server/dev-scripts/eval/qa-dataset.json} 一一对应：
 * <ul>
 *   <li>{@code type}：fact（事实问答）/ multi_hop（多跳/多事实）/ refusal（拒答边界）</li>
 *   <li>{@code expected}：金标片段，**逐字照抄语料原文**（与 kb-eval golden.jsonl 同纪律，
 *       语料变更后题库必须同步评审）。非拒答题至少 1 条</li>
 *   <li>{@code sourceDocs}：与 expected 等长，第 i 条金标片段的来源文档路径子串
 *       （用于「命中 gold 来源」判定）；拒答题为空</li>
 *   <li>{@code refusalExpected}：true 表示该题必须拒答（知识库范围内无答案）</li>
 * </ul>
 * 铁律 9：本类只是数据载体，分数全部由 {@link QaEvalScorer} 从真实检索/生成结果算出。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QaEvalItem {

    public static final String TYPE_FACT = "fact";
    public static final String TYPE_MULTI_HOP = "multi_hop";
    public static final String TYPE_REFUSAL = "refusal";

    private String id;

    private String type;

    private String question;

    private List<String> expected = new ArrayList<>();

    private List<String> sourceDocs = new ArrayList<>();

    private boolean refusalExpected;
}
