package com.moyz.adi.common.constant;

import java.util.List;

/**
 * 向量库 metadata / 记忆域常量（OPT-17 拆分：原 AdiConstant 嵌套类与记忆相关常量）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有引用（含 5 个静态星号导入文件的嵌套类简单名）无需改动。
 */
public interface MetadataConstant {

    class MetadataKey {
        private MetadataKey() {
        }

        public static final String KB_UUID = "kb_uuid";
        public static final String KB_ITEM_UUID = "kb_item_uuid";
        public static final String SEGMENT_ID = "segment_id";
        /**
         * 出处抬头用的文档 uuid 与段序（二开 2026-09-22）：检索命中后由展开器回填，
         * 仅供调用方辨别来源与排序，不参与向量化。
         * <p>
         * Document uuid / segment position attached to retrieved content for citation.
         * Enriched post-retrieval only; never part of the embedded text.
         */
        public static final String DOC_UUID = "doc_uuid";
        public static final String POSITION = "position";
        public static final String DOC_TITLE = "doc_title";
        public static final String ENGINE_NAME = "engine_name";
        public static final String SEARCH_UUID = "search_uuid";
        /**
         * 关联的角色 ID。键值 {@code "conv_id"} 是 conversation→character 重命名时
         * 保留的历史名，所有语义/情景记忆向量库都用它做 metadata 索引；常量名已规范化。
         * <p>
         * Owning character ID. The string literal {@code "conv_id"} is retained from the
         * conversation→character rename for backward compatibility with existing vector
         * store data — the Java constant name is normalized.
         */
        public static final String CHARACTER_ID = "conv_id";

        /**
         * 记忆类型 metadata key（semantic / episodic）。
         * <p>
         * Memory type metadata key. Old records without this field are treated as semantic.
         */
        public static final String MEMORY_TYPE = "memory_type";

        /**
         * 记忆创建时间（episodic 必填，格式 {@code yyyy-MM-dd HH:mm:ss}）。
         * 注意这是写入 jsonb metadata 内部的 key，与表自带列 {@code create_time} 同名
         * 但作用域不同——后者是 PG 列，前者是 metadata JSON 内的字段。
         * <p>
         * Memory created-at timestamp (required for episodic; formatted as
         * {@code yyyy-MM-dd HH:mm:ss}). Same name as the table column {@code create_time}
         * but lives inside the jsonb metadata — distinct scopes.
         */
        public static final String CREATE_TIME = "create_time";

        /**
         * 关联的角色消息 ID（episodic 必填）。键值 {@code "conv_msg_id"} 是
         * conversation→character 重命名时保留的历史名，与 {@link #CHARACTER_ID} 同源；
         * episodic 表与 semantic 表共用此 key 以保持 metadata 命名一致。
         * <p>
         * Character message ID this memory was derived from (required for episodic).
         * The literal {@code "conv_msg_id"} is retained from the conversation→character
         * rename, same as {@link #CHARACTER_ID}; episodic and semantic stores share this
         * key for metadata consistency.
         */
        public static final String CHARACTER_MSG_ID = "conv_msg_id";

        /**
         * 事件类型（episodic 用）。
         * <p>
         * Event type (episodic only).
         */
        public static final String EVENT_TYPE = "event_type";

        /**
         * 重要性 1-5（episodic 用）。
         * <p>
         * Importance 1-5 (episodic only).
         */
        public static final String IMPORTANCE = "importance";

        // ===== 二开新增（T1-04，2026-10-16）：chunk 级 ACL 固化，三个键一起写 =====
        // 只写 metadata，不参与向量化、不重跑 embedding（铁律：改 ACL 无需重建索引）。

        /**
         * ACL：密级（PUBLIC / ORG / PRIVATE）。检索向量过滤（T1-05）与回填（T1-06）共用。
         * <p>
         * ACL sensitivity of the source knowledge base.
         */
        public static final String ACL_SENSITIVITY = "acl_sensitivity";

        /**
         * ACL：可见组，逗号分隔的组 id 列表，恒含合成组 {@code owner:<owner_uuid>}
         * （组管理 TODO：正式组体系落地后追加真实组 id，见 KbAclMetadata）。
         * <p>
         * ACL visibility groups, comma separated; always contains the synthetic
         * {@code owner:<owner_uuid>} group. Real group management is TODO.
         */
        public static final String ACL_VISIBILITY_GROUPS = "acl_visibility_groups";

        /**
         * ACL：来源库 owner uuid。langchain4j 的 Filter 只支持相等比较，无法表达
         * "owner 在可见组 CSV 内"，owner 可见性必须用独立键过滤（T1-05）。
         * <p>
         * ACL owner uuid of the source knowledge base; needed as its own key because
         * langchain4j filters cannot express CSV containment.
         */
        public static final String KB_OWNER_UUID = "kb_owner_uuid";

        /**
         * 二开新增（T7-07，2026-10-20）：记忆归属用户 id（字符串化的 user_id）。
         * <p>
         * 同一用户跨 Agent 跨会话共享记忆的隔离维度：character_id 决定「哪个角色」，
         * user_id 决定「哪个人」——同一用户的不同角色记忆互不重叠，但同一用户的
         * 任意角色都可读到自己名下的记忆。存量数据由 V14 迁移按
         * {@code conv_id → adi_character.user_id} 回填，无主行写哨兵值
         * {@link #USER_ID_UNKNOWN}（绝不匹配任何真实用户）。
         */
        public static final String USER_ID = "user_id";

        /**
         * user_id 缺失时的哨兵值（V14 回填的无主记忆 / 迁移兼容）：永远不等于任何
         * 真实用户 id，因此「按用户过滤」的检索不会把无主记忆漏给任何人。
         */
        public static final String USER_ID_UNKNOWN = "__unknown__";

        public static final String TEXT = "text";
    }

    //Max length of metadata value
    int MAX_METADATA_VALUE_LENGTH = 500;

    /**
     * 二开新增（T1-03，2026-10-16）：密级三档（adi_knowledge_base.default_sensitivity /
     * chunk metadata acl_sensitivity 共用取值）。预留扩展位：新增档位只需扩 CHECK 约束与这里。
     */
    final class Sensitivity {
        private Sensitivity() {
        }

        public static final String PUBLIC = "PUBLIC";
        public static final String ORG = "ORG";
        public static final String PRIVATE = "PRIVATE";
    }

    class MemoryEvent {
        private MemoryEvent() {
        }

        public static final String NONE = "NONE";
        public static final String ADD = "ADD";
        public static final String UPDATE = "UPDATE";
        public static final String DELETE = "DELETE";
    }

    /**
     * 情景事件重要性默认值（1-5）。
     * <p>
     * Default importance for episodic events.
     */
    int EPISODIC_IMPORTANCE_DEFAULT = 3;

    /**
     * 长期记忆提取时，向量检索旧记忆的最低相似度阈值。
     * <p>
     * Minimum similarity threshold for retrieving old memories during long-term memory extraction.
     * 不同 embedding 模型尺度差异大，建议按模型配置化覆盖。
     */
    double LONG_TERM_MEMORY_MIN_SCORE_DEFAULT = 0.7D;

    /**
     * 召回数据的来源：knowledge_base character_memory web
     */
    class RetrieveContentFrom {
        private RetrieveContentFrom() {
        }

        public static final String KNOWLEDGE_BASE = "knowledge_base";
        public static final String CHARACTER_MEMORY = "character_memory";
        /**
         * 情景记忆召回通道，使用独立的向量库（与 CHARACTER_MEMORY 物理隔离）。
         * <p>
         * Episodic memory retrieval channel. Uses its own physically isolated vector store
         * (separate from CHARACTER_MEMORY) so the two channels never compete for top-K.
         */
        public static final String CHARACTER_MEMORY_EPISODIC = "character_memory_episodic";
        public static final String WEB = "web";
    }
}
