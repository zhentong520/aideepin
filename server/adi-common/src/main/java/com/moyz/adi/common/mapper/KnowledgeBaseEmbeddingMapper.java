package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.KnowledgeBaseEmbedding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseEmbeddingMapper extends BaseMapper<KnowledgeBaseEmbedding> {

    boolean deleteByItemUuid(@Param("kbItemUuid") String uuid, @Param("tableSuffix") String tableSuffix);

    boolean deleteByIds(@Param("ids") List<String> ids, @Param("tableSuffix") String tableSuffix);

    /**
     * S1-15: delete every vector of a knowledge base (KB delete cascade), scoped by the
     * metadata kb_uuid key instead of one deleteByItemUuid call per document.
     */
    boolean deleteByKbUuid(@Param("kbUuid") String kbUuid, @Param("tableSuffix") String tableSuffix);

    /**
     * S1-15 reconciliation: delete one batch (LIMIT #{batchSize}) of orphan vectors — rows
     * whose embedding_id is referenced by no live (not soft-deleted) row of any of the three
     * segment tables (main segment / question / child chunk). Returns the number deleted;
     * callers loop until 0.
     */
    int deleteOrphanBatch(@Param("tableSuffix") String tableSuffix, @Param("batchSize") int batchSize);
}
