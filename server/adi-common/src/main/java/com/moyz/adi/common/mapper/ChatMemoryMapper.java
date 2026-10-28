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
     * 注意：冲突时只覆盖 messages，不触碰 summary 列（T8-02 摘要槽位由专用方法维护）。
     */
    void upsert(@Param("memoryId") String memoryId, @Param("messages") String messages);

    /**
     * T8-02：摘要回写 + 窗口收缩，单条 upsert 原子完成（messages 与 summary 同写，
     * 避免「摘要已落、窗口未缩」的中间态被并发读放大成重复摘要）。
     * messages 为 null 时只更新 summary（防御：正常路径 recent 至少含当前用户消息）。
     */
    void upsertWithSummary(@Param("memoryId") String memoryId,
                           @Param("messages") String messages,
                           @Param("summary") String summary);

    /**
     * Select only the serialized messages column (avoid row overhead on hot read path).
     *
     * @return null when the memory does not exist
     */
    String selectMessages(@Param("memoryId") String memoryId);

    /**
     * T8-02：只取持久化摘要列。
     *
     * @return 摘要文本；行不存在或从未压缩过返回 null
     */
    String selectSummary(@Param("memoryId") String memoryId);

    /**
     * Physical delete (short-term memory has no soft-delete semantics).
     *
     * @return affected row count
     */
    int deleteByMemoryId(@Param("memoryId") String memoryId);
}
