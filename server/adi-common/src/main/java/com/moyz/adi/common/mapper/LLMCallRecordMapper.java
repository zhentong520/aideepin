package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.LLMCallRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LLMCallRecordMapper extends BaseMapper<LLMCallRecord> {

    /**
     * T6-13 按模型聚合 token 与调用量。
     * <p>聚合列均为 SQL 计算列，返回 Map 由 Java 侧转 DTO。</p>
     */
    @Select("""
            SELECT model_platform                                             AS modelPlatform,
                   model_name                                                 AS modelName,
                   COUNT(*)                                                   AS callCount,
                   COALESCE(SUM(input_tokens), 0)                             AS inputTokens,
                   COALESCE(SUM(output_tokens), 0)                            AS outputTokens,
                   COALESCE(SUM(duration), 0)                                 AS durationMs
            FROM adi_llm_call_record
            WHERE is_deleted = false
              AND request_time >= #{begin}
              AND request_time < #{end}
            GROUP BY model_platform, model_name
            """)
    List<Map<String, Object>> aggregateByModel(@Param("begin") LocalDateTime begin,
                                               @Param("end") LocalDateTime end);

    /**
     * T6-13 按来源渠道（Agent 名或 source_type）聚合。
     * <p>(source_type, source_agent) 分组后在 Java 侧归并为单一渠道名。</p>
     */
    @Select("""
            SELECT source_type                                                AS sourceType,
                   source_agent                                               AS sourceAgent,
                   COUNT(*)                                                   AS callCount,
                   COALESCE(SUM(input_tokens), 0)                             AS inputTokens,
                   COALESCE(SUM(output_tokens), 0)                            AS outputTokens
            FROM adi_llm_call_record
            WHERE is_deleted = false
              AND request_time >= #{begin}
              AND request_time < #{end}
            GROUP BY source_type, source_agent
            """)
    List<Map<String, Object>> aggregateByChannel(@Param("begin") LocalDateTime begin,
                                                 @Param("end") LocalDateTime end);
}
