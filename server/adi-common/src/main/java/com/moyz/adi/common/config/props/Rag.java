package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

/**
 * 二开新增（T7-01，2026-10-20）：RAG 质量看板与失败闭环配置。
 * <p>
 * RAG quality dashboard and failure-loop settings (T7-01).
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Rag} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Rag {

    /**
     * 低分阈值（adi.rag.eval.low-score-threshold）：单次检索最高分低于该值计为「低分」。
     * <p>
     * 用于质量看板低分占比、明细表 low_score 筛选、转评测题候选范围（三处同口径）。
     * 与知识库检索的 minScore 是两个独立参数：minScore 是入库检索时的召回过滤线，
     * 本阈值只用于质量评估视角，改它不影响任何检索行为。
     * <p>
     * Low-score threshold for the top score of one retrieval. Shared by the dashboard
     * low-score share, the detail low_score filter and the to-eval candidate scope.
     * Independent of the retrieval minScore: changing it never affects retrieval.
     */
    @DecimalMin(value = "0.0", message = "adi.rag.eval.low-score-threshold 应 >= 0")
    @DecimalMax(value = "1.0", message = "adi.rag.eval.low-score-threshold 应 <= 1")
    private double lowScoreThreshold = 0.5;

    /**
     * 评测题库目录（adi.rag.eval.dataset-dir）：失败案例转评测题写入
     * {@code <dir>/qa-dataset-derived.json}（与 T6-01 的 qa-dataset.json 同目录、同结构）。
     * <p>
     * ⚠️ 生产部署必须改成绝对路径：默认值是相对路径，只有工作目录为仓库根时才成立。
     * 目录不存在时转评测题接口报错退出，不会静默写丢。
     * <p>
     * Directory holding the QA eval datasets. Derived questions are appended to
     * qa-dataset-derived.json under it. MUST be an absolute path in production;
     * a missing directory fails the to-eval call instead of silently dropping data.
     */
    private String evalDatasetDir = "server/dev-scripts/eval";
}
