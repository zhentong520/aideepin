-- ============================================================
-- V2: Backfill missing schema (018 / 032 / 033)
-- 修复 DB-01：V1 baseline 复制的 all_ddl.sql 漏了三个迁移的 DDL，
-- 导致全新库（如 dev-compose 的 aideepin-dev-postgres）缺 3 表 1 列：
--   - adi_knowledge_base_audit_log / adi_knowledge_base_share   (来源 018)
--   - adi_document.raw_text                                     (来源 032)
--   - adi_user_agent_conv                                       (来源 033)
--
-- 全部语句幂等（IF NOT EXISTS / WHERE 守卫），对已手工执行过
-- 018/032/033 的存量库（如 adi-dev-postgres）重复执行无副作用。
-- ⚠️ 不要修改 V1__baseline.sql —— 它已在已建库上执行，checksum 已固化；
--    此后所有 DDL 变更一律新增 V{n} 文件，并同步 all_ddl.sql（铁律 #5）。
-- ============================================================

-- ========== 来源 018：知识库审计日志 + 共享表 ==========
create table if not exists adi_knowledge_base_audit_log (
    id            bigserial primary key,
    kb_uuid       varchar(64)   default ''    not null,
    kb_name       varchar(255)  default ''    not null,
    action        varchar(64)   default ''    not null,
    target_type   varchar(32)   default ''    not null,
    target_uuid   varchar(64)   default ''    not null,
    operator_id   bigint,
    operator_name varchar(128)  default ''    not null,
    success       boolean       default true  not null,
    detail        text          default ''    not null,
    create_time   timestamp    default now() not null,
    update_time   timestamp    default now() not null,
    is_deleted    boolean       default false not null
);
create index if not exists idx_audit_kb on adi_knowledge_base_audit_log (kb_uuid, is_deleted);

create table if not exists adi_knowledge_base_share (
    id           bigserial primary key,
    kb_uuid      varchar(64)  default ''    not null,
    target_type  varchar(16)  default 'USER' not null,
    target_id    bigint       not null,
    permission   varchar(16)  default 'READ' not null,
    grantor_id   bigint,
    grantor_name varchar(128) default ''    not null,
    create_time  timestamp    default now() not null,
    update_time  timestamp    default now() not null,
    is_deleted   boolean      default false not null,
    unique (kb_uuid, target_type, target_id, is_deleted)
);
create index if not exists idx_share_user on adi_knowledge_base_share (target_id, is_deleted);
create index if not exists idx_share_kb on adi_knowledge_base_share (kb_uuid, is_deleted);

-- ========== 来源 032：adi_document.raw_text ==========
alter table adi_document add column if not exists raw_text text;

comment on column adi_document.raw_text is '解析原始文本(清洗前), null 表示迁移前的老文档 | Raw parsed text before cleanup; null for documents ingested before this column';

-- ========== 来源 033：用户级智能体会话实例 ==========
create table if not exists adi_user_agent_conv
(
    id             bigserial primary key,
    uuid           varchar(32) default ''                not null,
    user_id        bigint      default 0                 not null,
    agent_uuid     varchar(32) default ''                not null,
    character_uuid varchar(32) default ''                not null,
    create_time    timestamp   default CURRENT_TIMESTAMP not null,
    update_time    timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted     boolean     default false             not null
);
comment on table adi_user_agent_conv is 'Per-user conversation instance bound to an agent | 用户基于智能体的会话实例';
comment on column adi_user_agent_conv.agent_uuid is 'The globally shared agent | 全局共享的智能体uuid';
comment on column adi_user_agent_conv.character_uuid is 'Character uuid owned by this user | 归属该用户的会话uuid';

create index if not exists idx_adi_uac_user on adi_user_agent_conv (user_id, is_deleted);
create index if not exists idx_adi_uac_agent on adi_user_agent_conv (agent_uuid, is_deleted);

-- 同一用户对同一智能体只保留一个有效会话实例
create unique index if not exists uk_adi_user_agent_conv
    ON adi_user_agent_conv (user_id, agent_uuid)
    WHERE is_deleted = false;

drop trigger if exists trigger_adi_user_agent_conv on adi_user_agent_conv;
create trigger trigger_adi_user_agent_conv
    before update
    on adi_user_agent_conv
    for each row
execute procedure update_modified_column();

-- 场景/智能体全局共享（033 的存量数据兜底，幂等：跑过后 user_id 均为 0）
update adi_scene set user_id = 0 where user_id <> 0;
update adi_agent set user_id = 0 where user_id <> 0;

create index if not exists idx_adi_scene_enable on adi_scene (is_enable, sort) where is_deleted = false;
create index if not exists idx_adi_agent_enable on adi_agent (is_enable, sort) where is_deleted = false;
