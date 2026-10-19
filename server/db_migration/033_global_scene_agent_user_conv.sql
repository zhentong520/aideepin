-- ============================================================
-- 033: Global scenes & agents + per-user conversation instances
-- 场景/智能体全局共享（后台统一配置），但每个用户拥有自己的会话实例
-- ============================================================
-- 设计要点：
--   1. 场景与智能体由管理员在后台配置，对所有用户可见（is_enable 控制是否下发）。
--   2. 用户不能共用智能体的 characterUuid：多处代码按 uuid 单条查询
--      （CharacterService.getOrThrow / createByFirstMessage、CharacterChatService 的
--      checkCharacter / blockingAsk），且短期记忆 MapDBChatMemoryStore 以 characterUuid
--      为键。共用会导致「多行 oneOpt 抛异常」+「跨用户串对话内容」。
--      因此每个用户按 (user_id, agent_uuid) 落一份自己的会话实例。
-- Idempotent: safe to run repeatedly

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

-- ------------------------------------------------------------
-- 场景/智能体改为全局共享
-- 原来 listMine() 按 user_id 过滤，导致「每个用户都得自己建一遍场景」。
-- 现在统一按 is_enable 下发给所有用户；user_id 仅保留为「创建者」溯源信息。
--
-- 存量数据兜底：把当前仍在使用的场景/智能体的 user_id 统一改成一个稳定值，
-- 避免 is_enable=true 的旧数据因创建者离职/换号而无人维护。
-- ------------------------------------------------------------
update adi_scene set user_id = 0 where user_id <> 0;
update adi_agent set user_id = 0 where user_id <> 0;

-- 补一个明确的索引：用户端只读「启用的」场景，按 sort 排序
create index if not exists idx_adi_scene_enable on adi_scene (is_enable, sort) where is_deleted = false;
create index if not exists idx_adi_agent_enable on adi_agent (is_enable, sort) where is_deleted = false;
