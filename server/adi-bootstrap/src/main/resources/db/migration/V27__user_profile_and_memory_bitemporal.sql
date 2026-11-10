-- ============================================================
-- V27__user_profile_and_memory_bitemporal.sql（二开 P0 批，2026-11-03）
-- 一个迁移覆盖「记忆/画像优化 P0」两处 DDL（对标调研：Memobase / Zep）：
--
-- 【P0-1】结构化用户画像层（Memobase 风格）：
--   adi_user_profile          每用户一份 JSON 画像（职业/偏好/语言等维度），
--                             长期记忆抽取轮同步合并更新；聊天注入时纯 SQL 读取，
--                             零 embedding、零向量检索（<100ms）。
--   adi_user_profile_event    用户事件时间线（append-only + bi-temporal），
--                             支持「用户以前做什么」类时序问题。
--
-- 【P0-2】语义记忆 bi-temporal 化（Zep 风格）：
--   向量表 metadata 新增 memory_valid / invalid_at 两个键（动态分表，
--   用 DO $$ 遍历 pg_tables 加 GIN 已有索引即可——metadata 键无需 DDL，
--   本迁移只做存量行回填 memory_valid='true'，让新旧数据过滤语义一致）。
--   ⚠️ 只 UPDATE metadata，绝不触碰向量列（不重跑 embedding）。
--   ⚠️ 幂等：WHERE 只更新缺 memory_valid 的行，复跑第二次影响 0 行。
-- Idempotent: safe to run repeatedly
-- ============================================================

-- ---------- P0-1: 结构化用户画像（当前态，每用户一行） ----------
CREATE TABLE IF NOT EXISTS adi_user_profile
(
    id              bigserial primary key,
    uuid            varchar(32)  DEFAULT ''  NOT NULL,
    user_id         bigint                   NOT NULL,
    profile         jsonb        DEFAULT '{}'::jsonb NOT NULL,
    profile_version integer      DEFAULT 0   NOT NULL,
    create_time     timestamp    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time     timestamp    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_deleted      boolean      DEFAULT false NOT NULL
);

COMMENT ON TABLE adi_user_profile IS 'Structured per-user profile (occupation/preferences/language dims), merged during memory extraction, read by pure SQL at chat time | 结构化用户画像：抽取轮 LLM 合并更新，聊天注入纯 SQL 读取零 embedding';
COMMENT ON COLUMN adi_user_profile.uuid IS 'Row unique uuid | 行唯一uuid';
COMMENT ON COLUMN adi_user_profile.user_id IS 'Owner user id (unique) | 归属用户id（唯一）';
COMMENT ON COLUMN adi_user_profile.profile IS 'Profile dimensions as JSON object, e.g. {"occupation":"developer","language":"zh-CN"} | 画像维度 JSON 对象（维度名→值）';
COMMENT ON COLUMN adi_user_profile.profile_version IS 'Monotonic merge counter for audit/optimistic display | 合并版本号（单调递增，审计用）';

CREATE UNIQUE INDEX IF NOT EXISTS uidx_user_profile_user_id ON adi_user_profile (user_id) WHERE is_deleted = false;

-- ---------- P0-1: 用户事件时间线（bi-temporal，append-only） ----------
CREATE TABLE IF NOT EXISTS adi_user_profile_event
(
    id            bigserial primary key,
    uuid          varchar(32)  DEFAULT ''  NOT NULL,
    user_id       bigint                   NOT NULL,
    event         text         DEFAULT ''  NOT NULL,
    source_msg_id bigint       DEFAULT 0   NOT NULL,
    valid_from    timestamp    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    invalid_at    timestamp    DEFAULT NULL,
    create_time   timestamp    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time   timestamp    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_deleted    boolean      DEFAULT false NOT NULL
);

COMMENT ON TABLE adi_user_profile_event IS 'Per-user event timeline (bi-temporal: valid_from/invalid_at), append-only, backs temporal questions like "what did the user do before" | 用户事件时间线：bi-temporal 时序区间，append-only，支撑时序问题';
COMMENT ON COLUMN adi_user_profile_event.event IS 'Event fact text | 事件事实文本';
COMMENT ON COLUMN adi_user_profile_event.source_msg_id IS 'Source character message id (traceability) | 来源角色消息id（溯源）';
COMMENT ON COLUMN adi_user_profile_event.valid_from IS 'Fact valid-from time (transaction/supplied time) | 事实生效时间';
COMMENT ON COLUMN adi_user_profile_event.invalid_at IS 'Fact invalidation time; NULL = still current | 事实失效时间；NULL=仍生效';

CREATE INDEX IF NOT EXISTS idx_user_profile_event_user ON adi_user_profile_event (user_id, invalid_at);

DROP TRIGGER IF EXISTS trigger_user_profile_update_time ON adi_user_profile;
CREATE TRIGGER trigger_user_profile_update_time
    BEFORE UPDATE
    ON adi_user_profile
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

DROP TRIGGER IF EXISTS trigger_user_profile_event_update_time ON adi_user_profile_event;
CREATE TRIGGER trigger_user_profile_event_update_time
    BEFORE UPDATE
    ON adi_user_profile_event
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ---------- P0-2: 语义记忆向量表存量行回填 memory_valid='true' ----------
-- 动态分表（adi_character_memory_embedding[_<suffix>] 运行时建表），用 DO $$ 遍历 pg_tables。
-- 只回填缺键的行；运行时新写入路径都会显式带 memory_valid，迁移后新旧行过滤语义一致。
DO $$
DECLARE
    t record;
    affected bigint;
    total bigint := 0;
BEGIN
    FOR t IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename LIKE 'adi_character_memory_embedding%'
    LOOP
        -- 动态分表的 metadata 列类型不统一（既有 json 也有 jsonb），
        -- json 类型没有 json||jsonb 运算符（V27 首启失败根因），按类型分支执行。
        IF (
            SELECT data_type FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = t.tablename AND column_name = 'metadata'
        ) = 'json' THEN
            EXECUTE format(
                'UPDATE %I SET metadata = (metadata::text::jsonb || ''{"memory_valid": "true"}''::jsonb)::text::json
                 WHERE metadata::text NOT LIKE ''%%memory_valid%%''',
                t.tablename);
        ELSE
            EXECUTE format(
                'UPDATE %I SET metadata = metadata || ''{"memory_valid": "true"}''::jsonb
                 WHERE metadata::text NOT LIKE ''%%memory_valid%%''',
                t.tablename);
        END IF;
        GET DIAGNOSTICS affected = ROW_COUNT;
        total := total + affected;
    END LOOP;
    RAISE NOTICE 'V27 memory_valid backfill done, rows updated: %', total;
END $$;
