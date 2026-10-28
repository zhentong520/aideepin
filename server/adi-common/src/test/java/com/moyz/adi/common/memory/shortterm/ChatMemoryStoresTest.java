package com.moyz.adi.common.memory.shortterm;

import com.moyz.adi.common.mapper.ChatMemoryMapper;
import com.moyz.adi.common.util.SpringUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S2-04：路由门面与按会话搬迁单测。
 * <p>
 * MapDBChatMemoryStore 是「构造即开文件」的懒加载单例，单测里用 mockStatic 拦截
 * getSingleton()，避免真实文件 IO；migratedPgStore 静态字段在用例间重置。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChatMemoryStoresTest {

    @Mock
    private ChatMemoryMapper chatMemoryMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private PgChatMemoryStore delegate;

    @BeforeEach
    void setUp() {
        delegate = new PgChatMemoryStore();
        ReflectionTestUtils.setField(delegate, "chatMemoryMapper", chatMemoryMapper);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        resetMigratingStore();
    }

    @AfterEach
    void tearDown() {
        resetMigratingStore();
    }

    private static void resetMigratingStore() {
        Field migrating = ReflectionUtils.findField(ChatMemoryStores.class, "migratingPgStore");
        ReflectionUtils.makeAccessible(migrating);
        ReflectionUtils.setField(migrating, null, null);
        Field pgStore = ReflectionUtils.findField(ChatMemoryStores.class, "pgStore");
        ReflectionUtils.makeAccessible(pgStore);
        ReflectionUtils.setField(pgStore, null, null);
    }

    @Test
    void blankOrMapdbConfigRoutesToMapdb() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class)) {
            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn(null);
            assertTrue(ChatMemoryStores.useMapdb());

            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("mapdb");
            assertTrue(ChatMemoryStores.useMapdb());

            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("pg");
            ChatMemoryStores.register(delegate);

            assertFalse(ChatMemoryStores.useMapdb());
        }
    }

    @Test
    void pgHitDoesNotTouchLegacyMapdb() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<MapDBChatMemoryStore> mapdb = mockStatic(MapDBChatMemoryStore.class)) {
            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("pg");
            ChatMemoryStores.register(delegate);


            AdiChatMemoryStore store = ChatMemoryStores.get();
            assertTrue(store instanceof ChatMemoryStores.MigratingPgChatMemoryStore);

            String json = PgChatMemoryStore.serializeFiltered(
                    List.of(dev.langchain4j.data.message.SystemMessage.from("sys"), UserMessage.from("q")));
            when(chatMemoryMapper.selectMessages("m1")).thenReturn(json);

            List<dev.langchain4j.data.message.ChatMessage> messages = store.getMessages("m1");

            assertEquals(2, messages.size());
            mapdb.verifyNoInteractions();
        }
    }

    @Test
    void pgMissMigratesLegacyMemoryAndMarksMigrated() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<MapDBChatMemoryStore> mapdb = mockStatic(MapDBChatMemoryStore.class)) {
            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("pg");
            ChatMemoryStores.register(delegate);


            //legacy mapdb has data, pg is empty
            MapDBChatMemoryStore legacy = mock(MapDBChatMemoryStore.class);
            mapdb.when(MapDBChatMemoryStore::getSingleton).thenReturn(legacy);
            when(legacy.getMessages("m2")).thenReturn(List.of(UserMessage.from("old q"), AiMessage.from("old a")));
            when(chatMemoryMapper.selectMessages("m2")).thenReturn(null);

            AdiChatMemoryStore store = ChatMemoryStores.get();
            List<dev.langchain4j.data.message.ChatMessage> messages = store.getMessages("m2");

            assertEquals(2, messages.size());
            //legacy row upserted into pg and removed from mapdb (marked migrated)
            verify(chatMemoryMapper).upsert(anyString(), anyString());
            verify(legacy).deleteMessages("m2");
        }
    }

    @Test
    void pgMissWithEmptyLegacyStaysEmpty() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<MapDBChatMemoryStore> mapdb = mockStatic(MapDBChatMemoryStore.class)) {
            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("pg");
            ChatMemoryStores.register(delegate);


            MapDBChatMemoryStore legacy = mock(MapDBChatMemoryStore.class);
            mapdb.when(MapDBChatMemoryStore::getSingleton).thenReturn(legacy);
            when(legacy.getMessages("m3")).thenReturn(List.of());
            when(chatMemoryMapper.selectMessages("m3")).thenReturn(null);

            AdiChatMemoryStore store = ChatMemoryStores.get();

            assertTrue(store.getMessages("m3").isEmpty());
            verify(chatMemoryMapper, never()).upsert(anyString(), anyString());
            verify(legacy, never()).deleteMessages(anyString());
        }
    }

    @Test
    void writePathsAlsoDiscardLegacyRow() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<MapDBChatMemoryStore> mapdb = mockStatic(MapDBChatMemoryStore.class)) {
            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("pg");
            ChatMemoryStores.register(delegate);


            MapDBChatMemoryStore legacy = mock(MapDBChatMemoryStore.class);
            mapdb.when(MapDBChatMemoryStore::getSingleton).thenReturn(legacy);

            AdiChatMemoryStore store = ChatMemoryStores.get();
            store.updateMessages("m4", List.of(UserMessage.from("q"), AiMessage.from("a")));
            store.deleteMessages("m4");

            verify(chatMemoryMapper).upsert(anyString(), anyString());
            verify(chatMemoryMapper).deleteByMemoryId("m4");
            //both writes clear the legacy row so a later pg miss can never resurrect it
            verify(legacy, org.mockito.Mockito.times(2)).deleteMessages("m4");
        }
    }

    @Test
    void mapdbModeReturnsLegacySingleton() {
        try (MockedStatic<SpringUtil> spring = mockStatic(SpringUtil.class);
             MockedStatic<MapDBChatMemoryStore> mapdb = mockStatic(MapDBChatMemoryStore.class)) {
            spring.when(() -> SpringUtil.getProperty("adi.memory.store")).thenReturn("mapdb");

            MapDBChatMemoryStore legacy = mock(MapDBChatMemoryStore.class);
            mapdb.when(MapDBChatMemoryStore::getSingleton).thenReturn(legacy);

            assertSame(legacy, ChatMemoryStores.get());
        }
    }
}
