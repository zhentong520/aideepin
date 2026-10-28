package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.ChatMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChatMemoryMapper extends BaseMapper<ChatMemory> {

    /**
     * Upsert the serialized message window (S2-04).
     * <p>
     * langchain4j 的 ChatMemoryStore 语义是「每轮全量覆盖」，last-write-wins 即正确行为；
     * 多实例并发写同一会话时后写覆盖先写（与 MapDB 单机语义一致，不会产生重复消息）。
     */
    void upsert(@Param("memoryId") String memoryId, @Param("messages") String messages);

    /**
     * Select only the serialized messages column (avoid row overhead on hot read path).
     *
     * @return null when the memory does not exist
     */
    String selectMessages(@Param("memoryId") String memoryId);

    /**
     * Physical delete (short-term memory has no soft-delete semantics).
     *
     * @return affected row count
     */
    int deleteByMemoryId(@Param("memoryId") String memoryId);
}
