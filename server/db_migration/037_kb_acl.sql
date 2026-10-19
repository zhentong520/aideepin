-- ============================================================
-- V5__kb_acl.sql（二开 T1-03，2026-10-16）
-- 库级 ACL 字段：默认可见组（jsonb 数组）+ 默认密级（三档 PUBLIC/ORG/PRIVATE）。
-- 密级预留扩展（新增档位只需扩 CHECK 约束），不留死位。
-- chunk 级固化见 T1-04，存量回填见 T1-06（V6__acl_backfill.sql）。
-- Idempotent: safe to run repeatedly
-- ============================================================

alter table adi_knowledge_base add column if not exists default_visibility_groups jsonb default '[]' not null;
comment on column adi_knowledge_base.default_visibility_groups is 'Default visibility groups (jsonb array of group ids; synthetic group owner:<uuid> always included) | 默认可见组（jsonb数组，恒含合成组 owner:<owner_uuid>）';

alter table adi_knowledge_base add column if not exists default_sensitivity varchar(16) default 'PRIVATE' not null;
comment on column adi_knowledge_base.default_sensitivity is 'Default sensitivity: PUBLIC / ORG / PRIVATE (extensible) | 默认密级三档，可扩展';

-- CHECK 约束无 IF NOT EXISTS，用 DO 块保证幂等
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_adi_kb_default_sensitivity') THEN
        ALTER TABLE adi_knowledge_base
            ADD CONSTRAINT ck_adi_kb_default_sensitivity
            CHECK (default_sensitivity IN ('PUBLIC', 'ORG', 'PRIVATE'));
    END IF;
END $$;
