-- ============================================================
-- V6__acl_backfill.sql（二开 T1-06，2026-10-16）
-- 存量 chunk ACL 回填：按库级 default_visibility_groups / default_sensitivity
-- + owner_uuid / is_public 推导，把 ACL 写进各维度动态向量表的 metadata（jsonb）。
--
-- ⚠️ 只 UPDATE metadata，绝不触碰向量列（不重跑 embedding、不重建索引）。
-- ⚠️ 幂等：WHERE 只更新缺 acl_sensitivity 的行，复跑第二次影响 0 行。
-- ⚠️ 动态分表名带维度后缀（adi_knowledge_base_embedding[_<suffix>]），
--    用 DO $$ 遍历 pg_tables 动态生成 UPDATE。
--
-- 密级推导规则（与写入侧 KbAclMetadata.effectiveSensitivity 完全一致）：
--   显式 PUBLIC/ORG 采用；否则 is_public=true → PUBLIC（保证回填前后公开库
--   检索行为零变化），其余 → PRIVATE。
-- 可见组：default_visibility_groups 数组折叠为 CSV + 合成组 owner:<owner_uuid>。
-- 注：判断"metadata 缺 acl_sensitivity"用 metadata->'acl_sensitivity' IS NULL，
--     不用 ? 操作符——该操作符与 JDBC 参数占位符冲突，管理端一键回填接口
--     （JdbcTemplate）复用同一条 SQL 时会直接报错。
-- Idempotent: safe to run repeatedly
-- ============================================================

DO $$
DECLARE
    t record;
    affected bigint;
    total bigint := 0;
BEGIN
    FOR t IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public'
          AND tablename LIKE 'adi_knowledge_base_embedding%'
    LOOP
        EXECUTE format(
            'UPDATE %I e SET metadata = COALESCE(e.metadata::jsonb, ''{}''::jsonb) || jsonb_build_object(' ||
            '  ''acl_sensitivity'', CASE WHEN k.default_sensitivity IN (''PUBLIC'',''ORG'') THEN k.default_sensitivity' ||
            '                          WHEN k.is_public THEN ''PUBLIC'' ELSE ''PRIVATE'' END,' ||
            '  ''acl_visibility_groups'', concat_ws('','', ' ||
            '    (SELECT string_agg(g, '','') FROM jsonb_array_elements_text(COALESCE(k.default_visibility_groups, ''[]''::jsonb)) g),' ||
            '    ''owner:'' || COALESCE(k.owner_uuid, '''')),' ||
            '  ''kb_owner_uuid'', COALESCE(k.owner_uuid, '''')) ' ||
            'FROM adi_knowledge_base k ' ||
            'WHERE e.metadata->>''kb_uuid'' = k.uuid ' ||
            '  AND e.metadata->''acl_sensitivity'' IS NULL',
            t.tablename);
        GET DIAGNOSTICS affected = ROW_COUNT;
        total := total + affected;
        RAISE NOTICE 'acl backfill on %: % rows', t.tablename, affected;
    END LOOP;
    RAISE NOTICE 'acl backfill total: % rows', total;
END $$;
