-- 018_kb_audit_share.sql
-- 知识库细粒度权限 + 操作审计（二开新增，对标 MaxKB / AnythingLLM 的细粒度权限与审计）
--
-- 依赖：017_rag_hybrid_rerank.sql 之后执行
-- 说明：
--   1) adi_knowledge_base_audit_log：文档/知识库写操作的留痕（操作人、对象、成败、详情）。
--   2) adi_knowledge_base_share：在 owner/public/admin 三态之上，按用户显式授权，
--      支撑「前端按授权看知识库」。

-- ========== 1. 审计日志表 ==========
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

-- ========== 2. 知识库共享表 ==========
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
