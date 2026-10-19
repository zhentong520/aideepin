package com.moyz.adi.it;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 集成测试：全新库迁移完备性（ARCH-01 / DB-01 的机器化验收）。
 * <p>
 * 起真实 pgvector/pg16 容器 → 依次执行 V1__baseline.sql 与 V{n}__*.sql →
 * 断言「实体 @TableName 引用的所有表都真实存在」。此前 all_ddl.sql 缺 3 表 1 列、
 * 全新库起不来（DB-01）正是这类问题，本测试让它从此进安全网。
 * <p>
 * 刻意不起 Spring：容器内 psql 执行 SQL，断言走 information_schema。
 * <p>
 * 需要 Docker；本地默认运行（Docker 常驻）。CI 无 Docker 时设
 * {@code SKIP_INTEGRATION=1} 跳过（Assumptions 中止 = JUnit 标记跳过，不算失败）。
 */
class SchemaMigrationTest {

    private static final Path SERVER_DIR = Paths.get(System.getProperty("user.dir")).getParent();

    private static final Path MIGRATION_DIR = SERVER_DIR.resolve("db_migration");
    private static final Path ENTITY_DIR = SERVER_DIR.resolve(
            "adi-common/src/main/java/com/moyz/adi/common/entity");

    private static final Pattern CREATE_TABLE = Pattern.compile(
            "(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?([a-z0-9_]+)");
    private static final Pattern TABLE_NAME = Pattern.compile(
            "@TableName\\((?:value\\s*=\\s*)?\"([a-zA-Z0-9_]+)\"[^)]*\\)?");

    /** 按 embedding 维度动态分表的基名（PgVectorEmbeddingStoreConfig 运行时建表） */
    private static final Set<String> DYNAMIC_TABLES = Set.of(
            "adi_knowledge_base_embedding",
            "adi_character_memory_embedding",
            "adi_character_episodic_memory_embedding");

    @Test
    void allEntityTablesExistAfterMigrations() throws IOException, InterruptedException {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("aideepin")
                .withUsername("aideepin")
                .withPassword("aideepin123")) {
            Assumptions.assumeTrue(!"1".equals(System.getenv("SKIP_INTEGRATION")) && !"true".equals(System.getenv("SKIP_INTEGRATION")),
                    "SKIP_INTEGRATION set, skip docker-based schema migration test");
            pg.start();

            // 1. 依赖函数（V1 里的触发器要用）+ 依次执行 V1 baseline 与 V2+ 增量
            exec(pg, "CREATE EXTENSION IF NOT EXISTS vector;", "CREATE EXTENSION IF NOT EXISTS age;",
                    "CREATE OR REPLACE FUNCTION update_modified_column() RETURNS trigger AS $$ "
                            + "BEGIN NEW.update_time = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;");
            runMigration(pg, "adi-bootstrap/src/main/resources/db/migration/V1__baseline.sql");
            runMigrationsAfterV1(pg);

            // 2. 实体表全集（与 check_schema_sync.sh 同口径）
            Set<String> entityTables = new LinkedHashSet<>();
            try (Stream<Path> files = Files.list(ENTITY_DIR)) {
                for (Path f : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                    Matcher m = TABLE_NAME.matcher(Files.readString(f));
                    while (m.find()) {
                        entityTables.add(m.group(1).toLowerCase());
                    }
                }
            }
            entityTables.removeAll(DYNAMIC_TABLES);
            assertTrue(entityTables.size() > 40, "实体表数异常，TABLE_NAME 提取可能失效: " + entityTables.size());

            // 3. 库中真实表全集
            Set<String> dbTables = new LinkedHashSet<>();
            for (String line : exec(pg, "SELECT tablename FROM pg_tables WHERE schemaname='public';").split("\n")) {
                String t = line.trim();
                if (!t.isEmpty()) {
                    dbTables.add(t);
                }
            }

            // 4. 对账：实体引用而库中不存在 → 迁移缺表（DB-01 类缺陷回归）
            List<String> missing = entityTables.stream()
                    .filter(t -> !dbTables.contains(t))
                    .collect(Collectors.toList());
            assertEquals(List.of(), missing, "迁移后仍缺表（DB-01 回归）");

            // 5. 抽查 DB-01 修复点：raw_text 列与 adi_user_agent_conv 唯一约束
            String rawText = exec(pg, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name='adi_document' AND column_name='raw_text';").trim();
            assertEquals("1", rawText, "adi_document.raw_text 缺失");

            String uacConstraint = exec(pg, "SELECT count(*) FROM pg_indexes "
                    + "WHERE tablename='adi_user_agent_conv' AND indexname='uk_adi_user_agent_conv';").trim();
            assertEquals("1", uacConstraint, "adi_user_agent_conv 唯一索引缺失");
        }
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
        List<String> cmds = new ArrayList<>();
        cmds.add("psql");
        // -tA: tuples-only + unaligned，输出纯值（默认表格输出会让 count 断言失败）
        cmds.add("-tA");
        cmds.add("-U");
        cmds.add(pg.getUsername());
        cmds.add("-d");
        cmds.add(pg.getDatabaseName());
        for (String s : sql) {
            cmds.add("-c");
            cmds.add(s);
        }
        org.testcontainers.containers.Container.ExecResult r = pg.execInContainer(cmds.toArray(new String[0]));
        if (r.getExitCode() != 0) {
            throw new AssertionError("psql failed: " + r.getStderr());
        }
        return r.getStdout();
    }
}
