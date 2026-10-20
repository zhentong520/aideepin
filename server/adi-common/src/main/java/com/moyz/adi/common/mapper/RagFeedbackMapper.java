package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.RagFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Map;

@Mapper
public interface RagFeedbackMapper extends BaseMapper<RagFeedback> {

    /**
     * T7-01 反馈统计：时间范围内 👍（PASS）/ 👎（FAIL）计数。
     * <p>SQL 抽成常量：RagQualitySqlIT 原样执行同一份文本，防 IT 与运行时口径漂移。</p>
     */
    String FEEDBACK_SUMMARY_SQL = """
            SELECT COUNT(*) FILTER (WHERE rating = 'PASS') AS passCount,
                   COUNT(*) FILTER (WHERE rating = 'FAIL') AS failCount
            FROM adi_rag_feedback
            WHERE is_deleted = false
              AND create_time >= #{begin}
              AND create_time < #{end}
            """;

    @Select(FEEDBACK_SUMMARY_SQL)
    Map<String, Object> feedbackSummary(@Param("begin") LocalDateTime begin,
                                        @Param("end") LocalDateTime end);
}
