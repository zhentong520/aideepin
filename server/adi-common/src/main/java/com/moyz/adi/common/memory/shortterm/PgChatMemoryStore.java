package com.moyz.adi.common.memory.shortterm;

import com.moyz.adi.common.constant.RedisKeyConstant;
import com.moyz.adi.common.entity.ChatMemory;
import com.moyz.adi.common.mapper.ChatMemoryMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * Postgres 短期记忆存储（S2-04/ADR-003）：一行一会话，替代 MapDB 单机文件，
 * 多实例共享、重启不丢。
 * <p>
 * 读写路径加 Redis 缓存（写穿透，TTL {@link #CACHE_TTL}）：聊天每轮至少读 2 次记忆
 * （token 预估 + LLM 组装），热 key 命中内存网络一跳。所有 Redis 操作 try/catch 降级
 * 直查库（S1-10 治理约定：Redis 故障不得放大为聊天 500）。
 */
@Slf4j
@Component
public class PgChatMemoryStore implements AdiChatMemoryStore {

    static final Duration CACHE_TTL = Duration.ofMinutes(30);

    @Resource
    private ChatMemoryMapper chatMemoryMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * S2-04：向 {@link ChatMemoryStores} 静态门面自注册（CompositeRag 等非 Spring
     * 调用方通过门面拿实例；不走 SpringUtil.getBean，遵守 S1-01 服务定位器冻结规则）。
     */
    @PostConstruct
    public void registerSelf() {
        ChatMemoryStores.register(this);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = (String) memoryId;
        if (null == key) {
            return new ArrayList<>();
        }
        //1. cache hit
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey(key));
            if (null != cached) {
                return deserialize(cached);
            }
        } catch (Exception e) {
            log.warn("chat memory cache read failed, fallback to db, memoryId:{}", key, e);
        }
        //2. db + cache-aside refill
        String json = chatMemoryMapper.selectMessages(key);
        if (StringUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        try {
            stringRedisTemplate.opsForValue().set(cacheKey(key), json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("chat memory cache refill failed (non-fatal), memoryId:{}", key, e);
        }
        return deserialize(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = (String) memoryId;
        if (null == key) {
            return;
        }
        String json = serializeFiltered(messages);
        if (null == json) {
            return;
        }
        chatMemoryMapper.upsert(key, json);
        //write-through（缓存失败不影响主流程：TTL 30min 内自然过期回源）
        try {
            stringRedisTemplate.opsForValue().set(cacheKey(key), json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("chat memory cache write-through failed (non-fatal), memoryId:{}", key, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = (String) memoryId;
        if (null == key) {
            return;
        }
        chatMemoryMapper.deleteByMemoryId(key);
        try {
            stringRedisTemplate.delete(cacheKey(key));
        } catch (Exception e) {
            log.warn("chat memory cache evict failed (non-fatal), memoryId:{}", key, e);
        }
    }

    /**
     * 原样导入一份已序列化的消息窗口（S2-04 搬迁专用）：不做过滤（MapDB 侧
     * 写入时已过滤），直接 upsert + 写穿透缓存。
     */
    public void importRaw(String memoryId, String json) {
        if (StringUtils.isEmpty(json)) {
            return;
        }
        chatMemoryMapper.upsert(memoryId, json);
        try {
            stringRedisTemplate.opsForValue().set(cacheKey(memoryId), json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("chat memory cache write-through failed (non-fatal), memoryId:{}", memoryId, e);
        }
    }

    @Override
    public void refreshSystemMessage(Object memoryId, String systemMessage) {
        String key = (String) memoryId;
        if (null == key) {
            return;
        }
        try {
            List<ChatMessage> existing = getMessages(memoryId);
            if (existing.isEmpty()) {
                return;
            }
            ChatMessage first = existing.get(0);
            if (first instanceof SystemMessage stored && StringUtils.equals(stored.text(), systemMessage)) {
                return;
            }
            List<ChatMessage> updated = new ArrayList<>();
            if (StringUtils.isNotBlank(systemMessage)) {
                updated.add(SystemMessage.from(systemMessage));
            }
            existing.stream().filter(msg -> !(msg instanceof SystemMessage)).forEach(updated::add);
            updateMessages(memoryId, updated);
        } catch (Exception e) {
            log.warn("Failed to refresh system message in short-term memory, memoryId:{}", memoryId, e);
        }
    }

    // ==================== T8-02：压缩摘要持久化槽位 ====================

    @Override
    public boolean supportsSummary() {
        return true;
    }

    @Override
    public String getSummary(Object memoryId) {
        String key = (String) memoryId;
        if (null == key) {
            return null;
        }
        //1. cache hit（与 messages 缓存同 TTL、同降级约定）
        try {
            String cached = stringRedisTemplate.opsForValue().get(summaryCacheKey(key));
            if (null != cached) {
                return StringUtils.isBlank(cached) ? null : cached;
            }
        } catch (Exception e) {
            log.warn("chat memory summary cache read failed, fallback to db, memoryId:{}", key, e);
        }
        //2. db + cache-aside refill
        String summary = chatMemoryMapper.selectSummary(key);
        if (StringUtils.isBlank(summary)) {
            return null;
        }
        try {
            stringRedisTemplate.opsForValue().set(summaryCacheKey(key), summary, CACHE_TTL);
        } catch (Exception e) {
            log.warn("chat memory summary cache refill failed (non-fatal), memoryId:{}", key, e);
        }
        return summary;
    }

    @Override
    public void saveSummaryAndShrink(Object memoryId, String summary, List<ChatMessage> remainingMessages) {
        String key = (String) memoryId;
        if (null == key || StringUtils.isBlank(summary)) {
            return;
        }
        String json = serializeFiltered(remainingMessages);
        //messages 为 null 时 SQL 内 COALESCE 保住现有窗口（防御，正常路径 recent 非空）
        chatMemoryMapper.upsertWithSummary(key, json, summary);
        //write-through 双缓存，失败降级靠 TTL 过期回源
        try {
            stringRedisTemplate.opsForValue().set(summaryCacheKey(key), summary, CACHE_TTL);
            if (null != json) {
                stringRedisTemplate.opsForValue().set(cacheKey(key), json, CACHE_TTL);
            }
        } catch (Exception e) {
            log.warn("chat memory summary write-through failed (non-fatal), memoryId:{}", key, e);
        }
    }

    /**
     * 序列化 + 与 MapDB 版本对齐的过滤规则（行为平移，保证搬迁前后记忆窗口一致）：
     * ① 首位 AiMessage 移除（langchain4j 历史约定）；② SystemMessage 只保留 index 0。
     *
     * @return null 表示过滤后为空窗口，无需落库
     */
    static String serializeFiltered(List<ChatMessage> messages) {
        List<ChatMessage> copy = new ArrayList<>(messages);
        if (!copy.isEmpty() && copy.get(0) instanceof AiMessage) {
            copy.remove(0);
        }
        if (copy.isEmpty()) {
            return null;
        }
        List<ChatMessage> available = new ArrayList<>();
        int index = 0;
        if (copy.get(0) instanceof SystemMessage) {
            available.add(copy.get(0));
            index = 1;
        }
        for (int i = index; i < copy.size(); i++) {
            ChatMessage chatMessage = copy.get(i);
            if (!(chatMessage instanceof SystemMessage)) {
                available.add(chatMessage);
            }
        }
        return messagesToJson(available);
    }

    /**
     * null/空 JSON（不存在或刚清空的会话）安全反序列化——新会话首轮 getMessages 是常态路径。
     */
    static List<ChatMessage> deserialize(String json) {
        if (StringUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        List<ChatMessage> messages = messagesFromJson(json);
        return null == messages ? new ArrayList<>() : messages;
    }

    static String cacheKey(String memoryId) {
        return RedisKeyConstant.CHAT_MEMORY + memoryId;
    }

    static String summaryCacheKey(String memoryId) {
        return RedisKeyConstant.CHAT_MEMORY_SUMMARY + memoryId;
    }
}
