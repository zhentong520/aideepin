package com.moyz.adi.common.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * T7-01 RAG 质量看板响应。
 * <p>
 * ⚠️ 语义约定（铁律：数据源故障显示「数据不可用」，不显示 0——0 会被误读为「检索质量完美」）：
 * <ul>
 *   <li>{@code OK}：查询正常，summary 携带五个质量指标（详见 {@link QualitySummary} 字段注释）</li>
 *   <li>{@code NO_DATA}：查询正常但时间范围内无任何检索记录（等价 OK+empty，独立枚举便于前端区分文案）</li>
 *   <li>{@code UNAVAILABLE}：数据源故障（聚合 SQL 抛错），此时不返回任何指标数字，前端显示「数据不可用」</li>
 * </ul>
 */
@Data
public class RagQualityDashboardResp {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_NO_DATA = "NO_DATA";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /** OK / NO_DATA / UNAVAILABLE */
    private String status;

    /** 故障原因（status=UNAVAILABLE 时有值） */
    private String reason;

    /** 五个质量指标 + 支撑计数 */
    private QualitySummary summary = new QualitySummary();

    /** 按知识库下钻（一条 kb 一行，按检索次数降序） */
    private List<KbQualityItem> byKb = new ArrayList<>();

    /** 👍👎 反馈统计（同一时间范围） */
    private FeedbackSummary feedback = new FeedbackSummary();

    /**
     * 五个 headline 指标（口径与 SQL 见 RagQueryLogMapper.QUALITY_SUMMARY_SQL 注释）：
     * <ol>
     *   <li>hitRate 命中率：hit_count &gt; 0 的检索占比（%），0 命中 = 知识库没召回到任何片段</li>
     *   <li>avgHitCount 平均命中文档数：AVG(hit_count)，保留命中数（ACL 双保险过滤后、展开前）</li>
     *   <li>avgTopScore 平均 Top 分数：AVG(单次检索最高分)，仅统计有命中的检索；无命中检索不参与（NULL 不计）</li>
     *   <li>lowScoreShare 低分占比：无命中或最高分 &lt; lowScoreThreshold 的检索占比（%）——「低质检索」单一口径，
     *       与明细表 scope=low_score、转评测题候选范围同口径</li>
     *   <li>elapsedP95 耗时 P95：percentile_cont(0.95) of elapsed_ms，尾延迟（均值会被长尾平均掉）</li>
     * </ol>
     */
    @Data
    public static class QualitySummary {

        /** 时间范围内检索总次数 */
        private Long totalQueries = 0L;

        /** 命中（hit_count > 0）次数 */
        private Long hitQueries = 0L;

        /** 低质检索次数（无命中或最高分低于阈值） */
        private Long lowScoreQueries = 0L;

        /** 命中率（%）；totalQueries=0 时为 null（不展示 0） */
        private Double hitRate;

        /** 平均命中文档数 */
        private Double avgHitCount;

        /** 平均 Top 分数；无命中检索时为 null（不展示 0） */
        private Double avgTopScore;

        /** 低分占比（%）；totalQueries=0 时为 null（不展示 0） */
        private Double lowScoreShare;

        /** 耗时 P95（毫秒） */
        private Double elapsedP95;

        /** Top 分数中位数 P50（仅统计有命中的检索；对离群低分不敏感，看典型质量） */
        private Double topScoreP50;

        /** 平均耗时（毫秒） */
        private Double avgElapsedMs;

        /** 本次聚合使用的低分阈值（adi.rag.eval.low-score-threshold） */
        private Double lowScoreThreshold;
    }

    /** 按知识库下钻行 */
    @Data
    public static class KbQualityItem {
        private String kbUuid;
        /** 知识库标题（best-effort 解析，失败回落 kbUuid） */
        private String kbTitle;
        private Long queryCount = 0L;
        private Long hitQueries = 0L;
        private Double hitRate;
        private Double avgHitCount;
        private Double avgTopScore;
        private Double avgElapsedMs;
    }

    /** 👍👎 反馈统计 */
    @Data
    public static class FeedbackSummary {
        private Long passCount = 0L;
        private Long failCount = 0L;
    }
}
