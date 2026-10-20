package com.moyz.adi.it;

import com.moyz.adi.common.mapper.RagFeedbackMapper;
import com.moyz.adi.common.mapper.RagQueryLogMapper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-01 验收：五指标聚合 SQL 正确性（真库造数）。
 * <p>
 * ⚠️ 与 {@code RagQualityDashboardTest}（mock 层）互补：那个验 Java 侧装配语义，
 * 本类验 SQL 口径本身——起真实 pg16 容器、跑全套 V1..Vn 迁移、造 5 条检索日志，
 * 然后用 NamedParameterJdbcTemplate <b>原样</b>执行
 * {@link RagQueryLogMapper#QUALITY_SUMMARY_SQL} / {@link RagQueryLogMapper#QUALITY_BY_KB_SQL}
 * 常量，断言五指标数值。SQL 与 Java 重算两边口径漂移时这里先红。
 * <p>需要 Docker；SKIP_INTEGRATION=1 时跳过（不算失败）。
 */
class RagQualitySqlIT {

    private static final Path SERVER_DIR = Paths.get(System.getProperty("user.dir")).getParent();

    /** 造数时间基准：begin/end 覆盖全部种子行 */
    private static final LocalDateTime BEGIN = LocalDateTime.of(2026, 10, 1, 0, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 10, 20, 0, 0, 0);
    private static final double THRESHOLD = 0.5;

    /**
     * MyBatis `#{name}` → Spring JDBC `:name` 占位符适配：
     * SQL 文本本体（WITH/SELECT/FILTER/percentile_cont）原样执行，仅占位符方言转换。
     */
    private static String springSql(String mybatisSql) {
        return mybatisSql.replaceAll("\\#\\{(\\w+)\\}", ":$1");
    }

    @Test
    void qualitySummarySqlComputesFiveMetricsFromSeededRows() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("aideepin")
                .withUsername("aideepin")
                .withPassword("aideepin123")) {
            Assumptions.assumeTrue(!"1".equals(System.getenv("SKIP_INTEGRATION")) && !"true".equals(System.getenv("SKIP_INTEGRATION")),
                    "SKIP_INTEGRATION set, skip docker-based SQL IT");
            pg.start();
            // 最小依赖：触发器函数 + V4（adi_rag_query_log）+ V12（query_text 列 + adi_rag_feedback）。
            // ⚠️ 刻意不跑全套迁移链（V1 需 age 扩展，与本 IT 无关）：
            // 迁移完备性由 SchemaMigrationTest 负责，本类只验证聚合 SQL 在真实 PG 上的口径。
            exec(pg, "CREATE OR REPLACE FUNCTION update_modified_column() RETURNS trigger AS $$ "
                    + "BEGIN NEW.update_time = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;");
            runMigration(pg, "adi-bootstrap/src/main/resources/db/migration/V4__rag_query_log.sql");
            runMigration(pg, "adi-bootstrap/src/main/resources/db/migration/V12__rag_quality_dashboard.sql");

            NamedParameterJdbcTemplate jdbc = jdbc(pg);

            // ---------- 造数 ----------
            // q1: 命中2，top 0.9，100ms          → 命中、非低分
            // q2: 命中1，top 0.4，200ms          → 命中、低分（0.4 < 0.5）
            // q3: 未命中，空命中数组，300ms       → 未命中（也计低分）
            // q4: 命中3，top 0.8，1000ms         → 命中、非低分
            // q5: 软删行                          → 不进任何口径
            insertLog(jdbc, "q1", "u-1", "[\"kb-a\",\"kb-b\"]", 2, "[{\"kb_uuid\":\"kb-a\",\"score\":0.9},{\"kb_uuid\":\"kb-a\",\"score\":0.7}]", 100, false);
            insertLog(jdbc, "q2", "u-1", "[\"kb-a\"]", 1, "[{\"kb_uuid\":\"kb-a\",\"score\":0.4}]", 200, false);
            insertLog(jdbc, "q3", "u-2", "[\"kb-a\"]", 0, "[]", 300, false);
            insertLog(jdbc, "q4", "u-2", "[\"kb-a\"]", 3, "[{\"kb_uuid\":\"kb-a\",\"score\":0.6},{\"kb_uuid\":\"kb-a\",\"score\":0.8}]", 1000, false);
            insertLog(jdbc, "q5", "u-2", "[\"kb-a\"]", 1, "[{\"kb_uuid\":\"kb-a\",\"score\":0.99}]", 50, true);
            jdbc.getJdbcOperations().update(
                    "INSERT INTO adi_rag_feedback (feedback_uuid, query_id, user_uuid, rating, create_time) VALUES " +
                            "('fb-pass-1', 'q1', 'u-1', 'PASS', ?), ('fb-fail-1', 'q2', 'u-2', 'FAIL', ?)",
                    Timestamp.valueOf(LocalDateTime.of(2026, 10, 11, 8, 0, 0)),
                    Timestamp.valueOf(LocalDateTime.of(2026, 10, 12, 8, 0, 0)));

            // ---------- 总聚合：五指标 ----------
            Map<String, Object> row = jdbc.queryForMap(springSql(RagQueryLogMapper.QUALITY_SUMMARY_SQL),
                    new MapSqlParameterSource()
                            .addValue("begin", BEGIN)
                            .addValue("end", END)
                            .addValue("lowScoreThreshold", THRESHOLD));

            assertEquals(4L, num(row.get("totalQueries")), "总次数（软删不计）");
            assertEquals(3L, num(row.get("hitQueries")), "命中次数");
            assertEquals(1.5, dbl(row.get("avgHitCount")), 1e-9, "平均命中文档数=(2+1+0+3)/4");
            assertEquals(75.0, dbl(row.get("hitQueries")) * 100.0 / 4, 1e-9);
            // 平均 Top 分数仅统计有命中的检索：(0.9+0.4+0.8)/3
            assertEquals(0.7, dbl(row.get("avgTopScore")), 1e-9, "平均Top分数=(0.9+0.4+0.8)/3，未命中不计");
            // 低分：q2（0.4<阈值）+ q3（无命中）→ 2/4
            assertEquals(2L, num(row.get("lowScoreQueries")), "低分次数=低分1+未命中1");
            assertEquals(0.8, dbl(row.get("topScoreP50")), 1e-9, "Top分数P50=median(0.4,0.8,0.9)");
            assertEquals(400.0, dbl(row.get("avgElapsedMs")), 1e-9, "平均耗时=(100+200+300+1000)/4");
            assertEquals(895.0, dbl(row.get("elapsedP95")), 1e-9, "耗时P95=300+0.85*(1000-300)");

            // ---------- 按知识库下钻 ----------
            List<Map<String, Object>> kbRows = jdbc.queryForList(springSql(RagQueryLogMapper.QUALITY_BY_KB_SQL),
                    new MapSqlParameterSource()
                            .addValue("begin", BEGIN)
                            .addValue("end", END)
                            .addValue("lowScoreThreshold", THRESHOLD));
            assertEquals(2, kbRows.size(), "kb_uuids 展开后 kb-a / kb-b 两行");
            Map<String, Map<String, Object>> byKb = new java.util.HashMap<>();
            for (Map<String, Object> r : kbRows) {
                byKb.put(String.valueOf(r.get("kbUuid")), r);
            }
            Map<String, Object> kbA = byKb.get("kb-a");
            Map<String, Object> kbB = byKb.get("kb-b");
            assertTrue(kbA != null && kbB != null, "下钻应含 kb-a 与 kb-b: " + byKb.keySet());
            assertEquals(4L, num(kbA.get("queryCount")), "kb-a 检索次数（q1-q4 各计一次）");
            assertEquals(3L, num(kbA.get("hitQueries")));
            assertEquals(1.5, dbl(kbA.get("avgHitCount")), 1e-9);
            assertEquals(0.7, dbl(kbA.get("avgTopScore")), 1e-9);
            assertEquals(1L, num(kbB.get("queryCount")), "kb-b 只被 q1 覆盖");
            assertEquals(0.9, dbl(kbB.get("avgTopScore")), 1e-9);

            // ---------- 反馈统计 SQL ----------
            Map<String, Object> fb = jdbc.queryForMap(springSql(RagFeedbackMapper.FEEDBACK_SUMMARY_SQL),
                    new MapSqlParameterSource()
                            .addValue("begin", BEGIN)
                            .addValue("end", END));
            assertEquals(1L, num(fb.get("passCount")));
            assertEquals(1L, num(fb.get("failCount")));
        }
    }

    // ---------- 造数工具 ----------

    private static void insertLog(NamedParameterJdbcTemplate jdbc, String queryId, String userUuid,
                                  String kbUuidsJson, int hitCount, String hitSegmentsJson,
                                  int elapsedMs, boolean deleted) {
        jdbc.getJdbcOperations().update(conn -> insertStatement(conn, queryId, userUuid, kbUuidsJson,
                hitCount, hitSegmentsJson, elapsedMs, deleted));
    }

    private static PreparedStatement insertStatement(Connection conn, String queryId, String userUuid,
                                                     String kbUuidsJson, int hitCount, String hitSegmentsJson,
                                                     int elapsedMs, boolean deleted) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO adi_rag_query_log (query_id, user_uuid, kb_uuids, filter_expression, hit_segments, "
                        + "hit_count, acl_filtered_count, elapsed_ms, prompt_tokens, embedding_model, feedback, "
                        + "query_text, create_time, is_deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
        ps.setString(1, queryId);
        ps.setString(2, userUuid);
        org.postgresql.util.PGobject kbUuids = new org.postgresql.util.PGobject();
        kbUuids.setType("jsonb");
        kbUuids.setValue(kbUuidsJson);
        ps.setObject(3, kbUuids);
        ps.setString(4, "");
        org.postgresql.util.PGobject hits = new org.postgresql.util.PGobject();
        hits.setType("jsonb");
        hits.setValue(hitSegmentsJson);
        ps.setObject(5, hits);
        ps.setInt(6, hitCount);
        ps.setInt(7, 0);
        ps.setInt(8, elapsedMs);
        ps.setInt(9, 0);
        ps.setString(10, "local:bge");
        ps.setString(11, "");
        ps.setString(12, "造数问句-" + queryId);
        ps.setTimestamp(13, Timestamp.valueOf(LocalDateTime.of(2026, 10, 10, 12, 0, 0)));
        ps.setBoolean(14, deleted);
        return ps;
    }

    private static long num(Object v) {
        return ((Number) v).longValue();
    }

    private static double dbl(Object v) {
        return ((Number) v).doubleValue();
    }

    // ---------- 容器/迁移基座（与 SchemaMigrationTest 同一套做法） ----------

    private NamedParameterJdbcTemplate jdbc(PostgreSQLContainer<?> pg) throws SQLException {
        SimpleDriverDataSource ds = new SimpleDriverDataSource();
        ds.setDriver(new org.postgresql.Driver());
        ds.setUrl(pg.getJdbcUrl());
        ds.setUsername(pg.getUsername());
        ds.setPassword(pg.getPassword());
        return new NamedParameterJdbcTemplate(ds);
    }

    private void runMigration(PostgreSQLContainer<?> pg, String relPath) throws IOException, InterruptedException {
        Path file = SERVER_DIR.resolve(relPath);
        String containerPath = "/tmp/" + file.getFileName();
        pg.copyFileToContainer(MountableFile.forHostPath(file), containerPath);
        exec(pg, "\\i " + containerPath);
    }

    /** V1 之后按文件名顺序执行 V{n}__*.sql（与 Flyway 一致） */
    private void runMigrationsAfterV1(PostgreSQLContainer<?> pg) throws IOException, InterruptedException {
        Path dir = SERVER_DIR.resolve("adi-bootstrap/src/main/resources/db/migration");
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> sorted = files
                    .filter(p -> p.getFileName().toString().matches("V\\d+__.*\\.sql")
                            && !p.getFileName().toString().startsWith("V1__"))
                    .sorted()
                    .collect(Collectors.toList());
            for (Path p : sorted) {
                runMigration(pg, "adi-bootstrap/src/main/resources/db/migration/" + p.getFileName());
            }
        }
    }

    private String exec(PostgreSQLContainer<?> pg, String... sql) throws IOException, InterruptedException {
        List<String> cmds = new java.util.ArrayList<>();
        cmds.add("psql");
        cmds.add("-tA");
        // 任一语句失败立即退出非零：与 Flyway 语义一致，防 psql 默认「报错继续、退出码 0」吞错
        cmds.add("-v");
        cmds.add("ON_ERROR_STOP=1");
        cmds.add("-U");
        cmds.add(pg.getUsername());
        cmds.add("-d");
        cmds.add(pg.getDatabaseName());
        for (String s : sql) {
            cmds.add("-c");
            cmds.add(s);
        }
        org.testcontainers.containers.Container.ExecResult result =
                pg.execInContainer(cmds.toArray(new String[0]));
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("psql failed: " + result.getStderr());
        }
        return result.getStdout();
    }
}
