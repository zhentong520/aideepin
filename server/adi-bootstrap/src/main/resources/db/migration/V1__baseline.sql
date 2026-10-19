-- noinspection SqlNoDataSourceInspectionForFile

-- noinspection SqlNoDataSourceInspectionForFile

-- Install pgvector extension (https://github.com/pgvector/pgvector)
-- Install Apache AGE extension (https://github.com/apache/age)
-- CREATE EXTENSION IF NOT EXISTS vector;
-- CREATE EXTENSION IF NOT EXISTS age;

SET client_encoding = 'UTF8';
CREATE SCHEMA IF NOT EXISTS public;

-- ============================================================
-- Common: update_time trigger function
-- ============================================================
CREATE OR REPLACE FUNCTION update_modified_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.update_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ============================================================
-- Drawing: AI image generation and gallery
-- ============================================================

-- Original table name: adi_ai_image
CREATE TABLE adi_draw
(
    id                    bigserial primary key,
    user_id               bigint        default 0                 not null,
    uuid                  varchar(32)   default ''                not null,
    ai_model_id           bigint        default 0                 not null,
    ai_model_name         varchar(45)   default ''                not null,
    prompt                varchar(1024) default ''                not null,
    negative_prompt       varchar(1024) default ''                not null,
    generate_size         varchar(20)   default ''                not null,
    generate_quality      varchar(20)   default ''                not null,
    generate_number       integer       default 1                 not null,
    generate_seed         integer       default -1                not null,
    dynamic_params        jsonb         default '{}'              not null,
    original_image        varchar(1000) default ''                not null,
    mask_image            varchar(1000) default ''                not null,
    resp_images_path      varchar(2048) default ''                not null,
    generated_images      varchar(2048) default ''                not null,
    interacting_method    smallint      default 1                 not null,
    process_status        smallint      default 1                 not null,
    process_status_remark varchar(250)  default ''                not null,
    is_public             boolean       default false             not null,
    with_watermark        boolean       default false             not null,
    star_count            int           default 0                 not null,
    duration              int           default 0                 not null,
    create_time           timestamp     default CURRENT_TIMESTAMP not null,
    update_time           timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted            boolean       default false             not null,
    CONSTRAINT adi_draw_generate_number_check CHECK ((generate_number >= 1)),
    CONSTRAINT adi_draw_process_status_check CHECK ((process_status = ANY (ARRAY [1, 2, 3]))),
    CONSTRAINT adi_draw_user_id_check CHECK ((user_id >= 0))
);
ALTER TABLE ONLY adi_draw
    ADD CONSTRAINT udx_uuid UNIQUE (uuid);
COMMENT ON TABLE adi_draw IS 'Image generation task';
COMMENT ON COLUMN adi_draw.user_id IS 'User ID';
COMMENT ON COLUMN adi_draw.uuid IS 'UUID of the request of generated images';
COMMENT ON COLUMN adi_draw.ai_model_name IS 'Image model name';
COMMENT ON COLUMN adi_draw.prompt IS 'The prompt for generating images';
COMMENT ON COLUMN adi_draw.generate_size IS 'Image generation size';
COMMENT ON COLUMN adi_draw.generate_quality IS 'Image generation quality';
COMMENT ON COLUMN adi_draw.generate_number IS 'Number of images to generate (limited by model max_images)';
COMMENT ON COLUMN adi_draw.generate_seed IS 'Random seed for reproducible generation';
COMMENT ON COLUMN adi_draw.original_image IS 'Original image UUID (required for interacting method 2 or 3)';
COMMENT ON COLUMN adi_draw.mask_image IS 'Deprecated. The UUID of the mask image for editing (interacting method 2)';
COMMENT ON COLUMN adi_draw.resp_images_path IS 'URLs of generated images from API response, comma-separated';
COMMENT ON COLUMN adi_draw.generated_images IS 'The UUID of the generated images, separated by commas';
COMMENT ON COLUMN adi_draw.interacting_method IS 'Image generation type: 1=Text to Image, 2=Image Editing, 3=Image to Image, 4=Background Generation, 5=Style Transfer';
COMMENT ON COLUMN adi_draw.process_status IS 'Task status: 1=In progress, 2=Failed, 3=Success';
COMMENT ON COLUMN adi_draw.process_status_remark IS 'Image generation status remark';
COMMENT ON COLUMN adi_draw.is_public IS 'Whether the image is publicly visible';
COMMENT ON COLUMN adi_draw.with_watermark IS 'Whether the image has a watermark';
COMMENT ON COLUMN adi_draw.star_count IS 'Like count';
COMMENT ON COLUMN adi_draw.create_time IS 'Creation time';
COMMENT ON COLUMN adi_draw.update_time IS 'Last update time';
COMMENT ON COLUMN adi_draw.is_deleted IS 'Whether the record is soft-deleted';

CREATE TRIGGER trigger_draw_update_time
    BEFORE UPDATE
    ON adi_draw
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_draw_star
(
    id          bigserial primary key,
    user_id     bigint    default 0                 not null,
    draw_id     bigint    default 0                 not null,
    create_time timestamp default CURRENT_TIMESTAMP not null,
    update_time timestamp default CURRENT_TIMESTAMP not null,
    is_deleted  boolean   default false             not null
);
CREATE TRIGGER trigger_draw_star_update_time
    BEFORE UPDATE
    ON adi_draw_star
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_draw_comment
(
    id          bigserial primary key,
    uuid        varchar(32) default ''                not null,
    user_id     bigint      default 0                 not null,
    draw_id     bigint      default 0                 not null,
    remark      text        default ''                not null,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted  boolean     default false             not null
);
CREATE TRIGGER trigger_draw_comment_update_time
    BEFORE UPDATE
    ON adi_draw_comment
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ============================================================
-- Model Platform & AI Model: model provider and model configuration
-- ============================================================

-- In versions 3.15.0 and below, model platform data was stored in adi_sys_config
-- Manually migrate these config items: deepseek_setting, openai_setting, dashscope_setting, ollama_setting, siliconflow_setting
CREATE TABLE adi_model_platform
(
    id                       bigserial primary key,
    name                     varchar(45)   default ''                not null,
    title                    varchar(45)   default ''                not null,
    base_url                 varchar(250)  default ''                not null,
    api_key                  varchar(100)  default ''                not null,
    secret_key               varchar(100)  default ''                not null,
    remark                   varchar(1000) default ''                not null,
    is_proxy_enable          boolean       default false             not null,
    is_openai_api_compatible boolean       default false             not null,
    rerank_url               varchar(500)  default ''                not null,
    create_time              timestamp     default CURRENT_TIMESTAMP not null,
    update_time              timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted               boolean       default false             not null
);

COMMENT ON TABLE adi_model_platform IS 'Model platform (model provider)';
COMMENT ON COLUMN adi_model_platform.name IS 'Model provider name, e.g., openai, dashscope, ollama';
COMMENT ON COLUMN adi_model_platform.title IS 'Model provider title, a more readable name, e.g., OpenAI, DeepSeek';
COMMENT ON COLUMN adi_model_platform.base_url IS 'API base URL of the model provider';
COMMENT ON COLUMN adi_model_platform.api_key IS 'API Key of the model provider';
COMMENT ON COLUMN adi_model_platform.secret_key IS 'Deprecated, only used by Qianfan which is no longer supported';
COMMENT ON COLUMN adi_model_platform.is_proxy_enable IS 'Whether to access the model provider API through a proxy';
COMMENT ON COLUMN adi_model_platform.is_openai_api_compatible IS 'Whether compatible with OpenAI API format';
COMMENT ON COLUMN adi_model_platform.rerank_url IS 'Dedicated rerank endpoint full URL; falls back to base_url + /rerank when empty. For providers whose rerank lives on a different base path than embeddings (e.g. Qianwen MaaS: embeddings on /compatible-mode/v1, rerank on /compatible-api/v1/reranks)';
COMMENT ON COLUMN adi_model_platform.remark IS 'Additional remarks about the model provider';

CREATE TRIGGER trigger_model_platform_update_time
    BEFORE UPDATE
    ON adi_model_platform
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_ai_model
(
    id                    bigserial primary key,
    name                  varchar(45)   default ''                not null,
    title                 varchar(45)   default ''                not null,
    type                  varchar(45)   default 'text'            not null,
    setting               varchar(500)  default ''                not null,
    remark                varchar(1000) default '',
    platform              varchar(45)   default ''                not null,
    context_window        int           default 0                 not null,
    max_input_tokens      int           default 0                 not null,
    max_output_tokens     int           default 0                 not null,
    input_types           varchar(100)  default 'text'            not null,
    properties            jsonb         default '{}'              not null,
    response_format_types varchar(200)  default 'text'            not null,
    is_support_web_search boolean       default false             not null,
    is_reasoner           boolean       default false             not null,
    is_thinking_closable  boolean       default false             not null,
    is_free               boolean       default false             not null,
    is_enable             boolean       default false             not null,
    create_time           timestamp     default CURRENT_TIMESTAMP not null,
    update_time           timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted            boolean       default false             not null
);

COMMENT ON TABLE adi_ai_model IS 'AI model';
COMMENT ON COLUMN adi_ai_model.type IS 'Model type: text, image, vision, embedding, rerank, multimodality';
COMMENT ON COLUMN adi_ai_model.name IS 'Model name as defined by the provider, must match exactly';
COMMENT ON COLUMN adi_ai_model.title IS 'Model title, a more readable name, e.g., openai-gpt3';
COMMENT ON COLUMN adi_ai_model.setting IS 'JSON format, e.g., {"voice_for_group1":"v1","voice_for_group2":"v2"}';
COMMENT ON COLUMN adi_ai_model.properties IS 'JSON, e.g., {"dimension":1536} for embedding model, {"voices":["v1","v2","v3"]} for TTS model';
COMMENT ON COLUMN adi_ai_model.remark IS 'Additional remarks about the AI model';
COMMENT ON COLUMN adi_ai_model.platform IS 'Model platform (as model provider): openai, dashscope, ollama';
COMMENT ON COLUMN adi_ai_model.context_window IS 'LLM context window';
COMMENT ON COLUMN adi_ai_model.input_types IS 'Input types: text, image, audio, video';
COMMENT ON COLUMN adi_ai_model.response_format_types IS 'Response format: text, json_object, json_schema';
COMMENT ON COLUMN adi_ai_model.is_support_web_search IS 'Whether web search is supported';
COMMENT ON COLUMN adi_ai_model.is_reasoner IS 'Whether the model is a reasoning model';
COMMENT ON COLUMN adi_ai_model.is_thinking_closable IS 'Whether the thinking process can be toggled off (e.g., Qwen3 yes, DeepSeek-R1 no)';
COMMENT ON COLUMN adi_ai_model.is_enable IS 'Whether the model is available for use';
COMMENT ON COLUMN adi_ai_model.is_free IS 'Whether the model is free to use';
COMMENT ON COLUMN adi_ai_model.create_time IS 'Creation time';
COMMENT ON COLUMN adi_ai_model.update_time IS 'Last update time';

CREATE TRIGGER trigger_ai_model_update_time
    BEFORE UPDATE
    ON adi_ai_model
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ============================================================
-- Character: persistent personas, presets, and messages
-- ============================================================

CREATE TABLE adi_character_preset
(
    id                bigserial primary key,
    uuid              varchar(32)   default ''                not null,
    title             varchar(45)   default ''                not null,
    remark            varchar(1000) default ''                not null,
    ai_system_message varchar(1000) default ''                not null,
    kb_title          varchar(100)  default ''                not null,
    type              varchar(45)   default ''                not null,
    create_time       timestamp     default CURRENT_TIMESTAMP not null,
    update_time       timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted        boolean       default false             not null
);
COMMENT ON TABLE adi_character_preset IS 'Preset Character table';
COMMENT ON COLUMN adi_character_preset.title IS 'Title';
COMMENT ON COLUMN adi_character_preset.remark IS 'Description';
COMMENT ON COLUMN adi_character_preset.ai_system_message IS 'System message for LLM';
COMMENT ON COLUMN adi_character_preset.kb_title IS 'Knowledge base title to auto-create, empty means no creation';
COMMENT ON COLUMN adi_character_preset.type IS 'Character type (technology/creative/education/business/professional/design/marketing/service/administration/utility)';

create trigger trigger_character_preset
    before update
    on adi_character_preset
    for each row
execute procedure update_modified_column();

CREATE TABLE adi_character
(
    id                        bigserial primary key,
    user_id                   bigint        default 0                 not null,
    uuid                      varchar(32)   default ''                not null,
    title                     varchar(45)   default ''                not null,
    remark                    varchar(500)  default ''                not null,
    ai_system_message         varchar(1000) default ''                not null,
    understand_context_enable boolean       default true              not null,
    llm_temperature           numeric(2, 1) default 0.7               not null,
    mcp_ids                   varchar(1000) default ''                not null,
    kb_ids                    varchar(1000) default ''                not null,
    answer_content_type       smallint      default 1                 not null,
    is_autoplay_answer        boolean       default true              not null,
    is_enable_thinking        boolean       default false             not null,
    is_enable_web_search      boolean       default false             not null,
    audio_config              jsonb         default '{}'              not null,
    api_key                   varchar(200)  default ''                not null,
    create_time               timestamp     default CURRENT_TIMESTAMP not null,
    update_time               timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted                boolean       default false             not null
);

COMMENT ON TABLE adi_character IS 'Character table (represents a persistent persona with long-term memory, not a one-off session)';
COMMENT ON COLUMN adi_character.user_id IS 'User ID';
COMMENT ON COLUMN adi_character.title IS 'Title, e.g., Sherlock Holmes';
COMMENT ON COLUMN adi_character.remark IS 'Remark, e.g., Brilliant detective with keen observation skills';
COMMENT ON COLUMN adi_character.ai_system_message IS 'Character setting content, e.g., You are Sherlock Holmes, a brilliant detective known for your keen observation skills';
COMMENT ON COLUMN adi_character.llm_temperature IS 'LLM response creativity/randomness';
COMMENT ON COLUMN adi_character.mcp_ids IS 'Enabled MCP service IDs, comma-separated';
COMMENT ON COLUMN adi_character.kb_ids IS 'Associated knowledge base IDs, comma-separated';
COMMENT ON COLUMN adi_character.answer_content_type IS 'Response content type: 1=Auto, 2=Text, 3=Audio';
COMMENT ON COLUMN adi_character.is_autoplay_answer IS 'Whether audio responses play automatically';
COMMENT ON COLUMN adi_character.is_enable_thinking IS 'Whether thinking/reasoning process is enabled (effective only if the model supports it)';
COMMENT ON COLUMN adi_character.is_enable_web_search IS 'Whether to enable web search';
COMMENT ON COLUMN adi_character.audio_config IS 'Audio configuration, stored in JSON format, e.g., {"voice":{"param_name":"longyingda","model":"cosyvoice-v2","platform":"dashscope"}}';
COMMENT ON COLUMN adi_character.api_key IS 'API key for external system integration (AES encrypted)';


CREATE TRIGGER trigger_character_update_time
    BEFORE UPDATE
    ON adi_character
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();


CREATE TABLE adi_character_preset_rel
(
    id                bigserial primary key,
    uuid              varchar(32) default ''                not null,
    user_id           bigint      default 0                 not null,
    preset_character_id bigint    default 0                 not null,
    user_character_id   bigint    default 0                 not null,
    create_time       timestamp   default CURRENT_TIMESTAMP not null,
    update_time       timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted        boolean     default false             not null
);

COMMENT ON TABLE adi_character_preset_rel IS 'Preset Character Relation table';
COMMENT ON COLUMN adi_character_preset_rel.user_id IS 'User ID';
COMMENT ON COLUMN adi_character_preset_rel.preset_character_id IS 'Preset character ID';
COMMENT ON COLUMN adi_character_preset_rel.user_character_id IS 'User character ID';

create trigger trigger_character_preset_rel
    before update
    on adi_character_preset_rel
    for each row
execute procedure update_modified_column();

CREATE TABLE adi_character_message
(
    id                              bigserial primary key,
    parent_message_id               bigint        default 0                 not null,
    character_id                    bigint        default 0                 not null,
    character_uuid                  varchar(32)   default ''                not null,
    content_type                    smallint      default 2                 not null,
    remark                          text          default ''                not null,
    processed_remark                text          default ''                not null,
    thinking_content                text          default ''                not null,
    audio_uuid                      varchar(32)   default ''                not null,
    audio_duration                  integer       default 0                 not null,
    uuid                            varchar(32)   default ''                not null,
    message_role                    integer       default 1                 not null,
    user_id                         bigint        default 0                 not null,
    ai_model_id                     bigint        default 0                 not null,
    attachments                     varchar(1000) default ''                not null,
    create_time                     timestamp     default CURRENT_TIMESTAMP not null,
    update_time                     timestamp     default CURRENT_TIMESTAMP not null,
    is_ref_embedding                boolean       default false             not null,
    is_ref_graph                    boolean       default false             not null,
    is_ref_memory_embedding         boolean       default false             not null,
    is_deleted                      boolean       default false             not null
);

COMMENT ON TABLE adi_character_message IS 'Character Message table';
COMMENT ON COLUMN adi_character_message.parent_message_id IS 'Parent message ID';
COMMENT ON COLUMN adi_character_message.character_id IS 'Character ID';
COMMENT ON COLUMN adi_character_message.character_uuid IS 'Character UUID';
COMMENT ON COLUMN adi_character_message.remark IS 'Original message (e.g., user question, AI answer)';
COMMENT ON COLUMN adi_character_message.processed_remark IS 'Processed message (e.g., user question + knowledge base context for LLM input, or AI response after compliance filtering)';
COMMENT ON COLUMN adi_character_message.content_type IS 'Message content type: 2=Text, 3=Audio';
COMMENT ON COLUMN adi_character_message.uuid IS 'Unique identifier for the message';
COMMENT ON COLUMN adi_character_message.audio_uuid IS 'Audio file UUID (references adi_file.uuid)';
COMMENT ON COLUMN adi_character_message.audio_duration IS 'Audio duration in seconds';
COMMENT ON COLUMN adi_character_message.message_role IS 'Message role: 1=User, 2=System, 3=Assistant';
COMMENT ON COLUMN adi_character_message.user_id IS 'User ID';
COMMENT ON COLUMN adi_character_message.ai_model_id IS 'adi_ai_model id';
COMMENT ON COLUMN adi_character_message.attachments IS 'Attachments, stored as: uuid,uuid';
COMMENT ON COLUMN adi_character_message.is_ref_embedding IS 'Whether knowledge base embeddings are referenced';
COMMENT ON COLUMN adi_character_message.is_ref_graph IS 'Whether knowledge graph is referenced';
COMMENT ON COLUMN adi_character_message.is_ref_memory_embedding IS 'Whether memory embeddings are referenced';

CREATE TRIGGER trigger_character_message_update_time
    BEFORE UPDATE
    ON adi_character_message
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

create table adi_character_message_ref_embedding
(
    id           bigserial primary key,
    message_id   bigint        default 0  not null,
    embedding_id varchar(36)   default '' not null,
    score        numeric(3, 2) default 0  not null,
    user_id      bigint        default 0  not null
);

comment on table adi_character_message_ref_embedding is '角色消息引用的知识库向量命中（溯源表）。embedding_id 指向 adi_knowledge_base_embedding[_<suffix>]。| Provenance of knowledge-base vector hits used by a character message. Sources adi_knowledge_base_embedding[_<suffix>]. Sibling tables: adi_character_message_ref_graph (KB graph hits), adi_character_message_ref_memory_embedding (character memory hits).';
comment on column adi_character_message_ref_embedding.message_id is 'adi_character_message.id this hit was used by';
comment on column adi_character_message_ref_embedding.embedding_id is 'UUID of the matched row in the KB vector store (adi_knowledge_base_embedding[_<suffix>])';
comment on column adi_character_message_ref_embedding.score is 'Similarity score returned by the vector store at retrieval time (0-1)';
comment on column adi_character_message_ref_embedding.user_id is 'Owner user id (denormalized from the message for fast filtering)';

create table adi_character_message_ref_graph
(
    id                     bigserial primary key,
    message_id             bigint default 0  not null,
    entities_from_question text   default '' not null, -- formerly graph_from_llm
    graph_from_store       text   default '' not null,
    user_id                bigint default 0  not null
);

comment on table adi_character_message_ref_graph is '角色消息引用的知识库图谱命中（溯源表）。每条消息一行（整张子图整体序列化为 JSON，不做边/点拆表）。来源：KB 图谱存储（Neo4j 或带 AGE 的 PgVector）。| Provenance of knowledge-base graph hits used by a character message. One row per message; the retrieved subgraph is stored as JSON, not normalized into edges. Source: KB graph store (Neo4j or PgVector with AGE). Sibling tables: adi_character_message_ref_embedding (KB vector hits), adi_character_message_ref_memory_embedding (character memory hits).';
comment on column adi_character_message_ref_graph.message_id is 'adi_character_message.id this subgraph was used by';
comment on column adi_character_message_ref_graph.entities_from_question is 'Vertex names extracted from the user question, comma-separated (e.g. "vertexName1,vertexName2")';
comment on column adi_character_message_ref_graph.graph_from_store is 'Retrieved subgraph serialized as JSON: {"vertices":[{"id":"111","name":"vertexName1"}],"edges":[{"id":"333","name":"edgeName1","start":"111","end":"222"}]}';
comment on column adi_character_message_ref_graph.user_id is 'Owner user id (denormalized from the message for fast filtering)';

create table adi_character_message_ref_memory_embedding
(
    id           bigserial primary key,
    message_id   bigint        default 0  not null,
    embedding_id varchar(36)   default '' not null,
    score        numeric(3, 2) default 0  not null,
    memory_type  smallint      default 1  not null,
    user_id      bigint        default 0  not null
);
comment on table adi_character_message_ref_memory_embedding is '角色消息引用的长期记忆命中（溯源表）。memory_type 列直接路由到对应的物理向量库（1=semantic、2=episodic、3=procedural 预留），新增类型只能 append，不得复用或重排。姊妹表：adi_character_message_ref_embedding（KB 向量命中）、adi_character_message_ref_graph（KB 图谱命中）。 | Provenance of long-term memory hits used by a character message. The memory_type column routes each row to its physical vector store (1=semantic, 2=episodic, 3=procedural-reserved). Codes are append-only — never reuse or reorder. Sibling tables: adi_character_message_ref_embedding (KB vector hits), adi_character_message_ref_graph (KB graph hits).';
comment on column adi_character_message_ref_memory_embedding.message_id is 'adi_character_message.id this memory hit was used by';
comment on column adi_character_message_ref_memory_embedding.embedding_id is 'UUID of the matched row in the semantic / episodic / procedural memory vector store; pick the store via memory_type';
comment on column adi_character_message_ref_memory_embedding.score is 'Similarity score returned by the vector store at retrieval time (0-1)';
comment on column adi_character_message_ref_memory_embedding.memory_type is 'Routing code: 1=semantic, 2=episodic, 3=procedural';
comment on column adi_character_message_ref_memory_embedding.user_id is 'Owner user id (denormalized from the message for fast filtering)';

-- ============================================================
-- LLM Call Record: unified LLM call resource consumption tracking
-- ============================================================

CREATE TABLE adi_llm_call_record
(
    id             bigserial primary key,
    uuid           varchar(32)  not null default '',
    source_type    smallint     not null default 0,
    source_id      bigint       not null default 0,
    user_id        bigint       not null default 0,
    model_platform varchar(50)  not null default '',
    model_name     varchar(100) not null default '',
    input_tokens   integer      not null default 0,
    output_tokens  integer      not null default 0,
    duration       integer      not null default 0,
    request_time   timestamp    not null default CURRENT_TIMESTAMP,
    create_time    timestamp    not null default CURRENT_TIMESTAMP,
    update_time    timestamp    not null default CURRENT_TIMESTAMP,
    is_deleted     boolean      not null default false
);

CREATE INDEX idx_llm_call_record_source ON adi_llm_call_record(source_type, source_id);
CREATE INDEX idx_llm_call_record_user_id ON adi_llm_call_record(user_id);
CREATE INDEX idx_llm_call_record_request_time ON adi_llm_call_record(request_time);

COMMENT ON TABLE adi_llm_call_record IS 'Unified LLM call resource consumption tracking';
COMMENT ON COLUMN adi_llm_call_record.source_type IS 'Source type: 0=unknown 1=character_chat 2=knowledge_base_qa 3=knowledge_base_ingest 4=workflow_node 5=agent';
COMMENT ON COLUMN adi_llm_call_record.source_id IS 'Source record primary key';
COMMENT ON COLUMN adi_llm_call_record.user_id IS 'User ID';
COMMENT ON COLUMN adi_llm_call_record.model_platform IS 'Model platform name';
COMMENT ON COLUMN adi_llm_call_record.model_name IS 'Model name';
COMMENT ON COLUMN adi_llm_call_record.input_tokens IS 'Input token count';
COMMENT ON COLUMN adi_llm_call_record.output_tokens IS 'Output token count';
COMMENT ON COLUMN adi_llm_call_record.duration IS 'Call duration in ms';
COMMENT ON COLUMN adi_llm_call_record.request_time IS 'Request start time';

-- ============================================================
-- File & Prompt: file storage and prompt templates
-- ============================================================

CREATE TABLE adi_file
(
    id               bigserial primary key,
    name             varchar(120) default ''                not null,
    uuid             varchar(32)  default ''                not null,
    ext              varchar(36)  default ''                not null,
    user_id          bigint       default 0                 not null,
    path             varchar(250) default ''                not null,
    storage_location int          default 1                 not null,
    ref_count        integer      default 0                 not null,
    create_time      timestamp    default CURRENT_TIMESTAMP not null,
    update_time      timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted       boolean      default false             not null,
    sha256           varchar(64)  default ''                not null
);

COMMENT ON TABLE adi_file IS 'File';
COMMENT ON COLUMN adi_file.name IS 'File name';
COMMENT ON COLUMN adi_file.uuid IS 'UUID of the file';
COMMENT ON COLUMN adi_file.ext IS 'File extension';
COMMENT ON COLUMN adi_file.user_id IS 'User ID, 0: System; Other: User';
COMMENT ON COLUMN adi_file.path IS 'File path or object name, e.g., https://*.png or 123.png (name in OSS bucket)';
COMMENT ON COLUMN adi_file.storage_location IS 'Storage Location: 1 - Local Storage, 2 - Alibaba Cloud OSS';
COMMENT ON COLUMN adi_file.ref_count IS 'The number of references to this file';
COMMENT ON COLUMN adi_file.create_time IS 'Creation time';
COMMENT ON COLUMN adi_file.update_time IS 'Last update time';
COMMENT ON COLUMN adi_file.is_deleted IS 'Whether the record is soft-deleted';
COMMENT ON COLUMN adi_file.sha256 IS 'Hash of the file';
CREATE TRIGGER trigger_file_update_time
    BEFORE UPDATE
    ON adi_file
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_prompt
(
    id          bigserial primary key,
    user_id     bigint                 default 0                 not null,
    act         character varying(120) default ''                not null,
    prompt      text                                             not null,
    create_time timestamp              default CURRENT_TIMESTAMP not null,
    update_time timestamp              default CURRENT_TIMESTAMP not null,
    is_deleted  boolean                default false             not null
);

COMMENT ON TABLE adi_prompt IS 'Prompt template';

COMMENT ON COLUMN adi_prompt.user_id IS 'Owner user ID (0: system)';

COMMENT ON COLUMN adi_prompt.act IS 'Prompt title';

COMMENT ON COLUMN adi_prompt.prompt IS 'Prompt content';

COMMENT ON COLUMN adi_prompt.create_time IS 'Creation time';

COMMENT ON COLUMN adi_prompt.update_time IS 'Last update time';

COMMENT ON COLUMN adi_prompt.is_deleted IS 'Whether the record is soft-deleted';

CREATE TRIGGER trigger_prompt_update_time
    BEFORE UPDATE
    ON adi_prompt
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ============================================================
-- System Config & User: system configuration and user management
-- ============================================================

CREATE TABLE adi_sys_config
(
    id          bigserial primary key,
    name        character varying(100)  default ''             not null,
    value       character varying(1000) default ''             not null,
    create_time timestamp               default localtimestamp not null,
    update_time timestamp               default localtimestamp not null,
    is_deleted  boolean                 default false          not null
);

COMMENT ON TABLE adi_sys_config IS 'System configuration table';
COMMENT ON COLUMN adi_sys_config.name IS 'Configuration item name';
COMMENT ON COLUMN adi_sys_config.value IS 'Configuration item value';
COMMENT ON COLUMN adi_sys_config.create_time IS 'Creation time';
COMMENT ON COLUMN adi_sys_config.update_time IS 'Last update time';
COMMENT ON COLUMN adi_sys_config.is_deleted IS 'Whether the record is soft-deleted';

CREATE TRIGGER trigger_sys_config_update_time
    BEFORE UPDATE
    ON adi_sys_config
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_user
(
    id                              bigserial primary key,
    name                            character varying(45)  default ''                not null,
    password                        character varying(120) default ''                not null,
    uuid                            character varying(32)  default ''                not null,
    email                           character varying(120) default ''                not null,
    active_time                     timestamp,
    user_status                     smallint               default '1'::smallint     not null,
    is_admin                        boolean                default false             not null,
    quota_by_token_daily            integer                default 0                 not null,
    quota_by_token_monthly          integer                default 0                 not null,
    quota_by_request_daily          integer                default 0                 not null,
    quota_by_request_monthly        integer                default 0                 not null,
    quota_by_image_daily            integer                default 0                 not null,
    quota_by_image_monthly          integer                default 0                 not null,
    locale                          character varying(10)  default ''                not null,
    create_time                     timestamp              default CURRENT_TIMESTAMP not null,
    update_time                     timestamp              default CURRENT_TIMESTAMP not null,
    is_deleted                      boolean                default false             not null
);

COMMENT ON TABLE adi_user IS 'User table';
COMMENT ON COLUMN adi_user.name IS 'Username';
COMMENT ON COLUMN adi_user.password IS 'Password';
COMMENT ON COLUMN adi_user.uuid IS 'UUID of the user';
COMMENT ON COLUMN adi_user.email IS 'User email';
COMMENT ON COLUMN adi_user.active_time IS 'Activation time';
COMMENT ON COLUMN adi_user.create_time IS 'Creation time';
COMMENT ON COLUMN adi_user.update_time IS 'Last update time';
COMMENT ON COLUMN adi_user.user_status IS 'User status: 1=Pending verification, 2=Active, 3=Frozen';
COMMENT ON COLUMN adi_user.is_admin IS 'Whether the user is an admin';
COMMENT ON COLUMN adi_user.is_deleted IS 'Whether the record is soft-deleted';
COMMENT ON COLUMN adi_user.quota_by_token_daily IS 'Daily token quota';
COMMENT ON COLUMN adi_user.quota_by_token_monthly IS 'Monthly token quota';
COMMENT ON COLUMN adi_user.quota_by_request_daily IS 'Daily request quota';
COMMENT ON COLUMN adi_user.quota_by_request_monthly IS 'Monthly request quota';
COMMENT ON COLUMN adi_user.quota_by_image_daily IS 'Daily image quota';
COMMENT ON COLUMN adi_user.quota_by_image_monthly IS 'Monthly image quota';

CREATE TRIGGER trigger_user_update_time
    BEFORE UPDATE
    ON adi_user
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_user_day_cost
(
    id            bigserial primary key,
    user_id       bigint    default 0                 not null,
    day           integer   default 0                 not null,
    tokens        integer   default 0                 not null,
    draw_times    integer   default 0                 not null,
    request_times integer   default 0                 not null,
    is_free       boolean   default false             not null,
    create_time   timestamp default CURRENT_TIMESTAMP not null,
    update_time   timestamp default CURRENT_TIMESTAMP not null,
    is_deleted    boolean   default false             not null
);

COMMENT ON TABLE adi_user_day_cost IS 'User daily consumption table';
COMMENT ON COLUMN adi_user_day_cost.user_id IS 'User ID';
COMMENT ON COLUMN adi_user_day_cost.day IS 'Date as an 8-digit integer, e.g., 20230901';
COMMENT ON COLUMN adi_user_day_cost.request_times IS 'Number of requests';
COMMENT ON COLUMN adi_user_day_cost.tokens IS 'Number of tokens consumed';
COMMENT ON COLUMN adi_user_day_cost.is_free IS 'Whether this row tracks free model usage (true) or paid model usage (false)';
COMMENT ON COLUMN adi_user_day_cost.create_time IS 'Creation time';
COMMENT ON COLUMN adi_user_day_cost.update_time IS 'Last update time';
COMMENT ON COLUMN adi_user_day_cost.draw_times IS 'Number of images';

CREATE TRIGGER trigger_user_day_cost_update_time
    BEFORE UPDATE
    ON adi_user_day_cost
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE adi_user_ext_api_key
(
    id            bigserial primary key,
    user_id       bigint        default 0                 not null,
    resource_type varchar(20)   default ''                not null,
    api_key       varchar(256)  default ''                not null,
    create_time   timestamp     default CURRENT_TIMESTAMP not null,
    update_time   timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted    boolean       default false             not null
);

CREATE UNIQUE INDEX udx_user_resource_type
    ON adi_user_ext_api_key(user_id, resource_type)
    WHERE is_deleted = false;

COMMENT ON TABLE adi_user_ext_api_key IS 'User-level External API Key';
COMMENT ON COLUMN adi_user_ext_api_key.user_id IS 'User ID';
COMMENT ON COLUMN adi_user_ext_api_key.resource_type IS 'Resource type: draw, mcp';
COMMENT ON COLUMN adi_user_ext_api_key.api_key IS 'Encrypted External API Key';

CREATE TRIGGER trigger_user_ext_api_key_update_time
    BEFORE UPDATE
    ON adi_user_ext_api_key
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ============================================================
-- Knowledge Base: RAG with vector and graph search
-- ============================================================

create table adi_knowledge_base
(
    id                     bigserial primary key,
    uuid                   varchar(32)   default ''                not null,
    title                  varchar(250)  default ''                not null,
    remark                 text          default ''                not null,
    is_public              boolean       default false             not null,
    is_strict              boolean       default true              not null,
    ingest_max_overlap     int           default 0                 not null,
    ingest_split_strategy  varchar(20)   default 'sentence_cn'     not null,
    ingest_max_segment_size int          default 512               not null,
    ingest_custom_separator varchar(100) default ''                not null,
    ingest_model_name      varchar(45)   default ''                not null,
    ingest_model_id        bigint        default 0                 not null,
    ingest_token_estimator varchar(45)   default ''                not null,
    ingest_embedding_model varchar(45)   default ''                not null,
    retrieve_max_results   int           default 0                 not null,
    retrieve_min_score     numeric(2, 1) default 0.6               not null,
    is_retrieval_enabled   boolean       default true              not null,
    query_llm_temperature  numeric(2, 1) default 0.7               not null,
    query_system_message   varchar(1000) default ''                not null,
    owner_id               bigint        default 0                 not null,
    owner_uuid             varchar(32)   default ''                not null,
    owner_name             varchar(45)   default ''                not null,
    star_count             int           default 0                 not null,
    item_count             int           default 0                 not null,
    embedding_count        int           default 0                 not null,
    api_key                varchar(200)  default ''                not null,
    create_time            timestamp     default CURRENT_TIMESTAMP not null,
    update_time            timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted             boolean       default false             not null
);

comment on table adi_knowledge_base is 'Knowledge Base';
comment on column adi_knowledge_base.title is 'Knowledge Base Title';
comment on column adi_knowledge_base.remark is 'Knowledge Base Description';
comment on column adi_knowledge_base.is_public is 'Is Public';
comment on column adi_knowledge_base.is_strict is 'Strict mode: return no answer if no results found in knowledge base; Non-strict: fall back to LLM if no results';
comment on column adi_knowledge_base.ingest_max_overlap is 'Max overlap (in tokens) when chunking documents; measured to change nothing with the current splitter (it only repeats a whole sub-segment, never a partial one) -- keep 0 (see migrations 024/025)';
comment on column adi_knowledge_base.ingest_split_strategy is 'Split strategy: sentence_cn (default, CJK sentence aware) / recursive / paragraph / line / sentence / custom';
comment on column adi_knowledge_base.ingest_max_segment_size is 'Max segment size in tokens when chunking documents; default 512 keeps one segment focused on a single idea';
comment on column adi_knowledge_base.ingest_custom_separator is 'Custom separator for splitting, only used when strategy is custom';
comment on column adi_knowledge_base.ingest_model_name is 'LLM used for indexing/graphing documents, defaults to first available LLM';
comment on column adi_knowledge_base.ingest_model_id is 'LLM ID for indexing/graphing, defaults to first available LLM';
comment on column adi_knowledge_base.ingest_token_estimator is 'Token count estimator, default is OpenAiTokenizer';
comment on column adi_knowledge_base.ingest_embedding_model is 'Embedding model for document embedding, default is all-minilm-l6-v2';
comment on column adi_knowledge_base.retrieve_max_results is 'Max recall vectors; 0 = auto (derived from the model context window, capped by RAG_RETRIEVE_NUMBER_MAX). This is the hard ceiling for every later ranking step: a ranker can only reorder what was pooled | 召回向量条数上限，0 表示按模型上下文窗口自适应（上限见 RAG_RETRIEVE_NUMBER_MAX）；这是排序侧一切手段的硬天花板——重排只能在已召回的候选里排序，池外的东西救不回来。实测：池=3 答案在池内 86.3% / 池=5 95.9% / 池=10 100.0%';
comment on column adi_knowledge_base.retrieve_min_score is 'Min score for vector search hits, 0 means use default';
comment on column adi_knowledge_base.is_retrieval_enabled IS 'Whether this KB participates in automatic chat retrieval; false = chat pipeline skips it (ext API explicit calls unaffected)';
comment on column adi_knowledge_base.query_llm_temperature is 'LLM response creativity/randomness specified during user query';
COMMENT ON COLUMN adi_knowledge_base.query_system_message IS 'System message for LLM';
comment on column adi_knowledge_base.star_count is 'Number of Likes';
comment on column adi_knowledge_base.item_count is 'Number of Knowledge Items';
comment on column adi_knowledge_base.embedding_count is 'Number of embeddings';
comment on column adi_knowledge_base.owner_id is 'Owner ID';
comment on column adi_knowledge_base.owner_uuid is 'Owner UUID';
comment on column adi_knowledge_base.owner_name is 'Owner Name';
comment on column adi_knowledge_base.create_time is 'Creation time';
comment on column adi_knowledge_base.update_time is 'Last update time';
comment on column adi_knowledge_base.is_deleted is 'Whether the record is soft-deleted';
comment on column adi_knowledge_base.api_key is 'API key for external system integration (AES encrypted)';

create trigger trigger_kb_update_time
    before update
    on adi_knowledge_base
    for each row
execute procedure update_modified_column();

create table adi_document
(
    id                           bigserial primary key,
    uuid                         varchar(32)  default ''                not null,
    kb_id                        bigint       default 0                 not null,
    kb_uuid                      varchar(32)  default ''                not null,
    source_file_id               bigint       default 0                 not null,
    title                        varchar(250) default ''                not null,
    brief                        varchar(250) default ''                not null,
    remark                       text         default ''                not null,
    segment_mode                 varchar(20)  default 'text'            not null,
    embedding_status             int          default 1                 not null,
    embedding_status_change_time timestamp    default CURRENT_TIMESTAMP not null,
    graphical_status             int          default 1                 not null,
    graphical_status_change_time timestamp    default CURRENT_TIMESTAMP not null,
    embedding_hit_count          int          default 0                 not null,
    graph_hit_count              int          default 0                 not null,
    word_count                   int          GENERATED ALWAYS AS (char_length(remark)) STORED not null,
    is_enabled                   boolean      default true              not null,
    enabled_change_time          timestamp    default CURRENT_TIMESTAMP not null,
    index_version                int          default 0                 not null,
    child_max_chunk_size         int          default 200               not null,
    fail_reason                  varchar(500) default ''                not null,
    source_type                  varchar(20)  default 'file'            not null,
    source_url                   varchar(1000) default ''               not null,
    source_method                varchar(10)  default 'GET'             not null,
    source_headers               text         default ''                not null,
    source_body                  text         default ''                not null,
    content_path                 varchar(200) default ''                not null,
    last_fetch_time              timestamp,
    fetch_interval_minutes       int          default 0                 not null,
    next_fetch_time              timestamp,
    create_time                  timestamp    default CURRENT_TIMESTAMP not null,
    update_time                  timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted                   boolean      default false             not null
);

comment on table adi_document is 'Knowledge Base Document';
comment on column adi_document.kb_id is 'Knowledge Base ID';
comment on column adi_document.source_file_id is 'Source File ID';
comment on column adi_document.title is 'Document Title';
comment on column adi_document.brief is 'Document Brief';
comment on column adi_document.remark is 'Document Content';
comment on column adi_document.segment_mode is 'Segment mode of this document: text | qa | parent_child. text: segments are chunks (vectorized). qa: document_segment rows are answers (not vectorized), questions live in adi_document_segment_question (vectorized). parent_child: document_segment rows are parent chunks (not vectorized), child chunks live in adi_document_segment_child_chunk (vectorized)';
comment on column adi_document.embedding_status is 'Embedding status: 1=Not embedded, 2=Embedding, 3=Embedded, 4=Failed';
comment on column adi_document.embedding_status_change_time is 'Last embedding status change time';
comment on column adi_document.graphical_status is 'Graphical status: 1=Not graphed, 2=Graphing, 3=Graphed, 4=Failed';
comment on column adi_document.graphical_status_change_time is 'Last graphical status change time';
comment on column adi_document.embedding_hit_count is 'How many times this document was recalled via vector (embedding) retrieval';
comment on column adi_document.graph_hit_count is 'How many times this document was recalled via graph retrieval';
comment on column adi_document.word_count is 'Character count of the document content (auto-computed by PostgreSQL: char_length(remark))';
comment on column adi_document.is_enabled is 'Whether the document is enabled for retrieval (false = its segments are excluded from vector/graph search)';
comment on column adi_document.enabled_change_time is 'Last enabled/disabled status change time';
comment on column adi_document.index_version is 'Generation of indexed artifacts built from this document; +1 on remark / segment_mode / KB split-param change (title and other metadata excluded). Index tasks snapshot it for staleness detection and merge-debounce';
comment on column adi_document.child_max_chunk_size is 'Parent-child mode: max child chunk size in tokens; document-level, applies to this document only';
comment on column adi_document.fail_reason is 'Failure reason of the latest failed async pipeline on this document (embedding task, graph task or QA generation), prefixed with the failing stage (vectorize: / graph: / qa_generate:); empty when not failed, cleared on success/retry';
comment on column adi_document.source_type is 'Document source type: file | url | api | 文档来源：file=上传文件，url=网页抓取，api=接口拉取';
comment on column adi_document.source_url is 'Fetch address for url/api sources (http/https only); empty for uploaded files | url/api 来源的抓取地址，file 来源留空';
comment on column adi_document.source_method is 'HTTP method for api sources: GET (default) or POST | api 来源的请求方法';
comment on column adi_document.source_headers is 'Custom request headers for api sources, stored as JSON text (not jsonb: see MyBatis typeHandler pitfalls) | api 来源的自定义请求头，JSON 文本';
comment on column adi_document.source_body is 'Request body for api sources (POST) | api 来源的请求体';
comment on column adi_document.content_path is 'Dot path to the text field inside a JSON api response (e.g. data.list.content); empty means the whole response | api 响应的取值路径，空表示整段响应';
comment on column adi_document.last_fetch_time is 'Timestamp of the last successful fetch; null for uploaded files or sources never fetched | 上次成功抓取时间';
comment on column adi_document.fetch_interval_minutes is 'Auto re-fetch period in minutes; 0 disables scheduled sync (manual re-fetch only) | 自动同步周期（分钟），0=不自动同步';
comment on column adi_document.next_fetch_time is 'When the scheduled sync should run next; scanned by the scheduled job | 下次自动同步时间';
comment on column adi_document.create_time is 'Creation time';
comment on column adi_document.update_time is 'Last update time';
comment on column adi_document.is_deleted is 'Whether the record is soft-deleted';

-- Partial index for the scheduled re-fetch scanner: only url/api sources with a due time
create index if not exists idx_document_next_fetch on adi_document (next_fetch_time) where source_type in ('url', 'api');

create trigger trigger_document_update_time
    before update
    on adi_document
    for each row
execute procedure update_modified_column();

-- Single source of truth for segment content:
--   * text mode    -> one row per chunk; content = chunk text; embedding_id NOT NULL (vectorized)
--   * qa mode      -> one row per answer; content = answer text; embedding_id NULL
--                     (questions are vectorized, stored in adi_document_segment_question)
--   * parent_child -> one row per parent chunk; content = parent text; embedding_id NULL
--                     (child chunks are vectorized, stored in adi_document_segment_child_chunk)
create table adi_document_segment
(
    id           bigserial primary key,
    uuid         varchar(32) default ''                 not null,
    kb_uuid      varchar(32) default ''                 not null,
    doc_uuid     varchar(32) default ''                 not null,
    position     int         default 0                  not null,
    content      text                                    not null,
    word_count   int         GENERATED ALWAYS AS (char_length(content)) STORED not null,
    hit_count    int         default 0                  not null,
    embedding_id varchar(64),
    source       varchar(20) default 'doc'              not null,
    is_enabled          boolean   default true                not null,
    enabled_change_time timestamp default CURRENT_TIMESTAMP  not null,
    embedding_status    int       default 3                   not null,
    graphical_status    int       default 3                   not null,
    fail_reason         varchar(500) default ''               not null,
    index_version       int       default 0                   not null,
    create_time  timestamp   default CURRENT_TIMESTAMP  not null,
    update_time  timestamp   default CURRENT_TIMESTAMP  not null,
    is_deleted   boolean     default false              not null
);

comment on table adi_document_segment is 'Document Segment (text chunk / QA answer / parent-child parent chunk) - single source of truth for segment content and metadata';
comment on column adi_document_segment.uuid is 'Segment UUID (also used as graph textSegmentId when the segment is graph-indexed)';
comment on column adi_document_segment.kb_uuid is 'Knowledge Base UUID';
comment on column adi_document_segment.doc_uuid is 'Document UUID (adi_document.uuid)';
comment on column adi_document_segment.position is 'Zero-based order of the segment inside the document';
comment on column adi_document_segment.content is 'text: chunk text / qa: answer text / parent_child: parent chunk text (the content returned to the LLM after retrieval expansion)';
comment on column adi_document_segment.word_count is 'Character count of the content (auto-computed by PostgreSQL: char_length(content))';
comment on column adi_document_segment.hit_count is 'text: direct hits; qa/parent_child: propagated +1 when a linked question/child chunk is hit';
comment on column adi_document_segment.embedding_id is 'Vector store entry id; NOT NULL only in text mode (answers and parent chunks are not vectorized)';
comment on column adi_document_segment.source is 'Origin of the segment: doc (from document ingestion) | manual (created manually) | annotation (reserved for the future annotation feature)';
comment on column adi_document_segment.create_time is 'Creation time';
comment on column adi_document_segment.update_time is 'Last update time';
comment on column adi_document_segment.is_deleted is 'Whether the record is soft-deleted';
comment on column adi_document_segment.is_enabled is 'Whether this segment is enabled for retrieval (false = its vector & graph data has been deleted; enabling re-generates them)';
comment on column adi_document_segment.enabled_change_time is 'Last enabled/disabled status change time';
comment on column adi_document_segment.embedding_status is 'Rebuild status of this segment''s vector data (segment-level, used by enable-segment async rebuild): 1=none (disabled), 2=rebuilding, 3=ready, 4=failed. Legacy rows default to 3';
comment on column adi_document_segment.graphical_status is 'Rebuild status of this segment''s graph data (segment-level, used by enable-segment async rebuild): 1=none (disabled), 2=rebuilding, 3=ready, 4=failed. Legacy rows default to 3';
comment on column adi_document_segment.fail_reason is 'Failure reason of the latest failed async pipeline on this segment (embedding or graph rebuild), prefixed with the failing stage (vectorize: / graph:); empty when not failed, cleared on success/retry';
comment on column adi_document_segment.index_version is 'Generation of indexed artifacts built from this segment; +1 on segment content edit. Segment-level index tasks snapshot it for staleness detection';

create unique index uk_document_segment_uuid on adi_document_segment (uuid);
create index idx_document_segment_doc on adi_document_segment (doc_uuid, position);
create index idx_document_segment_embedding on adi_document_segment (embedding_id);
create index idx_document_segment_kb on adi_document_segment (kb_uuid);

create trigger trigger_document_segment_update_time
    before update
    on adi_document_segment
    for each row
execute procedure update_modified_column();

create table adi_document_segment_question
(
    id                bigserial primary key,
    uuid              varchar(32) default ''                 not null,
    kb_uuid           varchar(32) default ''                 not null,
    doc_uuid          varchar(32) default ''                 not null,
    answer_segment_id bigint      default 0                  not null,
    position          int         default 0                  not null,
    content           text                                    not null,
    word_count        int         GENERATED ALWAYS AS (char_length(content)) STORED not null,
    hit_count         int         default 0                  not null,
    embedding_id      varchar(64),
    create_time       timestamp   default CURRENT_TIMESTAMP  not null,
    update_time       timestamp   default CURRENT_TIMESTAMP  not null,
    is_deleted        boolean     default false              not null
);

comment on table adi_document_segment_question is 'QA-mode question of a document segment; the question text is vectorized, the answer lives in adi_document_segment';
comment on column adi_document_segment_question.uuid is 'Question UUID';
comment on column adi_document_segment_question.kb_uuid is 'Knowledge Base UUID';
comment on column adi_document_segment_question.doc_uuid is 'Document UUID (adi_document.uuid)';
comment on column adi_document_segment_question.answer_segment_id is 'Answer segment id (adi_document_segment.id); multiple questions may point to the same answer';
comment on column adi_document_segment_question.position is 'Zero-based order of the question within its answer segment';
comment on column adi_document_segment_question.content is 'Question original text (this is the content that gets vectorized)';
comment on column adi_document_segment_question.word_count is 'Character count of the question (auto-computed by PostgreSQL: char_length(content))';
comment on column adi_document_segment_question.hit_count is 'How many times this question was hit by vector retrieval';
comment on column adi_document_segment_question.embedding_id is 'Vector store entry id';
comment on column adi_document_segment_question.create_time is 'Creation time';
comment on column adi_document_segment_question.update_time is 'Last update time';
comment on column adi_document_segment_question.is_deleted is 'Whether the record is soft-deleted';

create unique index uk_document_segment_question_uuid on adi_document_segment_question (uuid);
create index idx_document_segment_question_answer on adi_document_segment_question (answer_segment_id, position);
create index idx_document_segment_question_doc on adi_document_segment_question (doc_uuid);
create index idx_document_segment_question_embedding on adi_document_segment_question (embedding_id);

create trigger trigger_document_segment_question_update_time
    before update
    on adi_document_segment_question
    for each row
execute procedure update_modified_column();

create table adi_document_segment_child_chunk
(
    id                bigserial primary key,
    uuid              varchar(32) default ''                 not null,
    kb_uuid           varchar(32) default ''                 not null,
    doc_uuid          varchar(32) default ''                 not null,
    parent_segment_id bigint      default 0                  not null,
    position          int         default 0                  not null,
    content           text                                    not null,
    word_count        int         GENERATED ALWAYS AS (char_length(content)) STORED not null,
    hit_count         int         default 0                  not null,
    embedding_id      varchar(64),
    create_time       timestamp   default CURRENT_TIMESTAMP  not null,
    update_time       timestamp   default CURRENT_TIMESTAMP  not null,
    is_deleted        boolean     default false              not null
);

comment on table adi_document_segment_child_chunk is 'Parent-child mode child chunk of a document segment; the child text is vectorized, the parent lives in adi_document_segment';
comment on column adi_document_segment_child_chunk.uuid is 'Child chunk UUID';
comment on column adi_document_segment_child_chunk.kb_uuid is 'Knowledge Base UUID';
comment on column adi_document_segment_child_chunk.doc_uuid is 'Document UUID (adi_document.uuid)';
comment on column adi_document_segment_child_chunk.parent_segment_id is 'Parent segment id (adi_document_segment.id); multiple children belong to one parent';
comment on column adi_document_segment_child_chunk.position is 'Zero-based order of the child chunk within its parent segment';
comment on column adi_document_segment_child_chunk.content is 'Child chunk original text (this is the content that gets vectorized)';
comment on column adi_document_segment_child_chunk.word_count is 'Character count of the child chunk (auto-computed by PostgreSQL: char_length(content))';
comment on column adi_document_segment_child_chunk.hit_count is 'How many times this child chunk was hit by vector retrieval';
comment on column adi_document_segment_child_chunk.embedding_id is 'Vector store entry id';
comment on column adi_document_segment_child_chunk.create_time is 'Creation time';
comment on column adi_document_segment_child_chunk.update_time is 'Last update time';
comment on column adi_document_segment_child_chunk.is_deleted is 'Whether the record is soft-deleted';

create unique index uk_document_segment_child_chunk_uuid on adi_document_segment_child_chunk (uuid);
create index idx_document_segment_child_chunk_parent on adi_document_segment_child_chunk (parent_segment_id, position);
create index idx_document_segment_child_chunk_doc on adi_document_segment_child_chunk (doc_uuid);
create index idx_document_segment_child_chunk_embedding on adi_document_segment_child_chunk (embedding_id);

create trigger trigger_document_segment_child_chunk_update_time
    before update
    on adi_document_segment_child_chunk
    for each row
execute procedure update_modified_column();

create table adi_knowledge_base_star
(
    id          bigserial primary key,
    kb_id       bigint      default 0                 not null,
    kb_uuid     varchar(32) default ''                not null,
    user_id     bigint      default '0'               not null,
    user_uuid   varchar(32) default ''                not null,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted  boolean     default false             not null,
    UNIQUE (kb_id, user_id)
);

comment on table adi_knowledge_base_star is 'Knowledge Base - Like Records';
comment on column adi_knowledge_base_star.kb_id is 'adi_knowledge_base id';
comment on column adi_knowledge_base_star.kb_uuid is 'adi_knowledge_base uuid';
comment on column adi_knowledge_base_star.user_id is 'adi_user id';
comment on column adi_knowledge_base_star.user_uuid is 'adi_user uuid';
comment on column adi_knowledge_base_star.create_time is 'Creation time';
comment on column adi_knowledge_base_star.update_time is 'Last update time';
comment on column adi_knowledge_base_star.is_deleted is 'Whether the record is soft-deleted';

create trigger trigger_kb_star_update_time
    before update
    on adi_knowledge_base_star
    for each row
execute procedure update_modified_column();

create table adi_knowledge_base_qa
(
    id              bigserial primary key,
    uuid            varchar(32)   default ''                not null,
    kb_id           bigint        default 0                 not null,
    kb_uuid         varchar(32)   default ''                not null,
    question        varchar(1000) default ''                not null,
    prompt          text          default ''                not null,
    answer          text          default ''                not null,
    source_file_ids varchar(500)  default ''                not null,
    user_id         bigint        default 0                 not null,
    ai_model_id     bigint        default 0                 not null,
    create_time     timestamp     default CURRENT_TIMESTAMP not null,
    update_time     timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted      boolean       default false             not null
);

comment on table adi_knowledge_base_qa is 'Knowledge Base - Question Records';
comment on column adi_knowledge_base_qa.kb_id is 'adi_knowledge_base ID';
comment on column adi_knowledge_base_qa.kb_uuid is 'adi_knowledge_base UUID';
comment on column adi_knowledge_base_qa.question is 'User''s original question';
comment on column adi_knowledge_base_qa.prompt is 'Prompt provided to LLM';
comment on column adi_knowledge_base_qa.answer is 'Answer';
comment on column adi_knowledge_base_qa.source_file_ids is 'Source file IDs, separated by commas';
comment on column adi_knowledge_base_qa.user_id is 'User ID of the questioner';
comment on column adi_knowledge_base_qa.create_time is 'Creation time';
comment on column adi_knowledge_base_qa.update_time is 'Last update time';
comment on column adi_knowledge_base_qa.is_deleted is 'Whether the record is soft-deleted';

create trigger trigger_kb_qa_update_time
    before update
    on adi_knowledge_base_qa
    for each row
execute procedure update_modified_column();

create table adi_knowledge_base_qa_ref_embedding
(
    id           bigserial primary key,
    qa_record_id bigint        default 0  not null,
    embedding_id varchar(36)   default '' not null,
    score        numeric(3, 2) default 0  not null,
    user_id      bigint        default 0  not null
);

comment on table adi_knowledge_base_qa_ref_embedding is 'Knowledge Base - Question Records - Embedding References';
comment on column adi_knowledge_base_qa_ref_embedding.qa_record_id is 'adi_knowledge_base_qa ID';
comment on column adi_knowledge_base_qa_ref_embedding.embedding_id is 'adi_knowledge_base_embedding UUID';
comment on column adi_knowledge_base_qa_ref_embedding.score is 'Similarity score';
comment on column adi_knowledge_base_qa_ref_embedding.user_id is 'User ID';

-- Graph RAG
create table adi_knowledge_base_graph_segment
(
    id           bigserial primary key,
    uuid         varchar(32) default ''                not null,
    kb_uuid      varchar(32) default ''                not null,
    kb_item_uuid varchar(32) default ''                not null,
    remark       text        default ''                not null,
    user_id      bigint      default 0                 not null,
    create_time  timestamp   default CURRENT_TIMESTAMP not null,
    update_time  timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted   boolean     default false             not null
);

comment on table adi_knowledge_base_graph_segment is 'Knowledge Base - Graph Segment';
comment on column adi_knowledge_base_graph_segment.uuid is 'Unique identifier';
comment on column adi_knowledge_base_graph_segment.kb_uuid is 'adi_knowledge_base UUID';
comment on column adi_knowledge_base_graph_segment.kb_item_uuid is 'adi_document UUID';
comment on column adi_knowledge_base_graph_segment.remark is 'Content';
comment on column adi_knowledge_base_graph_segment.user_id is 'adi_user ID';
comment on column adi_knowledge_base_graph_segment.create_time is 'Creation time';
comment on column adi_knowledge_base_graph_segment.update_time is 'Last update time';
comment on column adi_knowledge_base_graph_segment.is_deleted is 'Whether the record is soft-deleted';

create trigger trigger_kb_graph_segment_update_time
    before update
    on adi_knowledge_base_graph_segment
    for each row
execute procedure update_modified_column();

create table adi_knowledge_base_qa_ref_graph
(
    id                     bigserial primary key,
    qa_record_id           bigint default 0  not null,
    entities_from_question text   default '' not null, -- formerly graph_from_llm
    graph_from_store       text   default '' not null,
    user_id                bigint default 0  not null
);

comment on table adi_knowledge_base_qa_ref_graph is 'Knowledge Base - Question Records - Graph References';
comment on column adi_knowledge_base_qa_ref_graph.qa_record_id is 'adi_knowledge_base_qa ID';
comment on column adi_knowledge_base_qa_ref_graph.entities_from_question is 'Graph parsed by LLM: vertexName1,vertexName2';
comment on column adi_knowledge_base_qa_ref_graph.graph_from_store is 'Graph retrieved from graph database: {vertices:[{id:"111",name:"vertexName1"},{id:"222",name:"vertexName2"}],edges:[{id:"333",name:"edgeName1",start:"111",end:"222"}]';
comment on column adi_knowledge_base_qa_ref_graph.user_id is 'adi_user ID';

-- ============================================================
-- Workflow: workflow definitions, components, runtime
-- ============================================================
create table adi_workflow_component
(
    id            bigserial primary key,
    uuid          varchar(32)  default ''                not null,
    name          varchar(32)  default ''                not null,
    title         varchar(100) default ''                not null,
    remark        text         default ''                not null,
    display_order int          default 0                 not null,
    is_enable     boolean      default false             not null,
    create_time   timestamp    default CURRENT_TIMESTAMP not null,
    update_time   timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted    boolean      default false             not null,
    constraint uk_workflow_component_name unique (name, is_deleted)
);
create trigger trigger_workflow_component
    before update
    on adi_workflow_component
    for each row
execute procedure update_modified_column();

-- Workflow Definition (User-defined Workflow)
create table adi_workflow
(
    id          bigserial primary key,
    uuid        varchar(32)  default ''                not null,
    title       varchar(100) default ''                not null,
    remark      text         default ''                not null,
    user_id     bigint       default 0                 not null,
    is_public   boolean      default false             not null,
    is_enable   boolean      default true              not null,
    api_key     varchar(200) default ''                not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);
-- definition
-- template
comment on table adi_workflow is 'Workflow Definition (User-defined Workflow)';
comment on column adi_workflow.api_key is 'API key for external system integration (AES encrypted)';
create trigger trigger_workflow
    before update
    on adi_workflow
    for each row
execute procedure update_modified_column();

create table adi_workflow_node
(
    id                    bigserial primary key,
    uuid                  varchar(32)      default ''                not null,
    workflow_id           bigint           default 0                 not null,
    workflow_component_id bigint           default 0                 not null,
    user_id               bigint           default 0                 not null,
    title                 varchar(100)     default ''                not null,
    remark                varchar(500)     default ''                not null,
    input_config          jsonb            default '{}'              not null,
    node_config           jsonb            default '{}'              not null,
    position_x            double precision default 0                 not null,
    position_y            double precision default 0                 not null,
    create_time           timestamp        default CURRENT_TIMESTAMP not null,
    update_time           timestamp        default CURRENT_TIMESTAMP not null,
    is_deleted            boolean          default false             not null
);
comment on table adi_workflow_node is 'Node of Workflow Definition';
comment on column adi_workflow_node.input_config is '{"params":[{"name":"user_define_param01","type":"string"}]}';
comment on column adi_workflow_node.node_config is '{"params":[{"prompt":"Summarize the following content:{user_define_param01}"}]}';
create trigger trigger_workflow_node
    before update
    on adi_workflow_node
    for each row
execute procedure update_modified_column();

create table adi_workflow_edge
(
    id               bigserial primary key,
    uuid             varchar(32) default ''                not null,
    workflow_id      bigint      default 0                 not null,
    source_node_uuid varchar(32) default ''                not null,
    source_handle    varchar(32) default ''                not null,
    target_node_uuid varchar(32) default ''                not null,
    create_time      timestamp   default CURRENT_TIMESTAMP not null,
    update_time      timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted       boolean     default false             not null
);
create trigger trigger_workflow_edge
    before update
    on adi_workflow_edge
    for each row
execute procedure update_modified_column();

-- Workflow Runtime
create table adi_workflow_runtime
(
    id            bigserial primary key,
    uuid          varchar(32)  default ''                not null,
    user_id       bigint       default 0                 not null,
    workflow_id   bigint       default 0                 not null,
    input         jsonb        default '{}'              not null,
    output        jsonb        default '{}'              not null,
    status        smallint     default 1                 not null,
    status_remark varchar(250) default ''                not null,
    input_tokens  int          default 0                 not null,
    output_tokens int          default 0                 not null,
    duration      int          default 0                 not null,
    create_time   timestamp    default CURRENT_TIMESTAMP not null,
    update_time   timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted    boolean      default false             not null
);
COMMENT ON COLUMN adi_workflow_runtime.input IS '{"userInput01":"text01","userInput02":true,"userInput03":10,"userInput04":["selectedA","selectedB"],"userInput05":["https://a.com/a.xlxs","https://a.com/b.png"]}';
COMMENT ON COLUMN adi_workflow_runtime.status IS 'Execution status: 1=Ready, 2=In progress, 3=Success, 4=Failed';
COMMENT ON COLUMN adi_workflow_runtime.status_remark IS 'Status remark';
COMMENT ON COLUMN adi_workflow_runtime.input_tokens IS 'Total input tokens aggregated from LLM-typed nodes (terminal snapshot, written at success / fail / waiting_input)';
COMMENT ON COLUMN adi_workflow_runtime.output_tokens IS 'Total output tokens aggregated from LLM-typed nodes (terminal snapshot)';
COMMENT ON COLUMN adi_workflow_runtime.duration IS 'Total run duration in milliseconds aggregated from all nodes (terminal snapshot)';
create trigger trigger_workflow_runtime
    before update
    on adi_workflow_runtime
    for each row
execute procedure update_modified_column();

-- Workflow Runtime - Node
create table adi_workflow_runtime_node
(
    id                  bigserial primary key,
    uuid                varchar(32)  default ''                not null,
    user_id             bigint       default 0                 not null,
    workflow_runtime_id bigint       default 0                 not null,
    node_id             bigint       default 0                 not null,
    input               jsonb        default '{}'              not null,
    output              jsonb        default '{}'              not null,
    status              smallint     default 1                 not null,
    status_remark       varchar(250) default ''                not null,
    duration            int          default 0                 not null,
    metadata            jsonb        default '{}'              not null,
    create_time         timestamp    default CURRENT_TIMESTAMP not null,
    update_time         timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted          boolean      default false             not null
);
COMMENT ON COLUMN adi_workflow_runtime_node.status IS 'Execution status: 1=In progress, 2=Failed, 3=Success';
COMMENT ON COLUMN adi_workflow_runtime_node.status_remark IS 'Status remark';
create trigger trigger_workflow_runtime_node
    before update
    on adi_workflow_runtime_node
    for each row
execute procedure update_modified_column();

COMMENT ON COLUMN adi_workflow_runtime_node.duration IS 'Node execution duration in ms';
COMMENT ON COLUMN adi_workflow_runtime_node.metadata IS 'Per-node-type runtime metadata as JSON: token usage for LLM nodes, HTTP status code for HTTP nodes, search result counts for search nodes, etc.';

-- ============================================================
-- MCP: model context protocol services
-- ============================================================

create table adi_mcp
(
    id                           bigserial primary key,
    uuid                         varchar(32)   default ''                not null,
    title                        varchar(100)  default ''                not null,
    transport_type               varchar(25)   default ''                not null,
    sse_url                      varchar(250)  default ''                not null,
    sse_timeout                  int           default 0                 not null,
    stdio_command                varchar(200)  default ''                not null,
    stdio_arg                    varchar(1024) default ''                not null,
    preset_params                jsonb         default '[]'              not null,
    customized_param_definitions jsonb         default '[]'              not null,
    install_type                 varchar(25)   default ''                not null,
    website                      varchar(250)  default ''                not null,
    remark                       text          default ''                not null,
    is_enable                    boolean       default false             not null,
    create_time                  timestamp     default CURRENT_TIMESTAMP not null,
    update_time                  timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted                   boolean       default false             not null
);
COMMENT ON TABLE adi_mcp is 'MCP server template';
COMMENT ON COLUMN adi_mcp.transport_type IS 'Transport type: sse, streamable_http, stdio';
COMMENT ON COLUMN adi_mcp.preset_params IS 'Admin-preset parameters, e.g., [{"name":"BAIDU_MAP_API_KEY","title":"Baidu Map Service","value":"111111","require_encrypt":true,"encrypted":true}]';
COMMENT ON COLUMN adi_mcp.customized_param_definitions IS 'User-configurable parameter definitions, merged with preset_params at runtime, e.g., [{"name":"GITHUB_TOKEN","title":"GitHub access token","require_encrypt":true}]';
COMMENT ON COLUMN adi_mcp.install_type IS 'Installation type: docker, local, remote, wasm';
COMMENT ON COLUMN adi_mcp.remark IS 'Supports markdown format';
COMMENT ON COLUMN adi_mcp.website IS 'Official website';

create trigger trigger_mcp
    before update
    on adi_mcp
    for each row
execute procedure update_modified_column();

create table adi_user_mcp
(
    id                    bigserial primary key,
    uuid                  varchar(32) default ''                not null,
    user_id               bigint      default 0                 not null,
    mcp_id                bigint      default 0                 not null,
    mcp_customized_params jsonb       default '[]'              not null,
    is_enable             boolean     default false             not null,
    create_time           timestamp   default CURRENT_TIMESTAMP not null,
    update_time           timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted            boolean     default false             not null
);
comment on table adi_user_mcp is 'User-enabled MCP server and configuration';
COMMENT ON COLUMN adi_user_mcp.mcp_customized_params IS 'User-defined MCP variables matching adi_mcp.customized_param_definitions, e.g., [{"name":"BAIDU_MAP_API_KEY","value":"111111","encrypted":true}]';

create trigger trigger_user_mcp
    before update
    on adi_user_mcp
    for each row
execute procedure update_modified_column();


create table adi_document_graph_vertex
(
    id           bigserial primary key,
    kb_uuid      varchar(32)               not null,
    doc_uuid     varchar(32)               not null,
    segment_uuid varchar(32)               not null,
    name         varchar(256)              not null,
    entity_type  varchar(64),
    description  text,
    create_time  timestamp   default CURRENT_TIMESTAMP not null,
    constraint uk_document_graph_vertex unique (kb_uuid, name, segment_uuid)
);
create index idx_dgv_segment on adi_document_graph_vertex (segment_uuid);
create index idx_dgv_doc on adi_document_graph_vertex (doc_uuid);
create index idx_dgv_element on adi_document_graph_vertex (kb_uuid, name);
comment on table adi_document_graph_vertex is 'Graph vertex <-> document segment provenance: one row per (vertex, segment) contribution. Authoritative for segment/doc-level graph cleanup and exclusivity judgement';
comment on column adi_document_graph_vertex.kb_uuid is 'Knowledge Base UUID (element addressing scope: vertices are identified by name within a KB)';
comment on column adi_document_graph_vertex.doc_uuid is 'Document UUID of the contributing segment (denormalized from adi_document_segment for doc-level queries)';
comment on column adi_document_graph_vertex.segment_uuid is 'Contributing segment uuid (adi_document_segment.uuid)';
comment on column adi_document_graph_vertex.name is 'Vertex entity name (same key GraphStoreIngestor uses to address vertices)';
comment on column adi_document_graph_vertex.entity_type is 'Entity type label from extraction (first-write wins, implicit endpoints null). Reserved for the future community-summary feature';
comment on column adi_document_graph_vertex.description is 'Entity description fragment extracted from THIS segment. Element-level description = concatenation of all contribution fragments (aggregated on read; never stored merged)';

create table adi_document_graph_edge
(
    id           bigserial primary key,
    kb_uuid      varchar(32)               not null,
    doc_uuid     varchar(32)               not null,
    segment_uuid varchar(32)               not null,
    source_name  varchar(256)              not null,
    target_name  varchar(256)              not null,
    description  text,
    weight       double precision default 1.0,
    create_time  timestamp   default CURRENT_TIMESTAMP not null,
    constraint uk_document_graph_edge unique (kb_uuid, source_name, target_name, segment_uuid)
);
create index idx_dge_segment on adi_document_graph_edge (segment_uuid);
create index idx_dge_doc on adi_document_graph_edge (doc_uuid);
create index idx_dge_element on adi_document_graph_edge (kb_uuid, source_name, target_name);
comment on table adi_document_graph_edge is 'Graph edge <-> document segment provenance: one row per (edge, segment) contribution';
comment on column adi_document_graph_edge.kb_uuid is 'Knowledge Base UUID';
comment on column adi_document_graph_edge.doc_uuid is 'Document UUID of the contributing segment';
comment on column adi_document_graph_edge.segment_uuid is 'Contributing segment uuid (adi_document_segment.uuid)';
comment on column adi_document_graph_edge.source_name is 'Endpoint entity name A; canonically ordered with target_name by lexicographic order (smaller first) - NOT the extraction direction';
comment on column adi_document_graph_edge.target_name is 'Endpoint entity name B; canonically ordered with source_name by lexicographic order (larger second)';
comment on column adi_document_graph_edge.description is 'Relationship description fragment extracted from THIS segment; element-level description = concatenation of fragments';
comment on column adi_document_graph_edge.weight is 'Relationship strength given by THIS extraction; element-level weight = SUM over fragments';

create table adi_index_task
(
    id             bigserial primary key,
    kb_uuid        varchar(32)               not null,
    doc_uuid       varchar(32)               not null,
    user_id        bigint                    not null,
    segment_uuid   varchar(32) default ''    not null,
    target_type    varchar(20)               not null,
    task_type      varchar(20)               not null,
    index_version  int                       not null,
    status         varchar(20)               not null,
    fail_reason    varchar(500) default '' not null,
    stop_flag      boolean     default false not null,
    executor_epoch varchar(32) default ''    not null,
    start_time     timestamp,
    heartbeat_time timestamp,
    create_time    timestamp   default CURRENT_TIMESTAMP not null,
    update_time    timestamp   default CURRENT_TIMESTAMP not null
);
-- Merge key includes index_version: one unfinished row per (target, type, version);
-- done rows leave the index and accumulate as history. A newer version therefore
-- always inserts fresh and can never be swallowed by a running row.
create unique index uk_index_task_active on adi_index_task (doc_uuid, segment_uuid, target_type, task_type, index_version) where status <> 'done';
create index idx_index_task_status on adi_index_task (status, id);
create index idx_index_task_doc on adi_index_task (doc_uuid);
comment on table adi_index_task is 'Index task queue: scheduling source of truth for all index writes (segmentation, embedding, graph extraction). At most one unfinished row per (doc_uuid, segment_uuid, target_type, task_type, index_version); done rows accumulate as run history; document-level tasks use empty segment_uuid';
comment on column adi_index_task.kb_uuid is 'Owning knowledge base uuid (denormalized for fan-out enqueue and audit; not part of the merge key)';
comment on column adi_index_task.doc_uuid is 'Target document uuid (never empty)';
comment on column adi_index_task.user_id is 'Triggering user; async executors have no ThreadContext, billing context is persisted here';
comment on column adi_index_task.segment_uuid is 'Target segment uuid for segment-level tasks; empty string for document-level tasks (PG unique indexes do not dedupe NULL)';
comment on column adi_index_task.target_type is 'document | segment';
comment on column adi_index_task.task_type is 'embedding | graphical';
comment on column adi_index_task.index_version is 'Snapshot of the target business table''s index_version at enqueue time: adi_document.index_version for document tasks, adi_document_segment.index_version for segment tasks. Part of the merge key: a newer version is always a fresh row; a running row can never swallow it';
comment on column adi_index_task.status is 'pending | running | done | failed. A newer-version enqueue supersedes same-key older pending rows to failed; failed is revived in place by a same-version re-enqueue (manual retry); done rows stay as run history';
comment on column adi_index_task.fail_reason is 'Truncated failure reason when status = failed (exception message, supersede notice, or the max-duration breaker notice); empty otherwise';
comment on column adi_index_task.stop_flag is 'Cooperative stop signal: set by a newer-version enqueue on this key''s older RUNNING row (status is NOT changed - the same-doc serialization gate stays closed until the executor aborts at its next checkpoint); reset to false on claim and on failed-row revive';
comment on column adi_index_task.executor_epoch is 'Execution epoch generated at claim: heartbeat / finalize / stop checks match on (id, epoch), so a zombie executor (its row was stale-reset and re-claimed under a new epoch) cannot finalize a row now owned by another executor';
comment on column adi_index_task.start_time is 'Claim time of the latest attempt (NULL while never run); update_time at done/failed is the finish time - the pair gives execution duration for history analysis';
comment on column adi_index_task.heartbeat_time is 'Executor liveness proof: initialized at claim and periodically refreshed while running (NULL when never run). A running row whose heartbeat is stale beyond the poller threshold is reset to pending (process-crash recovery)';
comment on column adi_index_task.update_time is 'Last row change (enqueue / supersede / claim / finish / recovery); plain audit column - liveness is judged by heartbeat_time, not this column';

-- =====================================================================
-- 二次开发增量（已并入全量快照）
-- 来源：015_finetune.sql / 016_skill.sql / 017_rag_hybrid_rerank.sql
--
-- 全新安装执行本文件时，以下区块等价于依次执行上述三个增量脚本，
-- 保证 all_ddl.sql 始终为最新状态（见 README.zh-CN.md 文件命名规范）。
-- 存量库升级不要执行本文件，按 README 单独跑 NNN_*.sql 增量脚本。
-- 后续新增的二开迁移脚本，请同步把内容追加到本区块末尾。
-- =====================================================================

-- ---------- 015_finetune：LoRA 微调训练模块 ----------

CREATE TABLE adi_finetune_dataset
(
    id           bigserial primary key,
    uuid         varchar(32)  default ''                not null,
    user_id      bigint       default 0                 not null,
    name         varchar(120) default ''                not null,
    description  varchar(500) default ''                not null,
    format       varchar(30)  default 'alpaca-jsonl'    not null,
    file_uuid    varchar(32)  default ''                not null,
    sample_count integer      default 0                 not null,
    eval_count   integer      default 0                 not null,
    token_stat   jsonb        default '{}'              not null,
    create_time  timestamp    default CURRENT_TIMESTAMP not null,
    update_time  timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted   boolean      default false             not null
);

COMMENT ON TABLE adi_finetune_dataset IS 'Finetune dataset (JSONL)';
COMMENT ON COLUMN adi_finetune_dataset.uuid IS 'UUID of the dataset';
COMMENT ON COLUMN adi_finetune_dataset.user_id IS 'Owner user ID';
COMMENT ON COLUMN adi_finetune_dataset.name IS 'Dataset name';
COMMENT ON COLUMN adi_finetune_dataset.description IS 'Dataset description';
COMMENT ON COLUMN adi_finetune_dataset.format IS 'Data format: alpaca-jsonl, sharegpt-jsonl';
COMMENT ON COLUMN adi_finetune_dataset.file_uuid IS 'UUID of the raw file row in adi_file';
COMMENT ON COLUMN adi_finetune_dataset.sample_count IS 'Total training samples';
COMMENT ON COLUMN adi_finetune_dataset.eval_count IS 'Validation samples reserved (derived from job val_ratio at train time)';
COMMENT ON COLUMN adi_finetune_dataset.token_stat IS 'Rough statistics in JSON, e.g. {"total_chars":123,"est_tokens":456}';
COMMENT ON COLUMN adi_finetune_dataset.create_time IS 'Creation time';
COMMENT ON COLUMN adi_finetune_dataset.update_time IS 'Last update time';
COMMENT ON COLUMN adi_finetune_dataset.is_deleted IS 'Whether the record is soft-deleted';

CREATE TRIGGER trigger_finetune_dataset_update_time
    BEFORE UPDATE
    ON adi_finetune_dataset
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_finetune_dataset_uuid ON adi_finetune_dataset (uuid);

CREATE TABLE adi_finetune_job
(
    id              bigserial primary key,
    uuid            varchar(32)   default ''                not null,
    user_id         bigint        default 0                 not null,
    dataset_id      bigint        default 0                 not null,
    name            varchar(120)  default ''                not null,
    base_model_name varchar(100)  default ''                not null,
    method          varchar(20)   default 'lora'            not null,
    preset          varchar(40)   default ''                not null,
    hyper_params    jsonb         default '{}'              not null,
    metrics         jsonb         default '{}'              not null,
    status          varchar(20)   default 'pending'         not null,
    progress        numeric(5, 2) default 0                 not null,
    current_epoch   integer       default 0                 not null,
    train_loss      numeric(10, 4) default 0                not null,
    eval_loss       numeric(10, 4) default 0                not null,
    worker_id       varchar(64)   default ''                not null,
    worker_epoch    varchar(64)   default ''                not null,
    stop_flag       boolean       default false             not null,
    fail_reason     varchar(500)  default ''                not null,
    log_path        varchar(250)  default ''                not null,
    create_time     timestamp     default CURRENT_TIMESTAMP not null,
    update_time     timestamp     default CURRENT_TIMESTAMP not null,
    claim_time      timestamp,
    heartbeat_time  timestamp,
    start_time      timestamp,
    end_time        timestamp,
    is_deleted      boolean       default false             not null
);

COMMENT ON TABLE adi_finetune_job IS 'Finetune training job queue (external GPU worker pulls via HTTP)';
COMMENT ON COLUMN adi_finetune_job.uuid IS 'UUID of the job';
COMMENT ON COLUMN adi_finetune_job.user_id IS 'Creator user ID';
COMMENT ON COLUMN adi_finetune_job.dataset_id IS 'Reference to adi_finetune_dataset.id';
COMMENT ON COLUMN adi_finetune_job.name IS 'Job name';
COMMENT ON COLUMN adi_finetune_job.base_model_name IS 'Base model name, e.g. Qwen2.5-7B-Instruct (LLaMA-Factory model name or HF id)';
COMMENT ON COLUMN adi_finetune_job.method IS 'Training method: lora, qlora';
COMMENT ON COLUMN adi_finetune_job.preset IS 'Parameter preset key: balanced/quality/chinese-chat/knowledge-qa/low-vram/fast-preview (traceability only, hyper_params is authoritative)';
COMMENT ON COLUMN adi_finetune_job.hyper_params IS 'Full hyper parameter snapshot (defaults filled, steps derived): lora_rank/lora_alpha/lora_dropout/lora_target/use_rslora/use_dora, learning_rate/num_epochs/batch_size/gradient_accumulation/lr_scheduler_type/warmup_ratio/weight_decay/max_grad_norm/optim, cutoff_len/val_ratio/packing/neftune_noise_alpha, gradient_checkpointing/flash_attn/seed/logging_steps/save_steps/eval_steps/early_stopping_patience, quant_bit/quant_method/export_merged/export_gguf/gguf_quant/template';
COMMENT ON COLUMN adi_finetune_job.metrics IS 'Training report summarized by the worker from trainer_state.json: final/best loss, total steps, trainable params, runtime, best checkpoint, GPU';
COMMENT ON COLUMN adi_finetune_job.status IS 'State machine: pending -> running -> success/failed/stopped';
COMMENT ON COLUMN adi_finetune_job.progress IS 'Overall progress percent 0-100';
COMMENT ON COLUMN adi_finetune_job.current_epoch IS 'Current epoch reported by the worker';
COMMENT ON COLUMN adi_finetune_job.train_loss IS 'Latest training loss';
COMMENT ON COLUMN adi_finetune_job.eval_loss IS 'Latest evaluation loss';
COMMENT ON COLUMN adi_finetune_job.worker_id IS 'Claiming worker identity';
COMMENT ON COLUMN adi_finetune_job.worker_epoch IS 'Claim epoch (regenerated per claim); late writes from a stale worker are rejected by (id, epoch)';
COMMENT ON COLUMN adi_finetune_job.stop_flag IS 'Cooperative stop flag: admin requests stop, worker aborts at next checkpoint';
COMMENT ON COLUMN adi_finetune_job.fail_reason IS 'Failure reason';
COMMENT ON COLUMN adi_finetune_job.log_path IS 'Relative path of the training log file under local storage';
COMMENT ON COLUMN adi_finetune_job.claim_time IS 'First claim time';
COMMENT ON COLUMN adi_finetune_job.heartbeat_time IS 'Worker liveness proof, refreshed on progress/heartbeat calls';
COMMENT ON COLUMN adi_finetune_job.start_time IS 'Latest (re)start time';
COMMENT ON COLUMN adi_finetune_job.end_time IS 'Terminal state time';

CREATE TRIGGER trigger_finetune_job_update_time
    BEFORE UPDATE
    ON adi_finetune_job
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_finetune_job_uuid ON adi_finetune_job (uuid);
CREATE INDEX idx_finetune_job_status ON adi_finetune_job (status);

CREATE TABLE adi_finetune_artifact
(
    id            bigserial primary key,
    uuid          varchar(32)  default ''                not null,
    job_id        bigint       default 0                 not null,
    job_uuid      varchar(32)  default ''                not null,
    user_id       bigint       default 0                 not null,
    type          varchar(20)  default 'adapter'         not null,
    name          varchar(120) default ''                not null,
    file_uuid     varchar(32)  default ''                not null,
    external_path varchar(250) default ''                not null,
    size_bytes    bigint       default 0                 not null,
    base_model    varchar(100) default ''                not null,
    remark        varchar(500) default ''                not null,
    create_time   timestamp    default CURRENT_TIMESTAMP not null,
    update_time   timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted    boolean      default false             not null
);

COMMENT ON TABLE adi_finetune_artifact IS 'Finetune output artifact (LoRA adapter / merged model / GGUF)';
COMMENT ON COLUMN adi_finetune_artifact.uuid IS 'UUID of the artifact';
COMMENT ON COLUMN adi_finetune_artifact.job_id IS 'Reference to adi_finetune_job.id';
COMMENT ON COLUMN adi_finetune_artifact.job_uuid IS 'Reference to adi_finetune_job.uuid';
COMMENT ON COLUMN adi_finetune_artifact.user_id IS 'Owner user ID';
COMMENT ON COLUMN adi_finetune_artifact.type IS 'Artifact type: adapter (zip on server), merged (dir on GPU host), gguf (file on GPU host)';
COMMENT ON COLUMN adi_finetune_artifact.name IS 'Artifact name, usually the exported model tag';
COMMENT ON COLUMN adi_finetune_artifact.file_uuid IS 'UUID of the uploaded file row in adi_file (adapter zip); empty for artifacts kept on the GPU host';
COMMENT ON COLUMN adi_finetune_artifact.external_path IS 'Path on the GPU host for large artifacts (merged/GGUF), empty for server-stored ones';
COMMENT ON COLUMN adi_finetune_artifact.size_bytes IS 'Artifact size in bytes';
COMMENT ON COLUMN adi_finetune_artifact.base_model IS 'Base model this artifact was trained on';
COMMENT ON COLUMN adi_finetune_artifact.remark IS 'Remark';

CREATE TRIGGER trigger_finetune_artifact_update_time
    BEFORE UPDATE
    ON adi_finetune_artifact
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_finetune_artifact_uuid ON adi_finetune_artifact (uuid);
CREATE INDEX idx_finetune_artifact_job ON adi_finetune_artifact (job_id);

-- ---------- 016_skill：Agent 技能模块 ----------

CREATE TABLE adi_skill
(
    id             bigserial primary key,
    uuid           varchar(32)   default ''                not null,
    user_id        bigint        default 0                 not null,
    name           varchar(64)   default ''                not null,
    title          varchar(120)  default ''                not null,
    description    varchar(1000) default ''                not null,
    version        varchar(20)   default ''                not null,
    content        text,
    resources_path varchar(250)  default ''                not null,
    has_scripts    boolean       default false             not null,
    source         varchar(20)   default 'manual'          not null,
    is_enable      boolean       default true              not null,
    is_public      boolean       default true              not null,
    create_time    timestamp     default CURRENT_TIMESTAMP not null,
    update_time    timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted     boolean       default false             not null
);

COMMENT ON TABLE adi_skill IS 'Agent skill library (SKILL.md standard: frontmatter name/description + markdown instructions + optional scripts/resources)';
COMMENT ON COLUMN adi_skill.uuid IS 'UUID of the skill';
COMMENT ON COLUMN adi_skill.user_id IS 'Creator user ID, 0: system';
COMMENT ON COLUMN adi_skill.name IS 'Skill name from SKILL.md frontmatter (unique), also used by read_skill tool';
COMMENT ON COLUMN adi_skill.title IS 'Display title';
COMMENT ON COLUMN adi_skill.description IS 'Description from SKILL.md frontmatter, injected into system prompt manifest';
COMMENT ON COLUMN adi_skill.version IS 'Skill version';
COMMENT ON COLUMN adi_skill.content IS 'SKILL.md instruction body (markdown, without frontmatter)';
COMMENT ON COLUMN adi_skill.resources_path IS 'Relative dir of extracted skill resources under local.base-data-path, e.g. skills/{uuid}; empty if skill has no extra files';
COMMENT ON COLUMN adi_skill.has_scripts IS 'Whether the skill package contains scripts/ (enables run_skill_script)';
COMMENT ON COLUMN adi_skill.source IS 'manual / zip';
COMMENT ON COLUMN adi_skill.is_enable IS 'Enabled by admin';
COMMENT ON COLUMN adi_skill.is_public IS 'Visible in skill marketplace';

CREATE TRIGGER trigger_skill_update_time
    BEFORE UPDATE
    ON adi_skill
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_skill_uuid ON adi_skill (uuid);
CREATE UNIQUE INDEX uk_skill_name ON adi_skill (name) WHERE is_deleted = false;

CREATE TABLE adi_user_skill
(
    id          bigserial primary key,
    uuid        varchar(32)  default ''                not null,
    user_id     bigint       default 0                 not null,
    skill_id    bigint       default 0                 not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);

COMMENT ON TABLE adi_user_skill IS 'User installed skills (marketplace install record + user-level toggle)';
COMMENT ON COLUMN adi_user_skill.user_id IS 'User ID';
COMMENT ON COLUMN adi_user_skill.skill_id IS 'Reference to adi_skill.id';
COMMENT ON COLUMN adi_user_skill.is_enable IS 'Whether the user enabled this skill';

CREATE TRIGGER trigger_user_skill_update_time
    BEFORE UPDATE
    ON adi_user_skill
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_user_skill ON adi_user_skill (user_id, skill_id) WHERE is_deleted = false;

ALTER TABLE adi_character
    ADD COLUMN IF NOT EXISTS skill_ids varchar(1000) default '' not null;

COMMENT ON COLUMN adi_character.skill_ids IS 'Bound agent skill IDs, comma-separated (same pattern as mcp_ids)';

-- ---------- 017_rag_hybrid_rerank：混合检索 + 重排 ----------
-- 1) 关键词路由应用层做中文 bigram 切分，只写入 search_text；search_tsv 由 PG 内置触发器
--    tsvector_update_trigger 自动维护，应用层不直接操作 tsvector，避免切分口径不一致。
-- 2) 使用 simple 配置（不做词干化、不过滤停用词），配合 bigram 对中文生效。
-- 3) 知识库级检索配置：混合检索开关、RRF k、rerank 开关与模型绑定。

-- 分段表：全文检索列
alter table adi_document_segment add column if not exists search_text text default '' not null;
comment on column adi_document_segment.search_text is
  'Bigram-tokenized content for keyword retrieval (written by app layer; search_tsv is derived from it)';

alter table adi_document_segment add column if not exists search_tsv tsvector;
comment on column adi_document_segment.search_tsv is
  'Full-text vector auto-maintained by trigger (config=pg_catalog.simple). Query with to_tsquery(''simple'', ''a|b|c'') - OR semantics, never plainto_tsquery (AND semantics returns 0 rows for long queries)';

-- 触发器：search_text 变化时自动重算 search_tsv
-- 注意：第二个参数是 regconfig，必须写全限定名 'pg_catalog.simple'。
-- 写 'simple' → 运行时报 `text search configuration name "simple" must be schema-qualified`；
-- 写 'public.simple' → 运行时报 `text search configuration "public.simple" does not exist`。
-- 两种写法 CREATE TRIGGER 阶段都不报错，要到插入/更新时才炸，会让分段入库整条链路失败——务必实测。
drop trigger if exists trg_segment_search_tsv on adi_document_segment;
create trigger trg_segment_search_tsv
    before insert or update of search_text
    on adi_document_segment
    for each row
execute function tsvector_update_trigger(search_tsv, 'pg_catalog.simple', search_text);

-- GIN 索引（部分索引：只索引未删除且启用的分段）
drop index if exists idx_segment_search_tsv;
create index idx_segment_search_tsv
    on adi_document_segment using gin (search_tsv)
    where is_deleted = false and is_enabled = true;

-- 知识库表：检索配置
alter table adi_knowledge_base add column if not exists retrieve_hybrid_enabled boolean default false not null;
comment on column adi_knowledge_base.retrieve_hybrid_enabled is
  'Enable hybrid retrieval: vector + keyword (bigram) fused by RRF. false = vector-only (legacy behavior)';

alter table adi_knowledge_base add column if not exists retrieve_rrf_k int default 60 not null;
comment on column adi_knowledge_base.retrieve_rrf_k is
  'RRF fusion constant k (score = sum(1/(k+rank))). Larger k flattens rank differences';

alter table adi_knowledge_base add column if not exists retrieve_keyword_limit int default 20 not null;
comment on column adi_knowledge_base.retrieve_keyword_limit is
  'Max results fetched from the keyword path before RRF fusion';

alter table adi_knowledge_base add column if not exists retrieve_rerank_enabled boolean default false not null;
comment on column adi_knowledge_base.retrieve_rerank_enabled is
  'Enable rerank stage after fusion. Requires a usable rerank model';

alter table adi_knowledge_base add column if not exists retrieve_rerank_model_id bigint default 0 not null;
comment on column adi_knowledge_base.retrieve_rerank_model_id is
  'Foreign key to adi_ai_model.id where type=rerank. 0 means use the first enabled rerank model';

alter table adi_knowledge_base add column if not exists retrieve_rerank_top_n int default 0 not null;
comment on column adi_knowledge_base.retrieve_rerank_top_n is
  'Keep only top-N after rerank; 0 means keep all (no truncation)';

alter table adi_knowledge_base add column if not exists retrieve_rerank_min_score numeric(3, 2) default 0.0 not null;
comment on column adi_knowledge_base.retrieve_rerank_min_score is
  'Drop reranked contents whose normalized score is below this threshold; 0.0 means keep all';

-- ============================================================
-- 二开扩展：命中片段左右扩窗（sentence-window）
-- Extension: neighbour window around each retrieval hit
-- 对应增量迁移：026_kb_neighbor_window.sql
-- Mirrors migration 026 so a fresh install gets the same schema.
--
-- 为什么需要：向量命中以「段」为单位，而答案所需信息常压在段边界（条款及其例外、标题与正文）。
-- 左右扩窗把「检索单位」与「喂给模型的单位」解耦：仍按单段检索，返回时带上邻近段。
-- 实测（73 条中文金标题库）：sentence_cn/512 下 w=1 把 recall@5 从 95.9% 提到 97.3%、MRR 从
-- 0.724 提到 0.798，代价是上下文约 2 倍；w=2 只多付约 21% 上下文但零增益，故窗口默认固定 1。
-- 局限：指标衡量的是「答案是否进入上下文」，衡量不了「答案是否更完整」——后者需 LLM 裁判，暂无。
-- 默认关闭，与 retrieve_hybrid_enabled / retrieve_rerank_enabled 保持一致。
-- 只改变送进 prompt 的文本，不改变向量化输入，因此无需重建索引。
-- ============================================================

alter table adi_knowledge_base add column if not exists retrieve_neighbor_enabled boolean default false not null;
comment on column adi_knowledge_base.retrieve_neighbor_enabled is
  'Expand each hit with its neighbouring segments of the same document before prompting | 是否把命中片段的相邻片段一并送入上下文；只影响送进prompt的文本，不需要重建索引';

alter table adi_knowledge_base add column if not exists retrieve_neighbor_window int default 1 not null;
comment on column adi_knowledge_base.retrieve_neighbor_window is
  'Segments taken on each side of a hit; 0 disables the expansion. Measured: +-1 captures all the gain, +-2 only costs context | 命中片段左右各取几个相邻片段，0表示不扩窗；实测±1已拿到全部增益';

-- 存量数据回填：新列 search_text 为空串、search_tsv 为 NULL，检索时自然不命中关键词路（等价旧行为）。
-- 存量分段如需启用混合检索，须由应用层重跑一次「重建关键词索引」（把 content 切分后写回 search_text）。
-- 增量分段在入库时由应用层直接写入 search_text，无需手工回填。

-- ============================================================
-- 二开扩展：Agent / Scene / Database MCP
-- Extension: Agent, Scene, external DB datasource (Database MCP)
-- 对应增量迁移：019_agent_scene_dbmcp.sql + 020_binding_unique_index.sql
-- Mirrors migrations 019 and 020 so a fresh install gets the same schema.
-- ============================================================

CREATE TABLE IF NOT EXISTS adi_agent
(
    id                bigserial primary key,
    uuid              varchar(32)   default ''                not null,
    user_id           bigint        default 0                 not null,
    title             varchar(100)  default ''                not null,
    remark            text          default ''                not null,
    avatar_url        varchar(500)  default ''                not null,
    system_message    text          default ''                not null,
    work_design       text          default ''                not null,
    model_platform    varchar(50)   default ''                not null,
    model_name        varchar(100)  default ''                not null,
    llm_temperature   numeric(3, 2) default 0.70              not null,
    kb_ids            varchar(500)  default ''                not null,
    mcp_ids           varchar(500)  default ''                not null,
    enable_rag        boolean       default true              not null,
    enable_mcp        boolean       default false             not null,
    enable_web_search boolean       default false             not null,
    character_uuid    varchar(32)   default ''                not null,
    sort              int           default 0                 not null,
    is_enable         boolean       default true              not null,
    create_time       timestamp     default CURRENT_TIMESTAMP not null,
    update_time       timestamp     default CURRENT_TIMESTAMP not null,
    is_deleted        boolean       default false             not null
);
COMMENT ON TABLE adi_agent IS 'Agent definition with work design | 智能体定义（含工作设计）';
COMMENT ON COLUMN adi_agent.system_message IS 'System prompt / role setting | 角色设定（系统提示词）';
COMMENT ON COLUMN adi_agent.work_design IS 'Work design JSON: goal, steps, constraints, output format | 工作设计JSON';
COMMENT ON COLUMN adi_agent.character_uuid IS 'Runtime character uuid used by chat | 对话运行时使用的角色uuid';

CREATE INDEX IF NOT EXISTS idx_adi_agent_user ON adi_agent (user_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_agent_uuid ON adi_agent (uuid);

DROP TRIGGER IF EXISTS trigger_adi_agent ON adi_agent;
CREATE TRIGGER trigger_adi_agent
    BEFORE UPDATE
    ON adi_agent
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE IF NOT EXISTS adi_agent_workflow
(
    id            bigserial primary key,
    uuid          varchar(32) default ''                not null,
    agent_uuid    varchar(32) default ''                not null,
    workflow_uuid varchar(32) default ''                not null,
    is_default    boolean     default false             not null,
    sort          int         default 0                 not null,
    create_time   timestamp   default CURRENT_TIMESTAMP not null,
    update_time   timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted    boolean     default false             not null
);
COMMENT ON TABLE adi_agent_workflow IS 'Agent to workflow binding | 智能体与工作流绑定';

CREATE INDEX IF NOT EXISTS idx_adi_agent_wf_agent ON adi_agent_workflow (agent_uuid, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_agent_wf_wf ON adi_agent_workflow (workflow_uuid, is_deleted);
-- One active binding per (agent, workflow): backs the idempotent upsert in AiAgentService
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_agent_workflow
    ON adi_agent_workflow (agent_uuid, workflow_uuid)
    WHERE is_deleted = false;

DROP TRIGGER IF EXISTS trigger_adi_agent_workflow ON adi_agent_workflow;
CREATE TRIGGER trigger_adi_agent_workflow
    BEFORE UPDATE
    ON adi_agent_workflow
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE IF NOT EXISTS adi_scene
(
    id          bigserial primary key,
    uuid        varchar(32)  default ''                not null,
    user_id     bigint       default 0                 not null,
    title       varchar(100) default ''                not null,
    remark      text         default ''                not null,
    icon        varchar(100) default ''                not null,
    sort        int          default 0                 not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);
COMMENT ON TABLE adi_scene IS 'Chat scene binding multiple agents | 对话场景（绑定多个智能体）';

CREATE INDEX IF NOT EXISTS idx_adi_scene_user ON adi_scene (user_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_scene_uuid ON adi_scene (uuid);

DROP TRIGGER IF EXISTS trigger_adi_scene ON adi_scene;
CREATE TRIGGER trigger_adi_scene
    BEFORE UPDATE
    ON adi_scene
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE IF NOT EXISTS adi_scene_agent
(
    id         bigserial primary key,
    uuid       varchar(32) default ''                not null,
    scene_uuid varchar(32) default ''                not null,
    agent_uuid varchar(32) default ''                not null,
    sort       int         default 0                 not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null,
    is_deleted boolean     default false             not null
);
COMMENT ON TABLE adi_scene_agent IS 'Scene to agent binding | 场景与智能体绑定';

CREATE INDEX IF NOT EXISTS idx_adi_scene_agent_scene ON adi_scene_agent (scene_uuid, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_scene_agent_agent ON adi_scene_agent (agent_uuid, is_deleted);
-- One active binding per (scene, agent): backs the idempotent upsert in SceneService
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_scene_agent
    ON adi_scene_agent (scene_uuid, agent_uuid)
    WHERE is_deleted = false;

DROP TRIGGER IF EXISTS trigger_adi_scene_agent ON adi_scene_agent;
CREATE TRIGGER trigger_adi_scene_agent
    BEFORE UPDATE
    ON adi_scene_agent
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE IF NOT EXISTS adi_db_datasource
(
    id          bigserial primary key,
    uuid        varchar(32)  default ''                not null,
    user_id     bigint       default 0                 not null,
    title       varchar(100) default ''                not null,
    db_type     varchar(20)  default 'postgresql'      not null,
    host        varchar(200) default ''                not null,
    port        int          default 5432              not null,
    db_name     varchar(100) default ''                not null,
    username    varchar(100) default ''                not null,
    password    varchar(300) default ''                not null,
    jdbc_params varchar(500) default ''                not null,
    remark      text         default ''                not null,
    is_enable   boolean      default true              not null,
    create_time timestamp    default CURRENT_TIMESTAMP not null,
    update_time timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted  boolean      default false             not null
);
COMMENT ON TABLE adi_db_datasource IS 'External database datasource (Database MCP) | 外部数据库数据源';
-- Only postgresql/mysql drivers are bundled; sqlserver/oracle are rejected by the service layer.
COMMENT ON COLUMN adi_db_datasource.db_type IS 'postgresql / mysql (others rejected by service) | 数据库类型（仅支持pg/mysql）';

CREATE INDEX IF NOT EXISTS idx_adi_db_ds_user ON adi_db_datasource (user_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_db_ds_uuid ON adi_db_datasource (uuid);

DROP TRIGGER IF EXISTS trigger_adi_db_datasource ON adi_db_datasource;
CREATE TRIGGER trigger_adi_db_datasource
    BEFORE UPDATE
    ON adi_db_datasource
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE TABLE IF NOT EXISTS adi_db_sync_task
(
    id               bigserial primary key,
    uuid             varchar(32)  default ''                not null,
    user_id          bigint       default 0                 not null,
    datasource_uuid  varchar(32)  default ''                not null,
    kb_uuid          varchar(32)  default ''                not null,
    title            varchar(100) default ''                not null,
    sql_text         text         default ''                not null,
    content_template text         default ''                not null,
    last_sync_time   timestamp    default null,
    last_status      varchar(20)  default ''                not null,
    last_rows        int          default 0                 not null,
    last_message     text         default ''                not null,
    is_enable        boolean      default true              not null,
    create_time      timestamp    default CURRENT_TIMESTAMP not null,
    update_time      timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted       boolean      default false             not null
);
COMMENT ON TABLE adi_db_sync_task IS 'Sync external DB query result into knowledge base | 数据库内容同步至知识库的任务';
COMMENT ON COLUMN adi_db_sync_task.sql_text IS 'Read-only SQL executed against the datasource | 对数据源执行的只读SQL';

CREATE INDEX IF NOT EXISTS idx_adi_db_task_user ON adi_db_sync_task (user_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_db_task_ds ON adi_db_sync_task (datasource_uuid, is_deleted);

DROP TRIGGER IF EXISTS trigger_adi_db_sync_task ON adi_db_sync_task;
CREATE TRIGGER trigger_adi_db_sync_task
    BEFORE UPDATE
    ON adi_db_sync_task
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ============================================================
-- 034: Knowledge base category
-- 知识库分类字典：admin 维护分类，KB 挂 category_id，便于按业务场景隔离管理
-- ============================================================
-- 设计要点：
--   1. category_id = 0 表示「未分类」，为虚拟组，不落字典表；老数据天然归入该组，向后兼容。
--   2. 分类仅影响「列表/选择器的组织与筛选」，不参与召回链路；
--      adi_agent.kb_ids / adi_character.kb_ids / 工作流节点 knowledge_base_uuid
--      均按 uuid 引用 KB，不受分类影响，无需迁移。
-- Idempotent: safe to run repeatedly

CREATE TABLE IF NOT EXISTS adi_kb_category
(
    id          bigserial primary key,
    name        varchar(64) default ''                not null,
    sort        int         default 0                 not null,
    remark      varchar(250) default ''               not null,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted  boolean     default false             not null
);
COMMENT ON TABLE adi_kb_category IS 'Knowledge base category dictionary | 知识库分类字典';
COMMENT ON COLUMN adi_kb_category.name IS 'Category name (unique among alive rows) | 分类名（未删除行内唯一）';
COMMENT ON COLUMN adi_kb_category.sort IS 'Display order, smaller first | 展示顺序，越小越靠前';
COMMENT ON COLUMN adi_kb_category.remark IS 'Remark | 备注';

-- 存活行内分类名唯一（软删行允许重名）
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_kb_category_name
    ON adi_kb_category (name) WHERE is_deleted = false;

DROP TRIGGER IF EXISTS trigger_adi_kb_category ON adi_kb_category;
CREATE TRIGGER trigger_adi_kb_category
    BEFORE UPDATE
    ON adi_kb_category
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- KB 挂分类：0 = 未分类
ALTER TABLE adi_knowledge_base ADD COLUMN IF NOT EXISTS category_id bigint default 0 not null;
COMMENT ON COLUMN adi_knowledge_base.category_id IS 'Category id, 0 = uncategorized | 分类id，0=未分类';

CREATE INDEX IF NOT EXISTS idx_adi_kb_category ON adi_knowledge_base (category_id) WHERE is_deleted = false;

-- ============================================================
-- Initial Data: seed data inserts
-- ============================================================

-- 管理员账号：catkeeper@aideepin.com  密码：123456
INSERT INTO adi_user (name, password, uuid, email, user_status, is_admin)
VALUES ('catkeeper', '$2a$10$z44gncmQk6xCBCeDx55gMe1Zc8uYtOKcoT4/HE2F92VcF7wP2iquG',
        replace(gen_random_uuid()::text, '-', ''), 'catkeeper@aideepin.com', 2, true);

-- 配置信息
-- https://api-docs.deepseek.com/zh-cn/
INSERT INTO adi_sys_config (name, value)
VALUES ('google_setting',
        '{"url":"https://www.googleapis.com/customsearch/v1","key":"","cx":""}');
INSERT INTO adi_sys_config (name, value)
VALUES ('request_text_rate_limit', '{"times":24,"minutes":3}');
INSERT INTO adi_sys_config (name, value)
VALUES ('request_image_rate_limit', '{"times":6,"minutes":3}');
INSERT INTO adi_sys_config (name, value)
VALUES ('character_max_num', '50');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_token_daily', '10000');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_token_monthly', '200000');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_request_daily', '150');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_request_monthly', '3000');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_image_daily', '30');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_image_monthly', '300');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_qa_ask_daily', '50');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_qa_item_monthly', '100');

-- 文件存储位置，默认存储到本地
-- store_location: 1表示存储到本地,2表示存储到阿里云oss
INSERT INTO adi_sys_config (name, value)
VALUES ('storage_location', '1');
-- endpoint: 如：oss-cn-hangzhou.aliyuncs.com
INSERT INTO adi_sys_config (name, value)
VALUES ('storage_location_ali_oss', '{"access_key_id":"","access_key_secret":"","endpoint":"","bucket_name":""}');

-- ASR settings
-- Supported platforms and models:
--   siliconflow  -> FunAudioLLM/SenseVoiceSmall
--   dashscope    -> paraformer-v2
--   openai       -> gpt-4o-mini-transcribe
-- Max file size: 10MB, max recording duration: 60s (TODO)
INSERT INTO adi_sys_config (name, value)
VALUES ('asr_setting',
        '{"model_name":"FunAudioLLM/SenseVoiceSmall","platform":"siliconflow","max_record_duration":60,"max_file_size":10485760}');

-- TTS settings
-- Supported platforms and models:
--   siliconflow  -> FunAudioLLM/CosyVoice2-0.5B
--   dashscope    -> cosyvoice-v3-flash
--   openai       -> gpt-4o-mini-tts
-- synthesizer_side: "client" uses browser TTS (free), "server" uses models (paid)
INSERT INTO adi_sys_config (name, value)
VALUES ('tts_setting', '{"synthesizer_side":"client","model_name":"","platform":""}');
-- Uncomment one of the following to enable server-side TTS with a specific model:
-- INSERT INTO adi_sys_config (name, value)
-- VALUES ('tts_setting', '{"synthesizer_side":"server","model_name":"cosyvoice-v3-flash","platform":"dashscope"}');
-- INSERT INTO adi_sys_config (name, value)
-- VALUES ('tts_setting', '{"synthesizer_side":"server","model_name":"gpt-4o-mini-tts","platform":"openai"}');
-- INSERT INTO adi_sys_config (name, value)
-- VALUES ('tts_setting', '{"synthesizer_side":"server","model_name":"FunAudioLLM/CosyVoice2-0.5B","platform":"siliconflow"}');

-- 模型平台
insert into adi_model_platform (name, title, base_url)
values ('openai', 'OpenAI', 'https://api.openai.com/v1');
insert into adi_model_platform (name, title, base_url)
values ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1');
insert into adi_model_platform (name, title, base_url)
values ('dashscope', 'DashScope', 'https://dashscope.aliyuncs.com/api/v1');
insert into adi_model_platform (name, title, base_url)
values ('siliconflow', 'SiliconFlow', 'https://api.siliconflow.cn/v1');
insert into adi_model_platform (name, title, base_url)
values ('ollama', 'ollama', 'http://localhost:11434');
-- SiliconFlow text models are compatible with OpenAI API; this row tests whether dynamically created platforms/models use OpenAiCompatibleLLMService correctly
insert into adi_model_platform (name, title, base_url, is_openai_api_compatible)
values ('openai-compatible-platform-test', 'OpenAI-Compatible Platform', 'https://api.siliconflow.cn/v1', true);
-- 火山方舟 ARK（二开新增）
-- 端点必须使用 Agent Plan 专属前缀 /api/plan/v3，而非 /api/v3：
--   聊天  POST /api/plan/v3/chat/completions -> 200
--   向量  POST /api/plan/v3/embeddings       -> 200（2048 维）
--   /api/v3 在聊天与向量上均返回 401/400。
-- api_key 不在初始化脚本中写入，请在管理端「模型平台」页面填写，避免密钥入库泄露。
insert into adi_model_platform (name, title, base_url, is_openai_api_compatible)
values ('volcengine', '火山方舟 ARK', 'https://ark.cn-beijing.volces.com/api/plan/v3', true);
-- 千问 MaaS 兼容平台（二开新增）
-- embedding 与 rerank 分处两个不同 base path：
--   embedding: https://maas.qianwenaiapi.com/compatible-mode/v1  (base_url 指向此处)
--   rerank:    https://maas.qianwenaiapi.com/compatible-api/v1/reranks  (路径复数，由 rerank_url 列指向)
-- api_key 不在初始化脚本中写入，请在管理端「模型平台」页面填写（或执行 029 迁移注入）。
-- rerank_url 列定义见 all_ddl.sql；OpenAiCompatibleScoringModel 已支持 rerank_url 优先于 base_url + /rerank。
insert into adi_model_platform (name, title, base_url, is_openai_api_compatible, rerank_url)
values ('qianwen', '千问 MaaS', 'https://maas.qianwenaiapi.com/compatible-mode/v1', true,
        'https://maas.qianwenaiapi.com/compatible-api/v1/reranks');

-- 火山方舟 — 大语言模型
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          input_types, properties, remark, is_enable)
VALUES ('doubao-seed-2.0-lite', '豆包 Seed 2.0 Lite', 'text', 'volcengine', 256000, 253952, 8192, 'text', '{}',
        '二开新增：字节豆包 Seed 2.0 均衡型，支持文本/图片/音频/视频全模态理解。方舟 Model Name 如需带版本号可改为 doubao-seed-2-0-lite-260428',
        true);
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          input_types, properties, remark, is_enable)
VALUES ('minimax-m3', 'MiniMax M3', 'text', 'volcengine', 200000, 198000, 8192, 'text', '{}',
        '二开新增：MiniMax M3，经火山方舟 OpenAI 兼容端点调用。方舟控制台显示名为 MiniMax-M3', true);

-- 大语言模型
-- https://api-docs.deepseek.com/zh-cn/quick_start/pricing
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, is_reasoner,
                          is_thinking_closable, is_enable)
VALUES ('deepseek-v4-flash', 'DeepSeek-V4-Flash', 'text', 'deepseek', 1000000, 980000, 384000, 'text,json_object', true, true,
        false);

-- https://developers.openai.com/api/docs/models/gpt-5-mini (superseded by gpt-5.4-mini)
-- INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
--                           response_format_types, remark,
--                           is_enable)
-- VALUES ('gpt-5-mini', 'gpt-5-mini', 'text', 'openai', 400000, 272000, 128000, 'text,json_object',
--         'GPT-5 mini is a faster, more cost-efficient version of GPT-5. It''s great for well-defined tasks and precise prompts.',
--         false);

-- https://developers.openai.com/api/docs/models/gpt-5.4-mini
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, remark,
                          is_enable)
VALUES ('gpt-5.4-mini', 'gpt-5.4-mini', 'text', 'openai', 400000, 272000, 128000, 'text,json_object',
        'GPT-5.4 mini brings the strengths of GPT-5.4 to a faster, more efficient model designed for high-volume workloads.',
        false);


INSERT INTO adi_ai_model (name, title, type, platform, properties, is_enable)
VALUES ('gpt-image-2', 'GPT-Image-2', 'image', 'openai', '{"max_images": 10}', false);
-- OpenAI 语音识别 (ASR)
INSERT INTO adi_ai_model (name, title, type, platform, input_types, is_enable)
VALUES ('gpt-4o-mini-transcribe', 'OpenAI-ASR', 'asr', 'openai', 'audio', false);
-- OpenAI 语音合成 (TTS)
-- gpt-4o-mini-tts supports all 13 voices
INSERT INTO adi_ai_model (name, title, type, platform, input_types, properties, is_enable)
VALUES ('gpt-4o-mini-tts', 'OpenAI-TTS', 'tts', 'openai', 'text', '{
  "voices": [
    {"name": "Alloy", "remark": "Alloy", "param_name": "alloy", "lang": "multilingual"},
    {"name": "Ash", "remark": "Ash", "param_name": "ash", "lang": "multilingual"},
    {"name": "Ballad", "remark": "Ballad", "param_name": "ballad", "lang": "multilingual"},
    {"name": "Coral", "remark": "Coral", "param_name": "coral", "lang": "multilingual"},
    {"name": "Echo", "remark": "Echo", "param_name": "echo", "lang": "multilingual"},
    {"name": "Fable", "remark": "Fable", "param_name": "fable", "lang": "multilingual"},
    {"name": "Nova", "remark": "Nova", "param_name": "nova", "lang": "multilingual"},
    {"name": "Onyx", "remark": "Onyx", "param_name": "onyx", "lang": "multilingual"},
    {"name": "Sage", "remark": "Sage", "param_name": "sage", "lang": "multilingual"},
    {"name": "Shimmer", "remark": "Shimmer", "param_name": "shimmer", "lang": "multilingual"},
    {"name": "Verse", "remark": "Verse", "param_name": "verse", "lang": "multilingual"},
    {"name": "Marin", "remark": "Marin", "param_name": "marin", "lang": "multilingual"},
    {"name": "Cedar", "remark": "Cedar", "param_name": "cedar", "lang": "multilingual"}
  ]
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-3-small', 'OpenAI-Embedding-Small', 'embedding', 'openai', 8191, '{
  "dimension": 1536
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-3-large', 'OpenAI-Embedding-Large', 'embedding', 'openai', 8191, '{
  "dimension": 3072
}', false);
-- https://help.aliyun.com/zh/dashscope/developer-reference/model-introduction?spm=a2c4g.11186623.0.i39
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, is_support_web_search, is_reasoner,
                          is_thinking_closable, is_enable)
VALUES ('qwen-turbo', 'Qwen-Turbo', 'text', 'dashscope', 131072, 98304, 16384, 'text,json_object', true, true, true,
        false);
-- 图片识别
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens, input_types,
                          is_enable)
VALUES ('qwen2-vl-7b-instruct', 'Qwen-Vision', 'vision', 'dashscope', 32768, 16384, 16384, 'text,image', false);
-- 通义万相-文生图 wan2.7-image
INSERT INTO adi_ai_model (name, title, type, platform, properties, is_enable)
VALUES ('wan2.7-image', 'Qwen-Image', 'image', 'dashscope', '{"max_images": 4, "sizes": [{"value":"1K","label":"1K · 1024×1024"},{"value":"2K","label":"2K · 2048×2048"}]}', false);
-- 通义万相-切换背景
INSERT INTO adi_ai_model (name, title, type, platform, input_types, is_enable)
VALUES ('wanx-background-generation-v2', 'Qwen-Background', 'image', 'dashscope', 'text,image', false);
-- 通义千问-向量（可选模型名：text-embedding-v1,text-embedding-v2,text-embedding-v3）
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-v1', 'Qwen-Embedding-V1', 'embedding', 'dashscope', 2048, '{
  "dimension": 1536
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-v3', 'Qwen-Embedding-V3', 'embedding', 'dashscope', 8192, '{
  "dimension": 1024
}', false);
-- 火山方舟 — 向量模型（adi.embedding-model 默认值，必须保持启用）
-- 注意事项：
--   * properties.dimension 必须与 application-*.yml 中 adi.embedding-model 指向模型的实际输出维度一致，
--     否则 pgvector 写入会因维度不符报错。
--   * 模型名必须使用无后缀别名 doubao-embedding-vision；带版本后缀的
--     doubao-embedding-vision-250615 在该账号下方舟返回 400（InvalidParameter）。
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, remark, is_enable)
VALUES ('doubao-embedding-vision', '豆包向量化 Vision', 'embedding', 'volcengine', 128000, '{
  "dimension": 2048
}', '二开新增：豆包多模态向量化，默认 2048 维。降维请改 properties.dimension 并同步清空重建向量库', true);
-- 千问 MaaS — 向量模型（用户提到的 qwen3-vl-embedding 不在该平台模型列表，实际可用为 qwen3.7-text-embedding，实测 1024 维）
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, remark, is_enable)
VALUES ('qwen3.7-text-embedding', '千问向量化', 'embedding', 'qianwen', 8192, '{
  "dimension": 1024
}', '二开新增：千问 MaaS 文本向量化，输出 1024 维（实测 /embeddings 返回 1024 维）', true);
-- 千问 MaaS — 重排序模型（端点 /compatible-api/v1/reranks 复数，由平台 rerank_url 指向）
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, remark, is_enable)
VALUES ('qwen3.7-text-rerank', '千问重排序', 'rerank', 'qianwen', 30000, '{}',
        '二开新增：千问 MaaS 重排序，单文档最大 30000 token；端点 /compatible-api/v1/reranks（复数）', true);
-- 语音识别
-- paraformer-v2 只支持公网可访问的音频文件，如需要传输本地音频文件，请激活使用下面硅基流动的SenseVoiceSmall
INSERT INTO adi_ai_model (name, title, type, platform, input_types, is_enable)
VALUES ('paraformer-v2', 'Qwen-ASR', 'asr', 'dashscope', 'audio', false);
-- 语音合成
INSERT INTO adi_ai_model (name, title, type, platform, input_types, properties, is_enable)
VALUES ('cosyvoice-v3-flash', 'Qwen-TTS', 'tts', 'dashscope', 'text', '{
  "voices": [
    {
      "name": "龙安洋",
      "remark": "阳光大男孩",
      "param_name": "longanyang",
      "lang": "中、英"
    },
    {
      "name": "龙安欢",
      "remark": "欢脱元气女",
      "param_name": "longanhuan",
      "lang": "中、英"
    },
    {
      "name": "龙呼呼",
      "remark": "天真烂漫女童",
      "param_name": "longhuhu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙泡泡",
      "remark": "飞天泡泡音",
      "param_name": "longpaopao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙杰力豆",
      "remark": "阳光顽皮男",
      "param_name": "longjielidou_v3",
      "lang": "中、英"
    },
    {
      "name": "龙仙",
      "remark": "豪放可爱女",
      "param_name": "longxian_v3",
      "lang": "中、英"
    },
    {
      "name": "龙铃",
      "remark": "稚气呆板女",
      "param_name": "longling_v3",
      "lang": "中、英"
    },
    {
      "name": "龙闪闪",
      "remark": "戏剧化童声",
      "param_name": "longshanshan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙牛牛",
      "remark": "阳光男童声",
      "param_name": "longniuniu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙嘉欣",
      "remark": "优雅粤语女",
      "param_name": "longjiaxin_v3",
      "lang": "中（粤语）、英"
    },
    {
      "name": "龙嘉怡",
      "remark": "知性粤语女",
      "param_name": "longjiayi_v3",
      "lang": "中（粤语）、英"
    },
    {
      "name": "龙安粤",
      "remark": "欢脱粤语男",
      "param_name": "longanyue_v3",
      "lang": "中（粤语）、英"
    },
    {
      "name": "龙老铁",
      "remark": "东北直率男",
      "param_name": "longlaotie_v3",
      "lang": "中（东北话）、英"
    },
    {
      "name": "龙陕哥",
      "remark": "原味陕北男",
      "param_name": "longshange_v3",
      "lang": "中（陕西话）、英"
    },
    {
      "name": "龙安闽",
      "remark": "清纯萝莉女",
      "param_name": "longanmin_v3",
      "lang": "中（闽南话）、英"
    },
    {
      "name": "龙飞",
      "remark": "热血磁性男",
      "param_name": "longfei_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应笑",
      "remark": "清甜推销女",
      "param_name": "longyingxiao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应询",
      "remark": "年轻青涩男",
      "param_name": "longyingxun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应静",
      "remark": "低调冷静女",
      "param_name": "longyingjing_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应聆",
      "remark": "温和共情女",
      "param_name": "longyingling_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应桃",
      "remark": "温柔淡定女",
      "param_name": "longyingtao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙小淳",
      "remark": "知性积极女",
      "param_name": "longxiaochun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙小夏",
      "remark": "沉稳权威女",
      "param_name": "longxiaoxia_v3",
      "lang": "中、英"
    },
    {
      "name": "YUMI",
      "remark": "正经青年女",
      "param_name": "longyumi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安昀",
      "remark": "居家暖男",
      "param_name": "longanyun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安温",
      "remark": "优雅知性女",
      "param_name": "longanwen_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安莉",
      "remark": "利落从容女",
      "param_name": "longanli_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安朗",
      "remark": "清爽利落男",
      "param_name": "longanlang_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应沐",
      "remark": "优雅知性女",
      "param_name": "longyingmu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安台",
      "remark": "嗲甜台湾女",
      "param_name": "longantai_v3",
      "lang": "中、英"
    },
    {
      "name": "龙华",
      "remark": "元气甜美女",
      "param_name": "longhua_v3",
      "lang": "中、英"
    },
    {
      "name": "龙橙",
      "remark": "智慧青年男",
      "param_name": "longcheng_v3",
      "lang": "中、英"
    },
    {
      "name": "龙泽",
      "remark": "温暖元气男",
      "param_name": "longze_v3",
      "lang": "中、英"
    },
    {
      "name": "龙哲",
      "remark": "呆板大暖男",
      "param_name": "longzhe_v3",
      "lang": "中、英"
    },
    {
      "name": "龙颜",
      "remark": "温暖春风女",
      "param_name": "longyan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙星",
      "remark": "温婉邻家女",
      "param_name": "longxing_v3",
      "lang": "中、英"
    },
    {
      "name": "龙天",
      "remark": "磁性理智男",
      "param_name": "longtian_v3",
      "lang": "中、英"
    },
    {
      "name": "龙婉",
      "remark": "细腻柔声女",
      "param_name": "longwan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙嫱",
      "remark": "浪漫风情女",
      "param_name": "longqiang_v3",
      "lang": "中、英"
    },
    {
      "name": "龙菲菲",
      "remark": "甜美娇气女",
      "param_name": "longfeifei_v3",
      "lang": "中、英"
    },
    {
      "name": "龙浩",
      "remark": "多情忧郁男",
      "param_name": "longhao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安柔",
      "remark": "温柔闺蜜女",
      "param_name": "longanrou_v3",
      "lang": "中、英"
    },
    {
      "name": "龙寒",
      "remark": "温暖痴情男",
      "param_name": "longhan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安智",
      "remark": "睿智轻熟男",
      "param_name": "longanzhi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安灵",
      "remark": "思维灵动女",
      "param_name": "longanling_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安雅",
      "remark": "高雅气质女",
      "param_name": "longanya_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安亲",
      "remark": "亲和活泼女",
      "param_name": "longanqin_v3",
      "lang": "中、英"
    },
    {
      "name": "龙妙",
      "remark": "抑扬顿挫女",
      "param_name": "longmiao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙三叔",
      "remark": "沉稳质感男",
      "param_name": "longsanshu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙媛",
      "remark": "温暖治愈女",
      "param_name": "longyuan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙悦",
      "remark": "温暖磁性女",
      "param_name": "longyue_v3",
      "lang": "中、英"
    },
    {
      "name": "龙修",
      "remark": "博才说书男",
      "param_name": "longxiu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙楠",
      "remark": "睿智青年男",
      "param_name": "longnan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙婉君",
      "remark": "细腻柔声女",
      "param_name": "longwanjun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙逸尘",
      "remark": "洒脱活力男",
      "param_name": "longyichen_v3",
      "lang": "中、英"
    },
    {
      "name": "龙老伯",
      "remark": "沧桑岁月爷",
      "param_name": "longlaobo_v3",
      "lang": "中、英"
    },
    {
      "name": "龙老姨",
      "remark": "烟火从容阿姨",
      "param_name": "longlaoyi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙机器",
      "remark": "呆萌机器人",
      "param_name": "longjiqi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙猴哥",
      "remark": "经典猴哥",
      "param_name": "longhouge_v3",
      "lang": "中、英"
    },
    {
      "name": "龙黛玉",
      "remark": "娇率才女音",
      "param_name": "longdaiyu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安燃",
      "remark": "活泼质感女",
      "param_name": "longanran_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安宣",
      "remark": "经典直播女",
      "param_name": "longanxuan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙硕",
      "remark": "博才干练男",
      "param_name": "longshuo_v3",
      "lang": "中、英"
    },
    {
      "name": "龙书",
      "remark": "沉稳青年男",
      "param_name": "longshu_v3",
      "lang": "中、英"
    },
    {
      "name": "Bella3.0",
      "remark": "精准干练女",
      "param_name": "loongbella_v3",
      "lang": "中、英"
    }
  ]
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, is_enable)
VALUES ('qwen3:8b', 'Qwen3-8B(Ollama)', 'text', 'ollama', 131072, 98304, 16384, 'text,json_object', false);
INSERT INTO adi_ai_model (name, title, type, platform, response_format_types, remark, is_free, is_enable)
VALUES ('THUDM/GLM-Z1-9B-0414', 'SiliconFlow-GLM-Z1-9B', 'text', 'siliconflow', 'text,json_object',
        'GLM-Z1-9B-0414 是 GLM 系列的小型模型，仅有 90 亿参数，但保持了开源传统的同时展现出惊人的能力。', true, false);
INSERT INTO adi_ai_model (name, title, type, platform, input_types, remark, is_free, is_enable)
VALUES ('THUDM/GLM-4.1V-9B-Thinking', 'SiliconFlow-Vision', 'vision', 'siliconflow', 'text,image',
        'GLM-4.1V-9B-Thinking 是由智谱 AI 和清华大学 KEG 实验室联合发布的一款开源视觉语言模型（VLM），专为处理复杂的多模态认知任务而设计。该模型基于 GLM-4-9B-0414 基础模型，通过引入“思维链”（Chain-of-Thought）推理机制和采用强化学习策略，显著提升了其跨模态的推理能力和稳定性。作为一个 9B 参数规模的轻量级模型，它在部署效率和性能之间取得了平衡，在 28 项权威评测基准中，有 18 项的表现持平甚至超越了 72B 参数规模的 Qwen-2.5-VL-72B。该模型不仅在图文理解、数学科学推理、视频理解等任务上表现卓越，还支持高达 4K 分辨率的图像和任意宽高比输入',
        true, false);

INSERT INTO adi_ai_model (name, title, type, platform, properties, remark, is_free, is_enable)
VALUES ('Kwai-Kolors/Kolors', 'SiliconFlow-Image', 'image', 'siliconflow', '{
  "image_sizes": [
    "1024x1024",
    "960x1280",
    "768x1024",
    "720x1440",
    "720x1280"
  ],
  "max_images": 4
}',
        'Kolors 是由快手 Kolors 团队开发的基于潜在扩散的大规模文本到图像生成模型。该模型通过数十亿文本-图像对的训练，在视觉质量、复杂语义准确性以及中英文字符渲染方面展现出显著优势。它不仅支持中英文输入，在理解和生成中文特定内容方面也表现出色。',
        true, false);

-- Qwen/Qwen-Image 为收费模型，详细评估后再判断是否要启用
-- INSERT INTO adi_ai_model (name, title, type, platform, properties, remark, is_free, is_enable)
-- VALUES ('Qwen/Qwen-Image', '硅基流动-文生图（qwen）', 'image', 'siliconflow', '{
--   "image_size": [
--     "1328x1328",
--     "1664x928",
--     "928x1664",
--     "1472x1140",
--     "1140x1472",
--     "1584x1056",
--     "1056x1584"
--   ]
-- }',
--         'Qwen-Image 是由阿里巴巴通义千问团队发布的图像生成基础模型，拥有 200 亿参数。该模型在复杂的文本渲染和精确的图像编辑方面取得了显著进展，尤其擅长生成包含高保真度中英文文字的图像。', false, false);

-- test data
INSERT INTO adi_ai_model (name, title, type, platform, response_format_types, is_enable)
VALUES ('THUDM/GLM-Z1-9B-0414', 'openai-compatible-model-test', 'text', 'openai-compatible-platform-test',
        'text,json_object', false);
-- test data end


-- 语音识别
INSERT INTO adi_ai_model (name, title, type, platform, input_types, response_format_types, is_free, is_enable)
VALUES ('FunAudioLLM/SenseVoiceSmall', 'SiliconFlow-ASR', 'asr', 'siliconflow', 'audio', 'text,json_object', true,
        true);
-- 语音合成
INSERT INTO adi_ai_model (name, title, type, platform, input_types, properties, is_enable)
values ('FunAudioLLM/CosyVoice2-0.5B', 'SiliconFlow-TTS', 'tts', 'siliconflow', 'text', '{
  "voices": [
    {
      "name": "fnlp/MOSS-TTSD-v0.5:alex"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:anna"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:bella"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:benjamin"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:charles"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:claire"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:david"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:diana"
    }
  ]
}', true);
