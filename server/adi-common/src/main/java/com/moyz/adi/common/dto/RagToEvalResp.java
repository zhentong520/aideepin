package com.moyz.adi.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * T7-01 一键转评测题响应。
 * <p>
 * 派生题库是「候选池」：题目 question 来自失败检索的问句，expected 金标留空待人工
 * 补充（语料原文逐字串），补齐后方可并入 qa-dataset.json。skippedCount 统计
 * 已存在（同 queryId 转过）被去重跳过的数量——重复点一键不产生重复题。
 */
@Data
public class RagToEvalResp {

    /** 本次新生成的题目数 */
    private int generatedCount;

    /** 因已存在被跳过的候选数 */
    private int skippedCount;

    /** 派生题库文件路径（adi.rag.eval.dataset-dir 下的 qa-dataset-derived.json） */
    private String datasetFile;

    /** 新生成题目 id（d-&lt;queryId&gt;） */
    private List<String> generatedIds = new ArrayList<>();
}
