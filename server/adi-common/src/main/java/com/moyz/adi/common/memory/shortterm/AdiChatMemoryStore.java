package com.moyz.adi.common.memory.shortterm;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

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
}
