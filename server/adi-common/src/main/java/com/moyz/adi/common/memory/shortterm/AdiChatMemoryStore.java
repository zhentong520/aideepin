package com.moyz.adi.common.memory.shortterm;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;

import java.util.List;

/**
 * Adi 统一短期记忆存储契约（S2-04）。
 * <p>
 * langchain4j 的 {@link ChatMemoryStore} 只有整列表读写三方法；本项目额外依赖
 * {@link #refreshSystemMessage}（系统提示词每轮覆盖，身份护栏不失效），因此把它
 * 提升为接口方法，让 MapDB（遗留）与 Pg（目标）两种实现可互换。
 */
public interface AdiChatMemoryStore extends ChatMemoryStore {

    /**
     * 用最新的系统提示词覆盖已持久化的那份（见 {@link MapDBChatMemoryStore} 的二开说明）。
     */
    void refreshSystemMessage(Object memoryId, String systemMessage);

    // ==================== T8-02：压缩摘要持久化槽位 ====================

    /**
     * 是否支持摘要槽位（T8-02）。MapDB 遗留实现返回 false——调用方
     * （AbstractLLMService）据此决定是否做「摘要回写 + 窗口收缩」：
     * 不支持时保持 T6-06 原行为（压缩仅在内存副本上进行、每轮重摘要），
     * 绝不能出现「窗口已缩、摘要丢失」的上下文损失。
     */
    default boolean supportsSummary() {
        return false;
    }

    /**
     * 读持久化摘要。
     *
     * @return 摘要文本；无摘要（从未压缩/行不存在/实现不支持）返回 null
     */
    default String getSummary(Object memoryId) {
        return null;
    }

    /**
     * 摘要回写 + 窗口收缩（同一次 upsert 原子完成）：
     * summary 覆盖已被摘要、且从 remainingMessages 中移除的旧历史。
     *
     * @param summary           合并后的摘要纯文本（不含 [CONVERSATION_SUMMARY] 标记）
     * @param remainingMessages 收缩后的消息窗口（index 0 为系统消息 + 未被摘要的最近消息）
     */
    default void saveSummaryAndShrink(Object memoryId, String summary, List<ChatMessage> remainingMessages) {
    }
}
