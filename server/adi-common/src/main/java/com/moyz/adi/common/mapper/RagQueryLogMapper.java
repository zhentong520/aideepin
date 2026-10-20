package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.RagQueryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface RagQueryLogMapper extends BaseMapper<RagQueryLog> {

    /**
     * T7-01 五指标聚合 SQL（与 RagQualityDashboardTest / RagQualitySqlIT 同源：IT 原样执行本常量，
     * 仅做 MyBatis `#{name}` → Spring JDBC `:name` 的占位符方言转换，防 Java 侧重算导致口径漂移）。
     * <p>
     * 指标口径（一一对应 RagQualityDashboardResp.QualitySummary）：
     * <ul>
     *   <li>totalQueries：时间范围内检索总次数（软删不计）</li>
     *   <li>hitQueries / hitRate：hit_count &gt; 0 记为命中；命中率 = hitQueries / totalQueries</li>
     *   <li>avgHitCount：AVG(hit_count)，保留命中数（ACL 双保险过滤后、展开前）</li>
     *   <li>avgTopScore：AVG(单次最高分)，仅统计有命中的检索（AVG 跳过 NULL）</li>
     *   <li>lowScoreQueries / lowScoreShare：无命中（top_score IS NULL）或最高分 &lt; 阈值；
     *       占比分母为 totalQueries——「低质检索」单一口径，明细 scope=low_score 与转评测题候选同此</li>
     *   <li>topScoreP50：percentile_cont(0.5)，FILTER 剔除 NULL 后仅统计有命中的检索</li>
     *   <li>elapsedP95：percentile_cont(0.95) of elapsed_ms，尾延迟</li>
     * </ul>
     * top_score 口径：hit_segments（jsonb 数组 [{kb_uuid,segment_id,doc_uuid,score}]）内最大 score；
     * 空数组 / 缺 score 字段均为 NULL。
     */
    String QUALITY_SUMMARY_SQL = """
            WITH q AS (
                SELECT l.hit_count,
                       l.elapsed_ms,
                       CASE WHEN jsonb_array_length(l.hit_segments) > 0
                            THEN (SELECT max((e ->> 'score')::float8)
                                  FROM jsonb_array_elements(l.hit_segments) e)
                            ELSE NULL END AS top_score
                FROM adi_rag_query_log l
                WHERE l.is_deleted = false
                  AND l.create_time >= #{begin}
                  AND l.create_time < #{end}
            )
            SELECT COUNT(*)                                                        AS totalQueries,
                   COUNT(*) FILTER (WHERE hit_count > 0)                           AS hitQueries,
                   COALESCE(AVG(hit_count), 0)                                     AS avgHitCount,
                   COUNT(*) FILTER (WHERE top_score IS NULL
                                        OR top_score < #{lowScoreThreshold})       AS lowScoreQueries,
                   AVG(top_score)                                                  AS avgTopScore,
                   percentile_cont(0.5) WITHIN GROUP (ORDER BY top_score)
                       FILTER (WHERE top_score IS NOT NULL)                        AS topScoreP50,
                   COALESCE(AVG(elapsed_ms), 0)                                    AS avgElapsedMs,
                   percentile_cont(0.95) WITHIN GROUP (ORDER BY elapsed_ms)        AS elapsedP95
            FROM q
            """;

    /**
     * T7-01 按知识库下钻聚合（kb_uuids 是 jsonb 数组，一次检索可命中多个库，
     * 用 jsonb_array_elements_text 展开后每个库各计一行）。
     * <p>列口径与 {@link #QUALITY_SUMMARY_SQL} 相同（不含 P50/P95：下钻看典型值即可）。</p>
     */
    String QUALITY_BY_KB_SQL = """
            WITH q AS (
                SELECT k.kb_uuid                                                   AS kb_uuid,
                       l.hit_count,
                       l.elapsed_ms,
                       CASE WHEN jsonb_array_length(l.hit_segments) > 0
                            THEN (SELECT max((e ->> 'score')::float8)
                                  FROM jsonb_array_elements(l.hit_segments) e)
                            ELSE NULL END AS top_score
                FROM adi_rag_query_log l
                CROSS JOIN LATERAL jsonb_array_elements_text(l.kb_uuids) AS k(kb_uuid)
                WHERE l.is_deleted = false
                  AND l.create_time >= #{begin}
                  AND l.create_time < #{end}
            )
            SELECT kb_uuid                                                          AS kbUuid,
                   COUNT(*)                                                         AS queryCount,
                   COUNT(*) FILTER (WHERE hit_count > 0)                            AS hitQueries,
                   COALESCE(AVG(hit_count), 0)                                      AS avgHitCount,
                   AVG(top_score)                                                   AS avgTopScore,
                   COALESCE(AVG(elapsed_ms), 0)                                     AS avgElapsedMs
            FROM q
            GROUP BY kb_uuid
            ORDER BY queryCount DESC
            """;

    @Select(QUALITY_SUMMARY_SQL)
    Map<String, Object> qualitySummary(@Param("begin") LocalDateTime begin,
                                       @Param("end") LocalDateTime end,
                                       @Param("lowScoreThreshold") double lowScoreThreshold);

    @Select(QUALITY_BY_KB_SQL)
    List<Map<String, Object>> qualityByKb(@Param("begin") LocalDateTime begin,
                                          @Param("end") LocalDateTime end,
                                          @Param("lowScoreThreshold") double lowScoreThreshold);
}
