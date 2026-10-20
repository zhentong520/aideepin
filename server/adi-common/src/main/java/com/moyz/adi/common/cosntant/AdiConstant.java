package com.moyz.adi.common.cosntant;

import dev.langchain4j.model.input.PromptTemplate;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class AdiConstant {
    private AdiConstant() {
    }

    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 验证码id过期时间：1小时
     */
    public static final int AUTH_CAPTCHA_ID_EXPIRE = 1;

    /**
     * 验证码过期时间，5分钟
     */
    public static final int AUTH_CAPTCHA_EXPIRE = 5;

    /**
     * 注册激活码有效时长，8小时
     */
    public static final int AUTH_ACTIVE_CODE_EXPIRE = 8;

    /**
     * 注册邮箱验证码默认有效时长，10分钟（可被 adi.register.email-code.expire-minutes 覆盖）
     * Default lifetime of the registration email code (minutes).
     */
    public static final int AUTH_REGISTER_EMAIL_CODE_EXPIRE_MINUTES = 10;

    /**
     * 注册邮箱验证码默认重发间隔，60秒（可被 adi.register.email-code.resend-interval-seconds 覆盖）
     * Default resend interval of the registration email code (seconds).
     */
    public static final int AUTH_REGISTER_EMAIL_CODE_RESEND_INTERVAL_SECONDS = 60;

    /**
     * 注册邮箱验证码默认位数，6位（可被 adi.register.email-code.code-length 覆盖）
     * Default length of the registration email code.
     */
    public static final int AUTH_REGISTER_EMAIL_CODE_LENGTH = 6;

    /**
     * token存活时间（8小时）
     */
    public static final int USER_TOKEN_EXPIRE = 8;

    /**
     * S-07 后已废弃：找回密码不再重置为固定口令，改为用户自设新密码。
     * 常量删除以防止未来被重新引用（全仓引用点已随 S-07 一并清除）。
     */
    // public static final String DEFAULT_PASSWORD 已删除（2026-10-16 S-07）

    /**
     * 用户自设密码的长度约束（S-07：重置/修改密码共用）
     */
    public static final int PASSWORD_MIN_LENGTH = 6;
    public static final int PASSWORD_MAX_LENGTH = 64;

    /**
     * 找回密码邮件发送冷却秒数（S-08：防邮件轰炸）
     */
    public static final int FORGOT_PASSWORD_COOLDOWN_SECONDS = 60;

    public static final int LOGIN_MAX_FAIL_TIMES = 3;

    /**
     * 免鉴权的静态/文档资源。
     *
     * ⚠️ 这里只允许放**以 / 开头的完整路径**，绝不能混入裸扩展名（.js / .css / .html）。
     * 原因：{@code TokenFilter#excludePath} 对本数组走 {@code endsWith} 分支，
     * 裸扩展名会让任意受保护接口「URL 末尾拼一个 .js」就整体绕过鉴权（含 /admin/ 校验）。
     * TokenFilter 侧已加 startsWith("/") 守卫做二次保险，两者必须同时成立。
     */
    public static final String[] WEB_RESOURCES = {
            "/swagger-ui/index.html",
            "/swagger-ui",
            "/swagger-resources",
            "/v3/api-docs",
            "/favicon.ico",
            "/doc.html"
    };

    public static final int SECRET_KEY_TYPE_SYSTEM = 1;
    public static final int SECRET_KEY_TYPE_CUSTOM = 2;

    public static final String OPENAI_MESSAGE_DONE_FLAG = "[DONE]";

    public static final String DEFAULT_MODEL = "gpt-3.5-turbo";

    /**
     * 可能的 extraInfo 如适用转音频的要求： 3. 回答的内容要尽量口语化，以方便将内容转成语音
     */
    public static final PromptTemplate PROMPT_INFO_EXTRA_TEMPLATE = PromptTemplate.from("""
            ## 任务
            根据已知信息及用户的记忆，尽可能准确地回答用户的问题
                        
            ## 要求
            1. 根据对用户的记忆，回答时可以适当调整语气和风格，以更符合用户的偏好
            2. 回答的内容不能让用户感知到已知信息的存在
            {{extraInfo}}
                        
            ## 用户的问题
            {{question}}
                        
            ## 已知信息
            {{information}}
                        
            ## 关于用户的记忆
            {{memory}}
                        
            ## 输出语言
            使用用户提问的语言进行内容输出
            """);

    public static final PromptTemplate PROMPT_INFO_EXTRA_TEMPLATE_EN = PromptTemplate.from("""
            ## Task
            Answer the user's question as accurately as possible based on the known information and the user's memory

            ## Requirements
            1. Based on the user's memory, adjust the tone and style appropriately to better match the user's preferences
            2. The answer should not reveal the existence of the known information to the user
            {{extraInfo}}

            ## User's Question
            {{question}}

            ## Known Information
            {{information}}

            ## About the User's Memory
            {{memory}}

            ## Output Language
            Respond in the language of the user's question
            """);

    public static final String PROMPT_EXTRA_AUDIO = "3. 回答的内容要尽量口语化，以方便将内容转成语音";
    public static final String PROMPT_EXTRA_AUDIO_EN = "3. The response should be as colloquial as possible to facilitate converting the content to speech";

    public static final Double LLM_TEMPERATURE_DEFAULT = 0.7D;
    public static final Double RAG_RETRIEVE_MIN_SCORE_DEFAULT = 0.6D;

    /**
     * 长期记忆提取时，向量检索旧记忆的最低相似度阈值。
     * <p>
     * Minimum similarity threshold for retrieving old memories during long-term memory extraction.
     * 不同 embedding 模型尺度差异大，建议按模型配置化覆盖。
     */
    public static final double LONG_TERM_MEMORY_MIN_SCORE_DEFAULT = 0.7D;

    public static class CharacterConstant {
        private CharacterConstant() {
        }

        public static final String DEFAULT_NAME = "通用智能助手";
        public static final String DEFAULT_NAME_EN = "General AI Assistant";

        public static final int ANSWER_CONTENT_TYPE_AUTO = 1;
        public static final int ANSWER_CONTENT_TYPE_TEXT = 2;
        public static final int ANSWER_CONTENT_TYPE_AUDIO = 3;

        public static final String AUDIO_CONFIG_FIELD_ANSWER_VOICE = "answer_voice";
        public static final String AUDIO_CONFIG_FIELD_VOICE_PLATFORM = "platform";
    }

    public static class GenerateImage {
        private GenerateImage() {
        }

        public static final int INTERACTING_METHOD_GENERATE_IMAGE = 1;
        public static final int INTERACTING_METHOD_EDIT_IMAGE = 2;
        public static final int INTERACTING_METHOD_VARIATION = 3;
        public static final int INTERACTING_METHOD_BACKGROUND_GENERATION = 4;

        public static final int STATUS_DOING = 1;
        public static final int STATUS_FAIL = 2;
        public static final int STATUS_SUCCESS = 3;
    }

    public static class MetadataKey {
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
    public static int MAX_METADATA_VALUE_LENGTH = 500;

    /**
     * 二开新增（T1-03，2026-10-16）：密级三档（adi_knowledge_base.default_sensitivity /
     * chunk metadata acl_sensitivity 共用取值）。预留扩展位：新增档位只需扩 CHECK 约束与这里。
     */
    public static final class Sensitivity {
        private Sensitivity() {
        }

        public static final String PUBLIC = "PUBLIC";
        public static final String ORG = "ORG";
        public static final String PRIVATE = "PRIVATE";
    }

    public static class SysConfigKey {
        private SysConfigKey() {
        }

        public static final String DEEPSEEK_SETTING = "deepseek_setting";
        public static final String OPENAI_SETTING = "openai_setting";
        public static final String DASHSCOPE_SETTING = "dashscope_setting";

        public static final String OLLAMA_SETTING = "ollama_setting";
        public static final String SILICONFLOW_SETTING = "siliconflow_setting";
        public static final String GOOGLE_SETTING = "google_setting";
        public static final String REQUEST_TEXT_RATE_LIMIT = "request_text_rate_limit";
        public static final String REQUEST_IMAGE_RATE_LIMIT = "request_image_rate_limit";
        public static final String CHARACTER_MAX_NUM = "character_max_num";
        public static final String QUOTA_BY_TOKEN_DAILY = "quota_by_token_daily";
        public static final String QUOTA_BY_TOKEN_MONTHLY = "quota_by_token_monthly";
        public static final String QUOTA_BY_REQUEST_DAILY = "quota_by_request_daily";
        public static final String QUOTA_BY_REQUEST_MONTHLY = "quota_by_request_monthly";
        public static final String QUOTA_BY_IMAGE_DAILY = "quota_by_image_daily";
        public static final String QUOTA_BY_IMAGE_MONTHLY = "quota_by_image_monthly";
        public static final String QUOTA_BY_QA_ASK_DAILY = "quota_by_qa_ask_daily";
        public static final String STORAGE_LOCATION = "storage_location";
        public static final String STORAGE_LOCATION_ALI_OSS = "storage_location_ali_oss";
        public static final String ASR_SETTING = "asr_setting";
        public static final String TTS_SETTING = "tts_setting";
        public static final String DEFAULT_LOCALE = "default_locale";

        /**
         * T6-13（铁律 10 开关）：Agent 渠道 LLM 调用记录写入开关，缺省开，
         * 置为 false 可热停用（不写记录，成本看板 Agent 维度随之无新数据）。
         */
        public static final String LLM_COST_RECORD_ENABLED = "llm_cost_record_enabled";

        /**
         * 二开新增（T6-08，2026-10-20）：LLM streaming 请求超时（秒）。
         * 走 adi_sys_config 热配置（管理端保存后立即生效），替换各 LLM Service 里
         * 硬编码的 60s 超时。删除该配置项即回退默认 60s（铁律 10 热回滚位）。
         * <p>
         * LLM streaming timeout in seconds (T6-08), hot-configurable via adi_sys_config.
         */
        public static final String LLM_STREAMING_TIMEOUT_SECONDS = "llm_streaming_timeout_seconds";

        /**
         * T7-02（铁律 10 开关）：agent-as-tool 委派机制运行期热开关，缺省开。
         * 管理端置为 false 可热停用 delegate_agent（返回可读错误，不落库），
         * 与配置文件 adi.agent.delegation-enabled 任一关闭即停用（系统配置优先）。
         * <p>
         * Runtime hot switch for the delegate_agent mechanism (T7-02). "false"
         * rejects every delegation with a readable error; sys_config takes
         * precedence over adi.agent.delegation-enabled.
         */
        public static final String AGENT_DELEGATION_ENABLED = "agent_delegation_enabled";

        /**
         * T7-03（铁律 10 开关）：多 Agent 编排节点（Orchestrator）运行期热开关，缺省开。
         * 管理端置为 false 可热停用编排节点（返回可读错误），
         * 与配置文件 adi.agent.orchestrator-enabled 任一关闭即停用（系统配置优先）。
         * <p>
         * Runtime hot switch for the Orchestrator node (T7-03). "false" rejects
         * orchestration with a readable error; sys_config takes precedence over
         * adi.agent.orchestrator-enabled.
         */
        public static final String AGENT_ORCHESTRATOR_ENABLED = "agent_orchestrator_enabled";
    }

    public static final String[] POI_DOC_TYPES = {"doc", "docx", "ppt", "pptx", "xls", "xlsx"};

    /**
     * 纯文本类型：按 UTF-8 全文读取（langchain4j {@code TextDocumentParser}）。
     * <p>
     * md / markdown 与 txt 同为纯文本，此前分支只判 {@code txt}，导致 .md 落不进任何分支：
     * 上传接口照常返回 file uuid，但 adi_document 一行都没有、也没有任何报错（静默丢弃）。
     * 现在与 txt 共用同一个解析器，Markdown 标记原样入库（标题层级对分句切分有利）。
     */
    public static final String[] TEXT_DOC_TYPES = {"txt", "md", "markdown"};

    /**
     * HTML 类型：用 Jsoup 抽取正文（去标签/脚本/样式）后走通用文本切块。
     * 知识库「更强文档解析」扩展（对标 RAGFlow 的网页维度）。
     */
    public static final String[] HTML_DOC_TYPES = {"html", "htm"};

    /**
     * CSV 类型：按纯文本读取（每行作为候选分段），对标结构化表格接入。
     */
    public static final String[] CSV_DOC_TYPES = {"csv"};

    /**
     * 统一受支持文档类型校验（上传与解析共用口径）。
     */
    public static boolean isSupportedDocType(String ext) {
        if (null == ext) {
            return false;
        }
        String e = ext.toLowerCase();
        for (String t : TEXT_DOC_TYPES) {
            if (t.equals(e)) {
                return true;
            }
        }
        for (String t : POI_DOC_TYPES) {
            if (t.equals(e)) {
                return true;
            }
        }
        for (String t : HTML_DOC_TYPES) {
            if (t.equals(e)) {
                return true;
            }
        }
        for (String t : CSV_DOC_TYPES) {
            if (t.equals(e)) {
                return true;
            }
        }
        return "pdf".equals(e);
    }

    /**
     * 文档来源（adi_document.source_type）。
     * <p>
     * file: 用户上传的文件（含 OCR 兜底）；url: 网页正文抓取；api: 接口定时/手动拉取。
     */
    public static final class DocSourceType {
        private DocSourceType() {
        }

        public static final String FILE = "file";
        public static final String URL = "url";
        public static final String API = "api";
    }

    /**
     * 数据源（URL / API）抓取的硬上限，防止一个配错的地址拖垮服务或撑爆向量化。
     */
    public static final class DocSourceLimit {
        private DocSourceLimit() {
        }

        /** 单次响应体上限（字节）：超出直接判失败，不落库 */
        public static final int MAX_BYTES = 5 * 1024 * 1024;
        /** 连接/读取超时（秒） */
        public static final int TIMEOUT_SECONDS = 20;
        /** 正文入库前截断长度（字符），避免超大页面拖垮切分与向量化 */
        public static final int MAX_TEXT_CHARS = 200_000;
        /** 同步周期上限（分钟）：比这更密的周期不接受，防止把目标站打挂 */
        public static final int MAX_INTERVAL_MINUTES = 60 * 24 * 30;
        /** 同步周期下限（分钟） */
        public static final int MIN_INTERVAL_MINUTES = 10;
    }


    public static class ModelPlatform {
        private ModelPlatform() {
        }

        public static final String DEEPSEEK = "deepseek";
        public static final String OPENAI = "openai";
        public static final String DASHSCOPE = "dashscope";

        public static final String OLLAMA = "ollama";
        public static final String SILICONFLOW = "siliconflow";
        public static final String QIANWEN = "qianwen";

        // 获取所有公共静态常量（String类型的值）的列表
        public static List<String> getModelConstants() {
            List<String> list = new ArrayList<>();
            Class<ModelPlatform> clazz = ModelPlatform.class;
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    String value = (String) field.get(null);
                    list.add(value);
                } catch (ReflectiveOperationException e) {
                    log.error("error", e);
                }

            }
            return list;
        }
    }

    public static class ModelType {
        private ModelType() {
        }

        public static final String TEXT = "text";
        public static final String IMAGE = "image";
        public static final String VISION = "vision";
        public static final String EMBEDDING = "embedding";
        public static final String RERANK = "rerank";
        public static final String ASR = "asr";
        public static final String TTS = "tts";

        public static List<String> getModelType() {
            List<String> list = new ArrayList<>();
            Class<ModelType> clazz = ModelType.class;
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    String value = (String) field.get(null);
                    list.add(value);
                } catch (ReflectiveOperationException e) {
                    log.error("error", e);
                }

            }
            return list;
        }
    }

    public static class SearchEngineName {
        private SearchEngineName() {
        }

        public static final String GOOGLE = "google";

        public static final String[] GOOGLE_COUNTRIES = {"cn", "af", "al", "dz", "as", "ad", "ao", "ai", "aq", "ag", "ar", "am", "aw", "au", "at", "az", "bs", "bh", "bd", "bb", "by", "be", "bz", "bj", "bm", "bt", "bo", "ba", "bw", "bv", "br", "io", "bn", "bg", "bf", "bi", "kh", "cm", "ca", "cv", "ky", "cf", "td", "cl", "cx", "cc", "co", "km", "cg", "cd", "ck", "cr", "ci", "hr", "cu", "cy", "cz", "dk", "dj", "dm", "do", "ec", "eg", "sv", "gq", "er", "ee", "et", "fk", "fo", "fj", "fi", "fr", "gf", "pf", "tf", "ga", "gm", "ge", "de", "gh", "gi", "gr", "gl", "gd", "gp", "gu", "gt", "gn", "gw", "gy", "ht", "hm", "va", "hn", "hk", "hu", "is", "in", "id", "ir", "iq", "ie", "il", "it", "jm", "jp", "jo", "kz", "ke", "ki", "kp", "kr", "kw", "kg", "la", "lv", "lb", "ls", "lr", "ly", "li", "lt", "lu", "mo", "mk", "mg", "mw", "my", "mv", "ml", "mt", "mh", "mq", "mr", "mu", "yt", "mx", "fm", "md", "mc", "mn", "ms", "ma", "mz", "mm", "na", "nr", "np", "nl", "an", "nc", "nz", "ni", "ne", "ng", "nu", "nf", "mp", "no", "om", "pk", "pw", "ps", "pa", "pg", "py", "pe", "ph", "pn", "pl", "pt", "pr", "qa", "re", "ro", "ru", "rw", "sh", "kn", "lc", "pm", "vc", "ws", "sm", "st", "sa", "sn", "rs", "sc", "sl", "sg", "sk", "si", "sb", "so", "za", "gs", "es", "lk", "sd", "sr", "sj", "sz", "se", "ch", "sy", "tw", "tj", "tz", "th", "tl", "tg", "tk", "to", "tt", "tn", "tr", "tm", "tc", "tv", "ug", "ua", "ae", "uk", "gb", "us", "um", "uy", "uz", "vu", "ve", "vn", "vg", "vi", "wf", "eh", "ye", "zm", "zw"};
        public static final String[] GOOGLE_LANGUAGES = {"zh-cn", "zh-tw", "af", "ak", "sq", "ws", "am", "ar", "hy", "az", "eu", "be", "bem", "bn", "bh", "xx-bork", "bs", "br", "bg", "bt", "km", "ca", "chr", "ny", "co", "hr", "cs", "da", "nl", "xx-elmer", "en", "eo", "et", "ee", "fo", "tl", "fi", "fr", "fy", "gaa", "gl", "ka", "de", "el", "kl", "gn", "gu", "xx-hacker", "ht", "ha", "haw", "iw", "hi", "hu", "is", "ig", "id", "ia", "ga", "it", "ja", "jw", "kn", "kk", "rw", "rn", "xx-klingon", "kg", "ko", "kri", "ku", "ckb", "ky", "lo", "la", "lv", "ln", "lt", "loz", "lg", "ach", "mk", "mg", "ms", "ml", "mt", "mv", "mi", "mr", "mfe", "mo", "mn", "sr-me", "my", "ne", "pcm", "nso", "no", "nn", "oc", "or", "om", "ps", "fa", "xx-pirate", "pl", "pt", "pt-br", "pt-pt", "pa", "qu", "ro", "rm", "nyn", "ru", "gd", "sr", "sh", "st", "tn", "crs", "sn", "sd", "si", "sk", "sl", "so", "es", "es-419", "su", "sw", "sv", "tg", "ta", "tt", "te", "th", "ti", "to", "lua", "tum", "tr", "tk", "tw", "ug", "uk", "ur", "uz", "vu", "vi", "cy", "wo", "xh", "yi", "yo", "zu"};
    }

    public static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5分钟

    public static class SSEEventName {
        private SSEEventName() {
        }

        public static final String START = "[START]";
        public static final String DONE = "[DONE]";
        public static final String ERROR = "[ERROR]";
        public static final String META = "[META]";
        public static final String AUDIO = "[AUDIO]";
        public static final String THINKING = "[THINKING]";
        public static final String AI_SEARCH_SOURCE_LINKS = "[SOURCE_LINKS]";
        public static final String WF_NODE_CHUNK = "[WF_NODE_CHUNK]";
        public static final String WF_NODE_OUTPUT = "[WF_NODE_OUTPUT]";
        public static final String STATE_CHANGED = "[STATE_CHANGED]";
        public static final String TOOL_CALL = "[TOOL_CALL]";
        /**
         * Runtime-level aggregated metrics snapshot pushed at terminal status (success / fail /
         * waiting_input). Frontend looks for this exact tag in RunDetail.vue.
         */
        public static final String RUNTIME_METRICS = "[RUNTIME_METRICS]";
        /**
         * Per-node metrics event prefix; the full event name is {@code NODE_METRICS_PREFIX + nodeUuid + "]"}.
         */
        public static final String NODE_METRICS_PREFIX = "[NODE_METRICS_";
    }

    public static class SSEEventData {

        /**
         * 状态：问题分析中
         * 如敏感词校验等
         * remark uses i18n code, frontend maps code to locale-specific text
         */
        public static final String STATE_QUESTION_ANALYSING = """
                {"state":"question_analysing","remark":"state.question_analysing"}
                """;

        public static final String STATE_KNOWLEDGE_SEARCHING = """
                {"state":"knowledge_searching","remark":"state.knowledge_searching"}
                """;
        //使用 THINKING 事件代替
        public static final String STATE_THINKING = """
                {"state":"thinking","remark":"state.thinking"}
                """;
        public static final String STATE_RESPONDING = """
                {"state":"responding","remark":"state.responding"}
                """;
    }

    public static final int RAG_TYPE_KB = 1;
    public static final int RAG_TYPE_SEARCH = 2;

    /**
     * 每块文档长度（按token算）
     */
    public static final int RAG_MAX_SEGMENT_SIZE_IN_TOKENS = 1000;

    public static final class SplitStrategy {
        public static final String RECURSIVE = "recursive";
        public static final String PARAGRAPH = "paragraph";
        public static final String LINE = "line";
        public static final String SENTENCE = "sentence";
        public static final String CUSTOM = "custom";
        /**
         * Sentence-aware splitting for Chinese text: breaks at CJK sentence terminators (。！？；) and at
         * line breaks, then packs whole sentences into segments. See {@code CjkSentenceSplitter}.
         */
        public static final String SENTENCE_CN = "sentence_cn";
    }

    /**
     * Parent-child mode child chunk size bounds in tokens
     */
    public static final class ChildChunkSize {
        public static final int MIN = 50;
        public static final int MAX = 4000;
        public static final int DEFAULT = 200;
    }

    /**
     * 分段来源（adi_document_segment.source）。
     * doc: 文档入库切分产生；manual: 页面手动创建；annotation: 预留给后续的聊天标注功能
     * （标注 = source=annotation 的 QA 段，复用问答模式结构与检索展开逻辑）。
     */
    public static final class SegmentSource {
        public static final String DOC = "doc";
        public static final String MANUAL = "manual";
        public static final String ANNOTATION = "annotation";
    }

    /**
     * 文档召回默认数量（非知识库链路——角色记忆/情景记忆——在调用方未指定时的兜底值）。
     * <p>
     * Knowledge-base rows resolve their own size (explicit setting, else the token-budget rule in
     * {@code EmbeddingRag#getRetrieveMaxResults}); this constant only covers callers that pass none.
     */
    public static final int RAG_RETRIEVE_NUMBER_DEFAULT = 3;

    /**
     * 文档召回最大数量：token 预算充足时知识库的召回条数，也是自适应路径的上限。
     * <p>
     * 5 → 10（2026-09-22，实测）。召回条数是"排序侧"一切手段的硬天花板：重排、改写、扩窗都只能
     * 在**已经被召回**的候选里做文章。73 条中文金标实测（sentence_cn/512，doubao-embedding-2048，
     * 见 kb-eval 的 RANKDIAG）：
     * <pre>
     *   池=3   答案在池内 86.3%   ← 13.7% 的问题无论怎么重排都不可能答对
     *   池=5   答案在池内 95.9%   ← 原上限，仍有 3 条（4.1%）答案在池外
     *   池=10  答案在池内 100.0%  ← 本次取值
     * </pre>
     * 代价是送进 prompt 的召回文本从 2658 字符增到 5376 字符（约 3.7k token），不增加任何模型调用；
     * 上限受 token 预算保护——模型窗口不够时 {@code getRetrieveMaxResults} 会自行降到预算允许的条数。
     */
    public static final int RAG_RETRIEVE_NUMBER_MAX = 10;

    /**
     * 向量搜索时命中所需的最低分数
     */
    public static final double RAG_MIN_SCORE = 0.6;

    /**
     * <p>Default fallback used only when a model has no explicit maxInputTokens configured.</p>
     */
    public static final int LLM_MAX_INPUT_TOKENS_DEFAULT = 32768;

    public static final String LLM_INPUT_TYPE_TEXT = "text";
    public static final String LLM_INPUT_TYPE_IMAGE = "image";
    public static final String LLM_INPUT_TYPE_AUDIO = "audio";
    public static final String LLM_INPUT_TYPE_VIDEO = "video";

    public static final String[] GRAPH_ENTITY_EXTRACTION_ENTITY_TYPES = {"ORGANIZATION", "PERSON", "GEO", "EVENT"};
    public static final String GRAPH_TUPLE_DELIMITER = "<|>";
    public static final String GRAPH_RECORD_DELIMITER = "##";
    public static final String GRAPH_COMPLETION_DELIMITER = "<|COMPLETE|>";

    public static final List<String> GRAPH_STORE_MAIN_FIELDS = List.of("name", "label", "text_segment_id", "description");

    /**
     * 唯一标识字段，如果该字段有指定，则根据该配置判断Vertex或Edge是否唯一，如知识库中根据 name、metadata->>kb_uuid 来做判断
     */
    public static final String GRAPH_METADATA_IDENTIFY_COLUMNS = "graph_metadata_identify_columns";

    /**
     * 内容追加字段
     * 更新数据时，如遇到该标识中的字段，追加内容而不是替换
     */
    public static final String GRAPH_METADATA_APPEND_COLUMNS = "graph_metadata_append_columns_if_exist";

    public static final int AI_IMAGE_TYPE_REGULAR = 1;
    public static final int AI_IMAGE_TYPE_THUMBNAIL = 2;
    public static final int AI_IMAGE_TYPE_REGULAR_MARK = 3;
    public static final int AI_IMAGE_TYPE_THUMBNAIL_MARK = 4;

    public static final String DOC_INDEX_TYPE_EMBEDDING = "embedding";
    public static final String DOC_INDEX_TYPE_GRAPHICAL = "graphical";

    /**
     * 文档解析出 0 字符（扫描件 / 图片型 PDF 没有文本层）时落给用户的可读原因。
     * 以前这类文档会一路"索引成功"，但库里没有任何内容可召回，用户只知道"搜不到"、
     * 排查成本极高；现在直接在文档状态上写明原因和处理办法。
     */
    public static final String EMPTY_PARSE_FAIL_REASON =
            "文档解析内容为空，可能是扫描件或图片型 PDF（没有文本层）；请先做 OCR 处理，或上传带文本层的副本";

    /**
     * 已启用 OCR 但识别结果仍为空时落给用户的可读原因：排查方向从"文档没有文本层"
     * 转为"OCR 服务本身"，避免用户重复上传同一批扫描件。
     */
    public static final String OCR_EMPTY_FAIL_REASON =
            "已启用 OCR，但仍未识别出任何文字；请检查 OCR 服务地址与语言参数是否正常、图片是否过淡或倾斜，处理后可对该文档点\"重试 OCR\"";

    public static final String DRAW_TYPE_PUBLIC = "public";
    public static final String DRAW_TYPE_STARRED = "starred";
    public static final String DRAW_TYPE_MINE = "mine";

    public static final String MP_LIMIT_1 = "limit 1";

    /**
     * 文件存储在本地
     */
    public static final int STORAGE_LOCATION_VALUE_LOCAL = 1;

    /**
     * 文件存储到阿里云OSS
     */
    public static final int STORAGE_LOCATION_VALUE_ALI_OSS = 2;

    public static final String URL_PREFIX_FILE = "/file/";
    public static final String URL_PREFIX_IMAGE = "/image/";
    public static final String URL_PREFIX_MY_IMAGE = "/my-image/";
    public static final String URL_PREFIX_MY_THUMBNAIL = "/my-thumbnail/";

    public static final List<String> IMAGE_EXTENSIONS = List.of("jpg", "jpeg", "png", "gif", "bmp", "webp");

    public static final String W_FAILED = "FAILED";

    public static final String COLUMN_NAME_IS_DELETE = "is_deleted";
    public static final String COLUMN_NAME_USER_ID = "user_id";
    public static final String COLUMN_NAME_ID = "id";
    public static final String COLUMN_NAME_UUID = "uuid";

    public static final String FORM_DATA_BOUNDARY_PRE = "----WebKitFormBoundary";

    public static final String RESPONSE_FORMAT_TYPE_TEXT = "text";

    public static final String RESPONSE_FORMAT_TYPE_JSON_OBJECT = "json_object";

    public static class WorkflowConstant {
        public static final String DEFAULT_INPUT_PARAM_NAME = "input";
        public static final String DEFAULT_OUTPUT_PARAM_NAME = "output";
        public static final String DEFAULT_ERROR_OUTPUT_PARAM_NAME = "error_msg";
        public static final String HUMAN_FEEDBACK_KEY = "human_feedback";
        public static final int NODE_PROCESS_STATUS_READY = 1;
        public static final int NODE_PROCESS_STATUS_DOING = 2;
        public static final int NODE_PROCESS_STATUS_SUCCESS = 3;
        public static final int NODE_PROCESS_STATUS_FAIL = 4;

        public static final int WORKFLOW_PROCESS_STATUS_READY = 1;
        public static final int WORKFLOW_PROCESS_STATUS_DOING = 2;
        public static final int WORKFLOW_PROCESS_STATUS_SUCCESS = 3;
        public static final int WORKFLOW_PROCESS_STATUS_FAIL = 4;
        public static final int WORKFLOW_PROCESS_STATUS_WAITING_INPUT = 5;

        public static final int WORKFLOW_NODE_PROCESS_TYPE_NORMAL = 1;
        public static final int WORKFLOW_NODE_PROCESS_TYPE_CONDITIONAL = 2;
        public static final int WORKFLOW_NODE_PROCESS_TYPE_PARALLEL = 3;

        public static final int MAIL_SENDER_TYPE_SYS = 1;
        public static final int MAIL_SENDER_TYPE_CUSTOM = 2;
    }

    public static class TokenEstimator {
        public static String OPENAI = "openai";
        public static String HUGGING_FACE = "huggingface";
        public static String QWEN = "qwen";

        public static List<String> ALL = List.of(OPENAI, HUGGING_FACE, QWEN);
    }

    public static class EmbeddingModel {
        public static String ALL_MINILM_L6 = "local:all-minilm-l6-v2";
        public static String BGE_SMALL_ZH_V15 = "local:bge-small-zh-v1.5";
        public static int BGE_SMALL_ZH_V15_DIMENSION = 512;
        public static List<String> LOCAL_MODELS = List.of(ALL_MINILM_L6, BGE_SMALL_ZH_V15);

        /**
         * bge-m3 模型名（T7-05）：作为可选 embedding provider 接入。
         * <p>
         * langchain4j 未内置 bge-m3 的 ONNX 推理类（权重约 2GB，亦不适合随发行包分发），
         * 因此 bge-m3 走「OpenAI 协议兼容端点」这唯一的现实接入方式：由 Xinference /
         * vLLM / One-API 等以 /v1/embeddings 协议 serving，配置形如
         * {@code adi.embedding-model=<platform>:bge-m3}（platform 标记
         * is_openai_api_compatible，AiModel.properties.dimension 显式配维度）。
         * 本地 ONNX 路线留待 langchain4j 上游提供对应 EmbeddingModel 实现后再补分支，
         * 不自行伪造一个不可用的本地 provider。
         */
        public static String BGE_M3 = "bge-m3";
        public static int BGE_M3_DIMENSION = 1024;
        /**
         * bge-m3 支持 MRL 截断维度（1024 全精度 / 768 / 512 截断），配置其他维度时告警——
         * 截断维度会改变向量语义，与既有索引不可混用，必须是运维的有意选择。
         */
        public static List<Integer> BGE_M3_ALLOWED_DIMENSIONS = List.of(1024, 768, 512);
    }

    public static class McpConstant {
        public static final String TRANSPORT_TYPE_SSE = "sse";
        public static final String TRANSPORT_TYPE_STDIO = "stdio";
        public static final String TRANSPORT_TYPE_STREAMABLE_HTTP = "streamable_http";
        public static final String INSTALL_TYPE_REMOTE = "remote";
        public static final String INSTALL_TYPE_WASM = "wasm";
        public static final String INSTALL_TYPE_LOCAL = "local";
        public static final String INSTALL_TYPE_DOCKER = "docker";
    }

    public static class TtsConstant {

        /**
         * 语音合成器位置-客户端
         */
        public static final String SYNTHESIZER_CLIENT = "client";
        /**
         * 语音合成器位置-服务端
         */
        public static final String SYNTHESIZER_SERVER = "server";

        /**
         * 通义默认语音音色-龙小淳(知性积极女)
         */
        public static final String DASHSCOPE_DEFAULT_VOICE = "longxiaochun_v3";
        public static final String SILICONFLOW_DEFAULT_VOICE = "FunAudioLLM/CosyVoice2-0.5B:alex";
        public static final String OPENAI_DEFAULT_VOICE = "alloy";
    }

    public static class CustomChatRequestParameterKeys {
        private CustomChatRequestParameterKeys() {
        }

        /**
         * 是否开启思考模式，默认不开启
         */
        public static final String ENABLE_THINKING = "enable_thinking";
        /**
         * 是否启用联网搜索功能
         */
        public static final String ENABLE_WEB_SEARCH = "enable_web_search";
    }

    public static class MemoryEvent {
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
    public static final int EPISODIC_IMPORTANCE_DEFAULT = 3;

    /**
     * 召回数据的来源：knowledge_base character_memory web
     */
    public static class RetrieveContentFrom {
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
