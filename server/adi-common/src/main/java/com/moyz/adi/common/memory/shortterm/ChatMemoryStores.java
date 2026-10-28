package com.moyz.adi.common.memory.shortterm;

import com.moyz.adi.common.util.SpringUtil;
import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

/**
 * 短期记忆存储门面（S2-04/ADR-003）：按 {@code adi.memory.store} 在
 * MapDB（遗留单机文件）与 Postgres（目标实现）间路由，调用方无感知。
 * <p>
 * 路由约定：
 * <ul>
 *   <li>未配置 / {@code mapdb} → 行为与历史版本完全一致（回滚安全位）；</li>
 *   <li>{@code pg} → Postgres 存储 + <b>按会话灰度搬迁</b>：pg 读 miss 时检查 MapDB
 *       遗留数据，有则整体搬迁到 pg 并删除 MapDB 键（标记已迁）——不整库一次性迁移，
 *       会话被再次触及即自动搬迁，MapDB 单例是懒加载的，未被触及的会话零成本。</li>
 * </ul>
 * <p>
 * 为什么是静态门面而非注入 bean：调用方 {@code CompositeRag} 是每次 new 的普通类
 * （非 Spring bean），静态门面与既有 {@code MapDBChatMemoryStore.getSingleton()}
 * 调用模式同构，替换面最小。
 */
@Slf4j
public final class ChatMemoryStores {

    private static volatile AdiChatMemoryStore migratingPgStore;

    private static volatile PgChatMemoryStore pgStore;

    private ChatMemoryStores() {
    }

    /**
     * 启动期自注册入口（由 {@link PgChatMemoryStore} 的 @PostConstruct 调用）。
     * <p>
     * 不走 {@code SpringUtil.getBean}：S1-01 架构规则冻结了服务定位器存量并禁止新代码
     * 使用（WholeAppArchitectureTest#noNewSpringUtilServiceLocator），静态门面 + 启动期
     * 自注册与 {@code RagStaticConfigInitializer} 的既有模式一致。
     */
    public static void register(PgChatMemoryStore store) {
        pgStore = store;
    }

    /**
     * 当前生效的短期记忆存储。pg 模式返回带搬迁能力的委托实现；
     * pg 实现尚未注册（Spring 未就绪或未装配）时回退 MapDB 历史行为，保证可用性。
     */
    public static AdiChatMemoryStore get() {
        if (useMapdb()) {
            return MapDBChatMemoryStore.getSingleton();
        }
        PgChatMemoryStore pg = pgStore;
        if (null == pg) {
            return MapDBChatMemoryStore.getSingleton();
        }
        if (null == migratingPgStore) {
            synchronized (ChatMemoryStores.class) {
                if (null == migratingPgStore) {
                    migratingPgStore = new MigratingPgChatMemoryStore(pg);
                }
            }
        }
        return migratingPgStore;
    }

    static boolean useMapdb() {
        String store = SpringUtil.getProperty("adi.memory.store");
        if (StringUtils.isBlank(store)) {
            return true; //未配置 = 历史行为，回滚安全
        }
        return "mapdb".equalsIgnoreCase(store.trim());
    }

    /**
     * pg 委托 + 遗留 MapDB 按会话搬迁。
     * <p>
     * 搬迁幂等且多实例安全：两实例同时搬迁同一会话会 upsert 相同内容、各删一次
     * MapDB 键，最终状态一致。空会话（新对话）每轮多一次 O(1) 的 MapDB 内存读，
     * 属迁移期可接受成本；全量搬迁确认后随 MapDB 一起下线（任务卡第 4 步）。
     */
    static final class MigratingPgChatMemoryStore implements AdiChatMemoryStore {

        private final PgChatMemoryStore delegate;

        MigratingPgChatMemoryStore(PgChatMemoryStore delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            List<ChatMessage> messages = delegate.getMessages(memoryId);
            if (!messages.isEmpty() || null == memoryId) {
                return messages;
            }
            //pg miss → 检查遗留 MapDB
            try {
                String key = (String) memoryId;
                String legacyJson = legacyJson(key);
                if (null != legacyJson) {
                    log.info("migrating chat memory from mapdb to pg, memoryId:{}", memoryId);
                    delegate.importRaw(key, legacyJson);
                    MapDBChatMemoryStore.getSingleton().deleteMessages(key);
                    return PgChatMemoryStore.deserialize(legacyJson);
                }
            } catch (Exception e) {
                //搬迁失败不得阻断聊天主流程（短期记忆丢失可容忍，会话会重建）
                log.warn("chat memory migration from mapdb failed, memoryId:{}", memoryId, e);
            }
            return messages;
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            delegate.updateMessages(memoryId, messages);
            //pg 已有权威副本，遗留键一次清干净（防后续 delete 后 miss 复活旧数据）
            discardLegacy(memoryId);
        }

        @Override
        public void deleteMessages(Object memoryId) {
            delegate.deleteMessages(memoryId);
            discardLegacy(memoryId);
        }

        @Override
        public void refreshSystemMessage(Object memoryId, String systemMessage) {
            delegate.refreshSystemMessage(memoryId, systemMessage);
        }

        // ==================== T8-02：摘要槽位委托 ====================

        @Override
        public boolean supportsSummary() {
            return delegate.supportsSummary();
        }

        @Override
        public String getSummary(Object memoryId) {
            return delegate.getSummary(memoryId);
        }

        @Override
        public void saveSummaryAndShrink(Object memoryId, String summary, List<ChatMessage> remainingMessages) {
            delegate.saveSummaryAndShrink(memoryId, summary, remainingMessages);
        }

        private String legacyJson(Object memoryId) {
            try {
                List<ChatMessage> legacy = MapDBChatMemoryStore.getSingleton().getMessages(memoryId);
                if (null == legacy || legacy.isEmpty()) {
                    return null;
                }
                return messagesToJson(legacy);
            } catch (Exception e) {
                log.debug("no legacy mapdb memory for {}", memoryId);
                return null;
            }
        }

        private void discardLegacy(Object memoryId) {
            try {
                MapDBChatMemoryStore.getSingleton().deleteMessages(memoryId);
            } catch (Exception e) {
                log.debug("legacy mapdb discard failed (non-fatal), memoryId:{}", memoryId);
            }
        }
    }
}
