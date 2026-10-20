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
