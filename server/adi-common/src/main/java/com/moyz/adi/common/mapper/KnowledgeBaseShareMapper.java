package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.KnowledgeBaseShare;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface KnowledgeBaseShareMapper extends BaseMapper<KnowledgeBaseShare> {

    @Select("SELECT * FROM adi_knowledge_base_share WHERE is_deleted=false AND kb_uuid=#{kbUuid}")
    List<KnowledgeBaseShare> listByKb(@Param("kbUuid") String kbUuid);

    @Select("SELECT DISTINCT kb_uuid FROM adi_knowledge_base_share WHERE is_deleted=false AND target_id=#{userId}")
    List<String> listKbUuidsForUser(@Param("userId") Long userId);

    @Delete("DELETE FROM adi_knowledge_base_share WHERE is_deleted=false AND kb_uuid=#{kbUuid} AND target_type=#{targetType} AND target_id=#{targetId}")
    int deleteByKbAndTarget(@Param("kbUuid") String kbUuid, @Param("targetType") String targetType,
                            @Param("targetId") Long targetId);
}
