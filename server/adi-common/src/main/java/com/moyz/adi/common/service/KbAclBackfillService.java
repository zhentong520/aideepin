package com.moyz.adi.common.service;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 二开新增（T1-06，2026-10-16）：存量 chunk ACL 回填（V6__acl_backfill.sql 的 service 等价实现）。
 * <p>
 * 管理端「一键回填」入口 {@code POST /admin/kb/acl/backfill} 调用。规则与迁移完全一致：
 * <ul>
 *   <li>遍历所有 adi_knowledge_base_embedding[_&lt;suffix&gt;] 动态分表</li>
 *   <li>按库级 default_sensitivity / default_visibility_groups + owner_uuid / is_public
 *       推导 acl_sensitivity / acl_visibility_groups / kb_owner_uuid 三个键</li>
 *   <li>⚠️ 只 UPDATE metadata（jsonb 合并），绝不触碰向量列，不重跑 embedding</li>
 *   <li>幂等：WHERE 只更新缺 acl_sensitivity 的行，复跑第二次影响 0 行</li>
 * </ul>
 * 注：不用 {@code metadata ? 'key'} 操作符——与 JDBC 参数占位符冲突，改用 {@code ->} IS NULL。
 */
@Slf4j
@Service
public class KbAclBackfillService {

    /** 动态分表白名单（防注入：表名来自 pg_tables，仍按正则二次校验） */
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^adi_knowledge_base_embedding[A-Za-z0-9_]*$");

    private static final String LIST_TABLES_SQL =
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE 'adi_knowledge_base_embedding%'";

    private static final String UPDATE_METADATA_SQL = """
            UPDATE %s e SET metadata = COALESCE(e.metadata::jsonb, '{}'::jsonb) || jsonb_build_object(
              'acl_sensitivity', CASE WHEN k.default_sensitivity IN ('PUBLIC','ORG') THEN k.default_sensitivity
                                  WHEN k.is_public THEN 'PUBLIC' ELSE 'PRIVATE' END,
              'acl_visibility_groups', concat_ws(',',
                (SELECT string_agg(g, ',') FROM jsonb_array_elements_text(COALESCE(k.default_visibility_groups, '[]'::jsonb)) g),
                'owner:' || COALESCE(k.owner_uuid, '')),
              'kb_owner_uuid', COALESCE(k.owner_uuid, ''))
            FROM adi_knowledge_base k
            WHERE e.metadata->>'kb_uuid' = k.uuid
              AND e.metadata->'acl_sensitivity' IS NULL
            """;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 执行回填，返回累计影响行数。幂等：第二次调用应返回 0。
     */
    public long backfill() {
        List<String> tables = jdbcTemplate.queryForList(LIST_TABLES_SQL, String.class);
        long total = 0;
        for (String table : tables) {
            if (!TABLE_NAME_PATTERN.matcher(table).matches()) {
                log.warn("skip unexpected embedding table:{}", table);
                continue;
            }
            // 表名来自 pg_tables 且通过白名单正则，占位拼接安全（等价 format 的 %I）
            int affected = jdbcTemplate.update(String.format(UPDATE_METADATA_SQL, "\"" + table + "\""));
            log.info("acl backfill on {},{} rows", table, affected);
            total += affected;
        }
        log.info("acl backfill total,{} rows across {} tables", total, tables.size());
        return total;
    }
}
