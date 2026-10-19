package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.vo.KeywordSegmentHit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentSegmentMapper extends BaseMapper<DocumentSegment> {

    /**
     * 按向量库条目id批量累加命中次数（text 模式段）
     */
    void incrementHitCountByEmbeddingIds(@Param("embeddingIds") List<String> embeddingIds);

    /**
     * 按段id批量累加命中次数（qa 答案 / parent_child 父段的传导累加）
     */
    void incrementHitCountByIds(@Param("ids") List<Long> ids);

    /**
     * 关键词路召回（二开新增，2026-09-15）：基于 bigram 写入的 search_tsv 做全文检索。
     * <p>
     * 只查 adi_document_segment 主表——向量路展开后的内容同样来自该表（qa 取答案行、
     * parent_child 上卷父段），两路内容同质，RRF 融合时可直接按文本去重合并。
     * <p>
     * 注意：search_tsv 为空（存量未重建索引）时该查询自然不命中，等价于关闭关键词路。
     */
    @Select("""
            select id, uuid, content,
                   ts_rank(search_tsv, to_tsquery('simple', #{tsQuery})) as score
            from adi_document_segment
            where kb_uuid = #{kbUuid}
              and is_deleted = false
              and is_enabled = true
              and search_tsv @@ to_tsquery('simple', #{tsQuery})
            order by score desc
            limit #{limit}
            """)
    List<KeywordSegmentHit> keywordSearch(@Param("kbUuid") String kbUuid,
                                          @Param("tsQuery") String tsQuery,
                                          @Param("limit") int limit);

    /**
     * 关键词路召回（排除停用文档）。
     * <p>
     * 与 {@link #keywordSearch} 的唯一差别是排除 {@code excludedDocUuids} 里的文档：
     * 向量路通过 metadata 过滤停用文档，关键词路直查分段表，必须显式带上同一份名单，
     * 否则停用文档的分段仍会被召回。名单为空时走不带 NOT IN 的分支，避免空集合生成非法 SQL。
     */
    @Select("""
            <script>
            select id, uuid, content,
                   ts_rank(search_tsv, to_tsquery('simple', #{tsQuery})) as score
            from adi_document_segment
            where kb_uuid = #{kbUuid}
              and is_deleted = false
              and is_enabled = true
              and search_tsv @@ to_tsquery('simple', #{tsQuery})
            <if test="excludedDocUuids != null and excludedDocUuids.size() > 0">
              and doc_uuid not in
              <foreach collection="excludedDocUuids" item="d" open="(" separator="," close=")">#{d}</foreach>
            </if>
            order by score desc
            limit #{limit}
            </script>
            """)
    List<KeywordSegmentHit> keywordSearchExcluding(@Param("kbUuid") String kbUuid,
                                                   @Param("tsQuery") String tsQuery,
                                                   @Param("limit") int limit,
                                                   @Param("excludedDocUuids") java.util.Set<String> excludedDocUuids);
}
