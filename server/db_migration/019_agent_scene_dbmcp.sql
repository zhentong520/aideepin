-- ============================================================
-- 019: Agent / Scene / Database MCP / Workflow Database Node
-- 二次开发扩展：智能体、场景、外部数据库数据源（数据库 MCP）、工作流数据库节点
-- Extension: Agent, Scene, external DB datasource (Database MCP), workflow Database node
-- Idempotent: safe to run repeatedly
-- ============================================================

-- ------------------------------------------------------------
-- 1. Agent 智能体
--    Agent with work design (system_message + work_design JSON),
--    bound knowledge bases / MCP / model, and runtime character.
-- ------------------------------------------------------------
create table if not exists adi_agent
(
    id               bigserial primary key,
    uuid             varchar(32)  default ''                not null,
    user_id          bigint       default 0                 not null,
    title            varchar(100) default ''                not null,
    remark           text         default ''                not null,
    avatar_url       varchar(500) default ''                not null,
    system_message   text         default ''                not null,
    work_design      text         default ''                not null,
    model_platform   varchar(50)  default ''                not null,
    model_name       varchar(100) default ''                not null,
    llm_temperature  numeric(3, 2) default 0.70             not null,
    kb_ids           varchar(500) default ''                not null,
    mcp_ids          varchar(500) default ''                not null,
    enable_rag       boolean      default true              not null,
    enable_mcp       boolean      default false             not null,
    enable_web_search boolean     default false             not null,
    character_uuid   varchar(32)  default ''                not null,
    sort             int          default 0                 not null,
    is_enable        boolean      default true              not null,
    create_time      timestamp    default CURRENT_TIMESTAMP not null,
    update_time      timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted       boolean      default false             not null
);
comment on table adi_agent is 'Agent definition with work design | 智能体定义（含工作设计）';
comment on column adi_agent.system_message is 'System prompt / role setting | 角色设定（系统提示词）';
comment on column adi_agent.work_design is 'Work design JSON: goal, steps, constraints, output format | 工作设计JSON';
comment on column adi_agent.character_uuid is 'Runtime character uuid used by chat | 对话运行时使用的角色uuid';

create index if not exists idx_adi_agent_user on adi_agent (user_id, is_deleted);
create index if not exists idx_adi_agent_uuid on adi_agent (uuid);

drop trigger if exists trigger_adi_agent on adi_agent;
create trigger trigger_adi_agent
    before update
    on adi_agent
    for each row
execute procedure update_modified_column();

-- ------------------------------------------------------------
-- 2. Agent 与工作流的绑定关系（多对多）
--    Agent <-> Workflow binding (many-to-many)
-- ------------------------------------------------------------
create table if not exists adi_agent_workflow
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
comment on table adi_agent_workflow is 'Agent to workflow binding | 智能体与工作流绑定';

create index if not exists idx_adi_agent_wf_agent on adi_agent_workflow (agent_uuid, is_deleted);
create index if not exists idx_adi_agent_wf_wf on adi_agent_workflow (workflow_uuid, is_deleted);

drop trigger if exists trigger_adi_agent_workflow on adi_agent_workflow;
create trigger trigger_adi_agent_workflow
    before update
    on adi_agent_workflow
    for each row
execute procedure update_modified_column();

-- ------------------------------------------------------------
-- 3. Scene 场景（可绑定多个 Agent）
--    Scene binding multiple agents
-- ------------------------------------------------------------
create table if not exists adi_scene
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
comment on table adi_scene is 'Chat scene binding multiple agents | 对话场景（绑定多个智能体）';

create index if not exists idx_adi_scene_user on adi_scene (user_id, is_deleted);
create index if not exists idx_adi_scene_uuid on adi_scene (uuid);

drop trigger if exists trigger_adi_scene on adi_scene;
create trigger trigger_adi_scene
    before update
    on adi_scene
    for each row
execute procedure update_modified_column();

-- ------------------------------------------------------------
-- 4. Scene 与 Agent 的绑定关系（多对多）
--    Scene <-> Agent binding (many-to-many)
-- ------------------------------------------------------------
create table if not exists adi_scene_agent
(
    id          bigserial primary key,
    uuid        varchar(32) default ''                not null,
    scene_uuid  varchar(32) default ''                not null,
    agent_uuid  varchar(32) default ''                not null,
    sort        int         default 0                 not null,
    create_time timestamp   default CURRENT_TIMESTAMP not null,
    update_time timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted  boolean     default false             not null
);
comment on table adi_scene_agent is 'Scene to agent binding | 场景与智能体绑定';

create index if not exists idx_adi_scene_agent_scene on adi_scene_agent (scene_uuid, is_deleted);
create index if not exists idx_adi_scene_agent_agent on adi_scene_agent (agent_uuid, is_deleted);

drop trigger if exists trigger_adi_scene_agent on adi_scene_agent;
create trigger trigger_adi_scene_agent
    before update
    on adi_scene_agent
    for each row
execute procedure update_modified_column();

-- ------------------------------------------------------------
-- 5. 外部数据库数据源（Database MCP）
--    External database datasource — read schema/data as knowledge
-- ------------------------------------------------------------
create table if not exists adi_db_datasource
(
    id           bigserial primary key,
    uuid         varchar(32)  default ''                not null,
    user_id      bigint       default 0                 not null,
    title        varchar(100) default ''                not null,
    db_type      varchar(20)  default 'postgresql'      not null,
    host         varchar(200) default ''                not null,
    port         int          default 5432              not null,
    db_name      varchar(100) default ''                not null,
    username     varchar(100) default ''                not null,
    password     varchar(300) default ''                not null,
    jdbc_params  varchar(500) default ''                not null,
    remark       text         default ''                not null,
    is_enable    boolean      default true              not null,
    create_time  timestamp    default CURRENT_TIMESTAMP not null,
    update_time  timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted   boolean      default false             not null
);
comment on table adi_db_datasource is 'External database datasource (Database MCP) | 外部数据库数据源';
-- Only postgresql/mysql drivers are bundled; sqlserver/oracle are rejected by the service layer.
comment on column adi_db_datasource.db_type is 'postgresql / mysql (others rejected by service) | 数据库类型（仅支持pg/mysql）';

create index if not exists idx_adi_db_ds_user on adi_db_datasource (user_id, is_deleted);
create index if not exists idx_adi_db_ds_uuid on adi_db_datasource (uuid);

drop trigger if exists trigger_adi_db_datasource on adi_db_datasource;
create trigger trigger_adi_db_datasource
    before update
    on adi_db_datasource
    for each row
execute procedure update_modified_column();

-- ------------------------------------------------------------
-- 6. 数据库 -> 知识库 同步任务
--    Datasource -> Knowledge Base sync task
-- ------------------------------------------------------------
create table if not exists adi_db_sync_task
(
    id              bigserial primary key,
    uuid            varchar(32)  default ''                not null,
    user_id         bigint       default 0                 not null,
    datasource_uuid varchar(32)  default ''                not null,
    kb_uuid         varchar(32)  default ''                not null,
    title           varchar(100) default ''                not null,
    sql_text        text         default ''                not null,
    content_template text        default ''                not null,
    last_sync_time  timestamp    default null,
    last_status     varchar(20)  default ''                not null,
    last_rows       int          default 0                 not null,
    last_message    text         default ''                not null,
    is_enable       boolean      default true              not null,
    create_time     timestamp    default CURRENT_TIMESTAMP not null,
    update_time     timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted      boolean      default false             not null
);
comment on table adi_db_sync_task is 'Sync external DB query result into knowledge base | 数据库内容同步至知识库的任务';
comment on column adi_db_sync_task.sql_text is 'Read-only SQL executed against the datasource | 对数据源执行的只读SQL';

create index if not exists idx_adi_db_task_user on adi_db_sync_task (user_id, is_deleted);
create index if not exists idx_adi_db_task_ds on adi_db_sync_task (datasource_uuid, is_deleted);

drop trigger if exists trigger_adi_db_sync_task on adi_db_sync_task;
create trigger trigger_adi_db_sync_task
    before update
    on adi_db_sync_task
    for each row
execute procedure update_modified_column();

-- ------------------------------------------------------------
-- 7. 工作流：注册 Database 节点组件
--    Register Database node as a workflow component
-- ------------------------------------------------------------
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Database', 'Database',
       'Execute read-only SQL on an external datasource and output rows as text/JSON',
       4, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Database');
