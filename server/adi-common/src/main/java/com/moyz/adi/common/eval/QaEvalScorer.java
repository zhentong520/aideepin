package com.moyz.adi.common.eval;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 端到端 QA 评测计分器（T6-01 核心，纯函数、离线可测）。
 * <p>
 * 三个聚合指标的定义（全部从逐题真实证据算出，禁止「有内容即通过」）：
 * <ul>
 *   <li><b>accuracy（命中 gold 来源）</b>：非拒答题中，「每条金标片段都被上下文逐字覆盖，
 *       且覆盖它的块的文档归属与题库 sourceDocs 一致」的比例。只覆盖不问来源不算命中——
 *       那会让错误出处混进分数。</li>
 *   <li><b>recall（上下文召回率）</b>：非拒答题全部金标片段的微平均覆盖率
 *       （覆盖片段数之和 / 片段总数之和）。</li>
 *   <li><b>refusalAccuracy（拒答正确率）</b>：全部题目中「拒答判定正确」的比例
 *       （应拒答的拒了 + 不应拒答的答了）。拒答判定：网关显式给出，否则按
 *       无检索结果 / 空答案 / 命中拒答话术推断。</li>
 * </ul>
 * 拒答题不进 accuracy/recall 均值，非拒答题不进拒答均值之外的部分——口径互不污染，
 * 详见 {@link QuestionResult} 字段注释。
 */
public final class QaEvalScorer {

    /**
     * 默认拒答话术：与知识库严格模式「[无答案]」及常见客层拒答措辞对齐。
     * 题库/网关可以扩展，但匹配永远是「答案中出现该子串」——可复核、可单测。
     */
    public static final List<String> DEFAULT_REFUSAL_PATTERNS = List.of(
            "[无答案]", "无答案", "无法回答", "无法从知识库", "知识库中未找到", "未找到相关内容",
            "抱歉，我不知道", "超出知识库范围", "没有相关资料");

    private QaEvalScorer() {
    }

    /**
     * 对一道题评分。{@code result.error} 非空时本题按失败计（recall/accuracy 0、refusal 不正确），
     * 保证基础设施故障不会被静默吞成好看的分数。
     */
    public static QuestionResult score(QaEvalItem item, GatewayResult result, List<String> refusalPatterns) {
        QuestionResult r = new QuestionResult();
        r.setId(item.getId());
        r.setType(item.getType());
        r.setQuestion(item.getQuestion());
        r.setRefusalExpected(item.isRefusalExpected());

        List<RetrievedChunk> chunks = result == null || result.getChunks() == null
                ? List.of() : result.getChunks();
        r.setRetrievedCount(chunks.size());

        if (result != null && StringUtils.isNotBlank(result.getError())) {
            r.setErrored(true);
            r.setDetail("gateway error: " + result.getError());
            r.setSnippetCovered(zeros(item.getExpected().size()));
            r.setContextRecall(item.isRefusalExpected() ? null : 0.0);
            r.setSourceHit(item.isRefusalExpected() ? null : false);
            r.setRefused(false);
            r.setRefusalCorrect(false);
            return r;
        }

        // ---- 拒答判定 ----
        boolean refused = resolveRefused(item, result, chunks, refusalPatterns);
        r.setRefused(refused);
        r.setRefusalCorrect(refused == item.isRefusalExpected());

        // ---- 拒答题到此为止 ----
        if (item.isRefusalExpected()) {
            r.setSnippetCovered(List.of());
            r.setContextRecall(null);
            r.setSourceHit(null);
            return r;
        }

        // ---- 金标覆盖与来源 ----
        List<Boolean> covered = new ArrayList<>(item.getExpected().size());
        List<String> missing = new ArrayList<>();
        boolean allCoveredWithSource = !item.getExpected().isEmpty();
        for (int i = 0; i < item.getExpected().size(); i++) {
            String snippet = item.getExpected().get(i);
            RetrievedChunk covering = coveringChunk(chunks, snippet);
            boolean hit = covering != null;
            covered.add(hit);
            if (!hit) {
                allCoveredWithSource = false;
                missing.add("[" + i + "]" + StringUtils.abbreviate(snippet, 24));
                continue;
            }
            String expectedSource = i < item.getSourceDocs().size() ? item.getSourceDocs().get(i) : null;
            if (StringUtils.isNotBlank(expectedSource)
                    && !titleMatches(covering.getDocTitle(), expectedSource)) {
                allCoveredWithSource = false;
                missing.add("[" + i + "]source:" + expectedSource);
            }
        }
        r.setSnippetCovered(covered);
        int total = item.getExpected().size();
        r.setContextRecall(total == 0 ? 0.0 : covered.stream().filter(Boolean::booleanValue).count() / (double) total);
        r.setSourceHit(allCoveredWithSource);
        if (!missing.isEmpty()) {
            r.setDetail("missing: " + String.join(",", missing));
        }
        return r;
    }

    /**
     * 拒答判定规则（按优先级）：
     * 1. 网关显式给出 refused → 直接采用（生成链路自己知道严格模式是否触发）；
     * 2. 无检索结果且未开启生成 → 判拒答（没有任何证据可以作答）；
     * 3. 答案为空 → 判拒答；
     * 4. 答案命中拒答话术 → 判拒答；
     * 5. 其余 → 未拒答。
     */
    private static boolean resolveRefused(QaEvalItem item, GatewayResult result,
                                          List<RetrievedChunk> chunks, List<String> refusalPatterns) {
        if (result != null && result.getRefused() != null) {
            return result.getRefused();
        }
        String answer = result == null ? null : result.getAnswer();
        if (chunks.isEmpty() && StringUtils.isBlank(answer)) {
            return true;
        }
        if (StringUtils.isBlank(answer)) {
            return false; // 有检索结果但没开生成：拒答性未知，按未拒答计（检索侧评测口径）
        }
        for (String pattern : refusalPatterns) {
            if (StringUtils.isNotBlank(pattern) && answer.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找到正文中逐字包含金标片段的块（两段都 strip 后做 contains；不做模糊匹配，
     * 金标是语料原文，模糊匹配只会制造假阳性）。
     */
    static RetrievedChunk coveringChunk(List<RetrievedChunk> chunks, String snippet) {
        if (StringUtils.isBlank(snippet)) {
            return null;
        }
        String needle = snippet.strip();
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null || StringUtils.isBlank(chunk.getText())) {
                continue;
            }
            if (chunk.getText().strip().contains(needle)) {
                return chunk;
            }
        }
        return null;
    }

    static boolean titleMatches(String docTitle, String expectedSource) {
        if (StringUtils.isBlank(docTitle) || StringUtils.isBlank(expectedSource)) {
            return false;
        }
        // 题库存的是语料路径子串（如 guide/knowledge-base/segment.md），抬头存的是文档标题；
        // 网关负责把抬头映射回语料路径，两边都 strip 后做包含判定
        return docTitle.strip().contains(expectedSource.strip())
                || expectedSource.strip().contains(docTitle.strip());
    }

    private static List<Boolean> zeros(int n) {
        List<Boolean> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(false);
        }
        return list;
    }
}
