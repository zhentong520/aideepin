package com.moyz.adi.common.memory.shortterm;

import com.moyz.adi.common.util.SpringUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static org.mapdb.Serializer.STRING;

/**
 * Short-Term Memory Storage based on MapDB
 */
@Slf4j
public class MapDBChatMemoryStore implements ChatMemoryStore {

    public static volatile MapDBChatMemoryStore singleton;

    private final DB db;

    private final Map<String, String> map;

    private MapDBChatMemoryStore() {
        String memoryDir = SpringUtil.getProperty("local.chat-memory");
        log.info("chat memory path:{}", memoryDir);
        db = DBMaker.fileDB(memoryDir + "chat-memory.db").transactionEnable().make();
        map = db.hashMap("messages", STRING, STRING).createOrOpen();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = map.get((String) memoryId);
        return messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<ChatMessage> copy = new ArrayList<>(messages);
        //AiMessage in first position is not allow
        if (!copy.isEmpty() && copy.get(0) instanceof AiMessage) {
            copy.remove(0);
        }
        if (copy.isEmpty()) {
            return;
        }
        //Filter out the available messages.(UserMessage,AiMessage)
        List<ChatMessage> availableMessage = new ArrayList<>();
        int index = 0;
        if (copy.get(0) instanceof SystemMessage) {
            availableMessage.add(copy.get(0));
            index = 1;
        }
        for (int i = index; i < copy.size(); i++) {
            ChatMessage chatMessage = copy.get(i);
            if (!(chatMessage instanceof SystemMessage)) {
                availableMessage.add(chatMessage);
            }
        }
        String json = messagesToJson(availableMessage);
        map.put((String) memoryId, json);
        db.commit();
    }

    /**
     * 用最新的系统提示词覆盖已持久化的那份。
     * <p>
     * Refresh the persisted system message with the latest one.
     * </p>
     * <p>
     * 二开（2026-10-05）：本存储只保留 index 0 的 SystemMessage，而 langchain4j 的
     * {@code alwaysKeepSystemMessageFirst} 默认关闭时会把新 SystemMessage 追加到末尾，
     * 于是历史里那份旧的（可能不含身份护栏）会被长期固化，导致改人设/加护栏对老会话不生效。
     * 这里在每轮请求前用当前提示词覆盖，保证系统提示词永远是最新的一份。
     * </p>
     */
    public void refreshSystemMessage(Object memoryId, String systemMessage) {
        String key = (String) memoryId;
        if (null == key) {
            return;
        }
        try {
            List<ChatMessage> existing = getMessages(memoryId);
            if (existing == null || existing.isEmpty()) {
                return;
            }
            ChatMessage first = existing.get(0);
            if (first instanceof SystemMessage stored) {
                if (StringUtils.equals(stored.text(), systemMessage)) {
                    return;
                }
            }
            List<ChatMessage> updated = new ArrayList<>();
            if (StringUtils.isNotBlank(systemMessage)) {
                updated.add(SystemMessage.from(systemMessage));
            }
            existing.stream().filter(msg -> !(msg instanceof SystemMessage)).forEach(updated::add);
            map.put(key, messagesToJson(updated));
            db.commit();
        } catch (Exception e) {
            log.warn("Failed to refresh system message in short-term memory, memoryId:{}", memoryId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        map.remove((String) memoryId);
        db.commit();
    }

    @PreDestroy
    public void destroy() {
        if (!db.isClosed()) {
            db.close();
            log.info("MapDB chat memory store closed");
        }
    }

    public static MapDBChatMemoryStore getSingleton() {
        if (null == singleton) {
            synchronized (MapDBChatMemoryStore.class) {
                if (null == singleton) {
                    singleton = new MapDBChatMemoryStore();
                }
            }
        }
        return singleton;
    }
}
