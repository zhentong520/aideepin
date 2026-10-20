-- ============================================================
-- V14__embedding_provider_and_memory_user.sql（二开第 8 批，2026-10-20）
-- 一个迁移覆盖本批两处 DDL/数据变更（T7-05 + T7-07）：
--
-- 【T7-05】知识库 embedding provider 溯源列：
--   adi_knowledge_base.embedding_provider / embedding_provider_version。
--   换 embedding 模型（如切 bge-m3）后向量表按「模型+维度」分表，新表为空、
--   旧表成孤儿，检索不报错只是永远零命中。provider 版本串即溯源凭据：
--   记录「该库向量由哪个 provider 产出」，与当前生效 provider 比对可识别
--   「向量建于旧模型之下」的库，动作 = 按当前 provider 全量重建；
--   回滚 = 把 adi.embedding-model 切回旧 provider 再重建一次。
--   存量库两列留空串（未知）——消费方必须跳过比对而非判为不一致，
--   避免升级后第一启动把全部存量库刷成告警（EmbeddingProviderGuard 约定）。
--
-- 【T7-07】记忆用户维度回填：把 user_id 补进语义/情景记忆向量表的 metadata。
--   推导链：metadata->>'conv_id' → adi_character.uuid → adi_character.user_id。
--   同时新建敏感记忆确认队列表 adi_memory_pending（PII pending→confirm 两段式）。
--   ⚠️ 动态分表（adi_character_memory_embedding[_<suffix>] 等由
--      PgVectorEmbeddingStoreConfig 运行时建表），用 DO $$ 遍历 pg_tables。
--   ⚠️ 只 UPDATE metadata，绝不触碰向量列（不重跑 embedding）。
--   ⚠️ 幂等：WHERE 只更新缺 user_id 的行，复跑第二次影响 0 行。
--   ⚠️ 空值显式兜底：character 行缺失/无主的记忆写哨兵 '__unknown__'，
--      永远不等于任何真实用户 id，按用户过滤时不会漏给任何人。
-- Idempotent: safe to run repeatedly
-- ============================================================

-- ---------- T7-05: 知识库 embedding provider 溯源列 ----------
alter table adi_knowledge_base add column if not exists embedding_provider varchar(64) default '' not null;
comment on column adi_knowledge_base.embedding_provider is 'Embedding provider name that produced the current vector index (blank = unknown/legacy; consumers must skip comparison, not treat as mismatch) | 产出当前向量索引的 embedding provider 名称（空=未知/存量，消费方跳过比对）';

alter table adi_knowledge_base add column if not exists embedding_provider_version varchar(64) default '' not null;
comment on column adi_knowledge_base.embedding_provider_version is 'Immutable embedding provider version (local name@dim, remote platform/name@dim). Mismatch with the active provider means vectors were built by an older model: retrieval would silently recall nothing until a rebuild. Rollback = switch adi.embedding-model back and rebuild again | embedding provider 不可变版本串；与当前生效 provider 不一致即向量建于旧模型，需按当前 provider 重建；回滚=切回旧 provider 再重建';

-- ---------- T7-07: 敏感记忆两段式确认 pending 表 ----------
-- PII（联系方式/证件/账号密码）命中的抽取记忆不直写向量库，先进本表 PENDING，
-- 用户 confirm 后由 MemoryPendingService 写入对应向量库，reject 则标记驳回。
-- ⚠️ confirm 必须校验 pending.user_id = 当前登录用户——共享的是"写入资格确认"，
--    谁的记忆谁确认，任何人不能替他人确认（隔离断言在 MemoryPendingServiceTest）。
CREATE TABLE IF NOT EXISTS adi_memory_pending
(
    id            bigserial primary key,
    memory_uuid   varchar(32)       DEFAULT ''        NOT NULL,
    user_id       bigint                              NOT NULL,
    character_id  bigint            DEFAULT 0         NOT NULL,
    memory_type   smallint          DEFAULT 1         NOT NULL,
    content       text              DEFAULT ''        NOT NULL,
    status        varchar(16)       DEFAULT 'PENDING' NOT NULL,
    source_msg_id bigint            DEFAULT 0         NOT NULL,
    create_time   timestamp         DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time   timestamp         DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_deleted    boolean           DEFAULT false     NOT NULL
);

COMMENT ON TABLE adi_memory_pending IS 'Sensitive (PII) memory pending user confirmation before vector-store persistence | 敏感(PII)记忆持久化前的用户确认队列表';
COMMENT ON COLUMN adi_memory_pending.memory_uuid IS 'Pending row unique uuid | 待确认行唯一uuid';
COMMENT ON COLUMN adi_memory_pending.user_id IS 'Owner user id; only this user may confirm/reject | 归属用户id，仅本人可确认/驳回';
COMMENT ON COLUMN adi_memory_pending.character_id IS 'Owning character id (conv context) | 归属角色id（对话上下文）';
COMMENT ON COLUMN adi_memory_pending.memory_type IS 'MemoryType numeric code: 1=semantic 2=episodic (routes the confirm-time vector write) | MemoryType数值编码：1=语义 2=情景（决定确认后写入哪个向量库）';
COMMENT ON COLUMN adi_memory_pending.content IS 'Memory content awaiting confirmation | 待确认的记忆内容';
COMMENT ON COLUMN adi_memory_pending.status IS 'PENDING / CONFIRMED / REJECTED | 待确认 / 已确认(已入向量库) / 已驳回';
COMMENT ON COLUMN adi_memory_pending.source_msg_id IS 'Source character message id (traceability) | 来源角色消息id（溯源）';

CREATE INDEX IF NOT EXISTS idx_memory_pending_user_status ON adi_memory_pending (user_id, status);
CREATE INDEX IF NOT EXISTS idx_memory_pending_create_time ON adi_memory_pending (create_time);

DROP TRIGGER IF EXISTS trigger_memory_pending_update_time ON adi_memory_pending;
CREATE TRIGGER trigger_memory_pending_update_time
    BEFORE UPDATE
    ON adi_memory_pending
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ---------- T7-07: 记忆 user_id 回填（按 owner 关系推导） ----------
DO $$
DECLARE
    t record;
    affected bigint;
    total bigint := 0;
BEGIN
    FOR t IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public'
          AND (tablename LIKE 'adi_character_memory_embedding%'
               OR tablename LIKE 'adi_character_episodic_memory_embedding%')
    LOOP
        -- 1) 有主记忆：按 conv_id → adi_character.uuid 取 user_id 写入 metadata
        EXECUTE format(
            'UPDATE %I e SET metadata = COALESCE(e.metadata::jsonb, ''{}''::jsonb) || jsonb_build_object(''user_id'', COALESCE(c.user_id::text, ''__unknown__'')) '
            'FROM adi_character c '
            'WHERE e.metadata->>''conv_id'' = c.uuid '
            '  AND e.metadata->>''user_id'' IS NULL',
            t.tablename);
        GET DIAGNOSTICS affected = ROW_COUNT;
        total := total + affected;
        RAISE NOTICE 'memory user_id backfill on % (owned): % rows', t.tablename, affected;
        -- 2) 无主记忆（character 行已不存在）：写哨兵值，绝不匹配任何真实用户
        EXECUTE format(
            'UPDATE %I e SET metadata = COALESCE(e.metadata::jsonb, ''{}''::jsonb) || jsonb_build_object(''user_id'', ''__unknown__'') '
            'WHERE e.metadata->>''user_id'' IS NULL',
            t.tablename);
        GET DIAGNOSTICS affected = ROW_COUNT;
        total := total + affected;
        RAISE NOTICE 'memory user_id backfill on % (orphan sentinel): % rows', t.tablename, affected;
    END LOOP;
    RAISE NOTICE 'memory user_id backfill total: % rows', total;
END $$;
