package com.moyz.adi.common.eval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 逐题评分结果（铁律 9：每个布尔值都来自真实证据，报告里的任何聚合分数都能从这里逐题复核）。
 */
@Data
public class QuestionResult {

    private String id;

    private String type;

    private String question;

    /**
     * 与 {@code item.expected} 等长：第 i 条金标片段是否被检索上下文覆盖（逐字包含）
     */
    private List<Boolean> snippetCovered = new ArrayList<>();

    /**
     * 上下文召回率 = 覆盖的金标片段数 / 金标片段总数；拒答题无金标，恒为 null（不进 recall 均值）
     */
    private Double contextRecall;

    /**
     * 「命中 gold 来源」：每条金标片段都被覆盖，且覆盖它的块所属文档与 sourceDocs[i] 匹配。
     * 拒答题恒为 null（不进 accuracy 均值）
     */
    private Boolean sourceHit;

    /**
     * 系统是否拒答（网关显式给出，或按规则推断：无检索结果 / 空答案 / 命中拒答话术）
     */
    private boolean refused;

    /**
     * 拒答判定是否正确（refused == refusalExpected）
     */
    private boolean refusalCorrect;

    /**
     * 拒答预期（透传自题库，便于报告自含）
     */
    private boolean refusalExpected;

    private int retrievedCount;

    /**
     * 网关异常 / 拒答话术命中等补充信息
     */
    private String detail = "";

    /**
     * 本题是否计为失败（网关异常时 accuracy/recall 记 0，绝不静默剔除）
     */
    private transient boolean errored;
}
