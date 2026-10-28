package com.moyz.adi.common.memory.shortterm;

import com.moyz.adi.common.mapper.ChatMemoryMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2-04：PgChatMemoryStore 单测（mock mapper + redis，真库 upsert 语义由
 * adi-integration-test 的 Schema 迁移测试兜底）。
 */
@ExtendWith(MockitoExtension.class)
class PgChatMemoryStoreTest {

    private static final String MEMORY_ID = "char-uuid-1";

    @Mock
    private ChatMemoryMapper chatMemoryMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PgChatMemoryStore store;

    @BeforeEach
    void setUp() {
        store = new PgChatMemoryStore();
        ReflectionTestUtils.setField(store, "chatMemoryMapper", chatMemoryMapper);
        ReflectionTestUtils.setField(store, "stringRedisTemplate", stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void cacheHitSkipsDb() {
        String json = "[{\"type\":\"AI\",\"text\":\"hi\"}]";
        when(valueOperations.get(PgChatMemoryStore.cacheKey(MEMORY_ID))).thenReturn(json);

        List<ChatMessage> messages = store.getMessages(MEMORY_ID);

        assertEquals(1, messages.size());
        verify(chatMemoryMapper, never()).selectMessages(anyString());
    }

    @Test
    void cacheMissFallsBackToDbAndRefills() {
        String json = "[{\"type\":\"AI\",\"text\":\"hi\"}]";
        when(valueOperations.get(PgChatMemoryStore.cacheKey(MEMORY_ID))).thenReturn(null);
        when(chatMemoryMapper.selectMessages(MEMORY_ID)).thenReturn(json);

        List<ChatMessage> messages = store.getMessages(MEMORY_ID);

        assertEquals(1, messages.size());
        verify(valueOperations).set(eq(PgChatMemoryStore.cacheKey(MEMORY_ID)), eq(json), eq(PgChatMemoryStore.CACHE_TTL));
    }

    @Test
    void unknownMemoryReturnsEmptyList() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatMemoryMapper.selectMessages(MEMORY_ID)).thenReturn(null);

        List<ChatMessage> messages = store.getMessages(MEMORY_ID);

        assertTrue(messages.isEmpty());
        verify(valueOperations, never()).set(anyString(), anyString(), eq(PgChatMemoryStore.CACHE_TTL));
    }

    @Test
    void redisReadFailureDegradesToDb() {
        String json = "[{\"type\":\"AI\",\"text\":\"hi\"}]";
        when(valueOperations.get(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(chatMemoryMapper.selectMessages(MEMORY_ID)).thenReturn(json);

        List<ChatMessage> messages = store.getMessages(MEMORY_ID);

        assertEquals(1, messages.size());
    }

    @Test
    void updateFiltersLeadingAiAndDuplicatedSystemMessages() {
        List<ChatMessage> input = List.of(
                AiMessage.from("should be dropped (leading AiMessage)"),
                SystemMessage.from("sys-2 kept (index 0)"),
                UserMessage.from("q"),
                AiMessage.from("a"),
                SystemMessage.from("sys-3 should be dropped")
        );

        String json = PgChatMemoryStore.serializeFiltered(input);

        List<ChatMessage> roundTrip = PgChatMemoryStore.deserialize(json);
        //MapDB 原语义平移：首位 SystemMessage 保留，其余 SystemMessage 全部丢弃
        assertEquals(3, roundTrip.size());
        assertTrue(roundTrip.get(0) instanceof SystemMessage);
        assertEquals("sys-2 kept (index 0)", ((SystemMessage) roundTrip.get(0)).text());
        assertTrue(roundTrip.get(1) instanceof UserMessage);
        assertTrue(roundTrip.get(2) instanceof AiMessage);
    }

    @Test
    void updateEmptyWindowWritesNothing() {
        assertNull(PgChatMemoryStore.serializeFiltered(List.of(AiMessage.from("only message"))));
        assertNull(PgChatMemoryStore.serializeFiltered(List.of()));
    }

    @Test
    void updateWritesThroughCache() {
        List<ChatMessage> input = List.of(UserMessage.from("q"), AiMessage.from("a"));

        store.updateMessages(MEMORY_ID, input);

        verify(chatMemoryMapper).upsert(eq(MEMORY_ID), anyString());
        verify(valueOperations).set(eq(PgChatMemoryStore.cacheKey(MEMORY_ID)), anyString(), eq(PgChatMemoryStore.CACHE_TTL));
    }

    @Test
    void deleteRemovesRowAndCache() {
        store.deleteMessages(MEMORY_ID);

        verify(chatMemoryMapper).deleteByMemoryId(MEMORY_ID);
        verify(stringRedisTemplate).delete(PgChatMemoryStore.cacheKey(MEMORY_ID));
    }

    @Test
    void refreshSystemMessageSkipsWhenUnchanged() {
        String json = PgChatMemoryStore.serializeFiltered(
                List.of(SystemMessage.from("sys"), UserMessage.from("q")));
        when(valueOperations.get(PgChatMemoryStore.cacheKey(MEMORY_ID))).thenReturn(json);

        store.refreshSystemMessage(MEMORY_ID, "sys");

        verify(chatMemoryMapper, never()).upsert(anyString(), anyString());
    }

    @Test
    void refreshSystemMessageReplacesStoredCopy() {
        String json = PgChatMemoryStore.serializeFiltered(
                List.of(SystemMessage.from("old guard"), UserMessage.from("q")));
        when(valueOperations.get(PgChatMemoryStore.cacheKey(MEMORY_ID))).thenReturn(json);

        store.refreshSystemMessage(MEMORY_ID, "new guard");

        verify(chatMemoryMapper).upsert(eq(MEMORY_ID), anyString());
    }

    @Test
    void deserializeToleratesEmptyJson() {
        assertTrue(PgChatMemoryStore.deserialize(null).isEmpty());
        assertTrue(PgChatMemoryStore.deserialize("").isEmpty());
    }

    // ==================== T8-02：压缩摘要持久化槽位 ====================

    @Test
    void supportsSummary() {
        assertTrue(store.supportsSummary(), "pg 实现必须声明支持摘要槽位（MapDB 默认 false）");
    }

    @Test
    void getSummaryCacheHitSkipsDb() {
        when(valueOperations.get(PgChatMemoryStore.summaryCacheKey(MEMORY_ID))).thenReturn("旧摘要");

        assertEquals("旧摘要", store.getSummary(MEMORY_ID));
        verify(chatMemoryMapper, never()).selectSummary(anyString());
    }

    @Test
    void getSummaryMissFallsBackToDbAndRefills() {
        when(valueOperations.get(PgChatMemoryStore.summaryCacheKey(MEMORY_ID))).thenReturn(null);
        when(chatMemoryMapper.selectSummary(MEMORY_ID)).thenReturn("旧摘要");

        assertEquals("旧摘要", store.getSummary(MEMORY_ID));
        verify(valueOperations).set(eq(PgChatMemoryStore.summaryCacheKey(MEMORY_ID)), eq("旧摘要"), eq(PgChatMemoryStore.CACHE_TTL));
    }

    @Test
    void getSummaryAbsentReturnsNull() {
        when(valueOperations.get(anyString())).thenReturn(null);
        when(chatMemoryMapper.selectSummary(MEMORY_ID)).thenReturn(null);

        assertNull(store.getSummary(MEMORY_ID));
    }

    @Test
    void saveSummaryAndShrinkWritesMapperAndBothCaches() {
        List<ChatMessage> remaining = List.of(SystemMessage.from("sys"), UserMessage.from("q"), AiMessage.from("a"));

        store.saveSummaryAndShrink(MEMORY_ID, "合并摘要", remaining);

        //messages 与 summary 单条 upsert 原子完成
        verify(chatMemoryMapper).upsertWithSummary(eq(MEMORY_ID), anyString(), eq("合并摘要"));
        verify(valueOperations).set(eq(PgChatMemoryStore.summaryCacheKey(MEMORY_ID)), eq("合并摘要"), eq(PgChatMemoryStore.CACHE_TTL));
        verify(valueOperations).set(eq(PgChatMemoryStore.cacheKey(MEMORY_ID)), anyString(), eq(PgChatMemoryStore.CACHE_TTL));
    }

    @Test
    void saveSummaryBlankIsNoop() {
        store.saveSummaryAndShrink(MEMORY_ID, " ", List.of(UserMessage.from("q")));

        verify(chatMemoryMapper, never()).upsertWithSummary(anyString(), anyString(), anyString());
    }
}
