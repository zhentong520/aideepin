package com.moyz.adi.it;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.service.CredentialBackfillService;
import com.moyz.adi.common.util.AesUtil;
import com.moyz.adi.common.util.CredentialCipher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * T2-07 实库验证（2026-10-19）：凭据回填端到端。
 * <p>
 * 起真实 pgvector/pg16 容器 → 跑 V1..V7 全部迁移 → 插入含历史明文/AesUtil 密文的
 * 工作流邮件节点 → 真实跑 {@link CredentialBackfillService}（手工装配，不起 Spring）→
 * <ul>
 *   <li>第一次回填迁移 N 行</li>
 *   <li>第二次复跑 0 行（幂等，任务书硬性要求）</li>
 *   <li>grep 确认 node_config jsonb 中原字段已置空、credential_uuid 已写入</li>
 *   <li>adi_credential 密文可解回原文（AES-256-GCM 往返）</li>
 * </ul>
 */
class CredentialVaultBackfillTest {

    private static final Path SERVER_DIR = Paths.get(System.getProperty("user.dir")).getParent();

    private static final String AES_KEY = "0123456789abcdef";
    private static final String MASTER_KEY = java.util.Base64.getEncoder().encodeToString(new byte[]{
            11, 22, 33, 44, 55, 66, 77, 88, 99, 111, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33});

    @Test
    void backfillMigratesOnceThenZeroAndBlanksJsonb() throws IOException, InterruptedException {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("aideepin")
                .withUsername("aideepin")
                .withPassword("aideepin123")) {
            Assumptions.assumeTrue(!"1".equals(System.getenv("SKIP_INTEGRATION")) && !"true".equals(System.getenv("SKIP_INTEGRATION")),
                    "SKIP_INTEGRATION set, skip docker-based credential backfill test");
            pg.start();

            // 1. 依赖函数 + 全部迁移 V1..V7
            exec(pg, "CREATE EXTENSION IF NOT EXISTS vector;", "CREATE EXTENSION IF NOT EXISTS age;",
                    "CREATE OR REPLACE FUNCTION update_modified_column() RETURNS trigger AS $$ "
                            + "BEGIN NEW.update_time = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;");
            runMigration(pg, "adi-bootstrap/src/main/resources/db/migration/V1__baseline.sql");
            runMigrationsAfterV1(pg);

            // 2. 构造存量数据：一个历史 AesUtil 密文密码节点 + 一个更早期的明文密码节点 + 一个无敏感键节点
            AesUtil.AES_KEY = AES_KEY;
            CredentialCipher.reset();
            CredentialCipher.init(MASTER_KEY);

            // ⚠️ ASCII 明文：AesUtil.decrypt(String) 用平台默认字符集（Windows GBK），非 ASCII 往返有损（既有行为）
            String plainPwd = "it-real-db-pwd-ascii";
            String legacyCipher = AesUtil.encrypt(plainPwd);
            JdbcTemplate jdbc = jdbcTemplate(pg);
            jdbc.update("INSERT INTO adi_workflow_node (uuid, workflow_id, workflow_component_id, title, node_config) " +
                            "VALUES (?, ?, ?, ?, ?::jsonb)",
                    "n1".repeat(16), 1L, 100L, "mail node legacy",
                    "{\"sender_type\":2,\"sender\":{\"name\":\"a\",\"mail\":\"a@b.c\",\"password\":\"" + legacyCipher + "\"}}");
            jdbc.update("INSERT INTO adi_workflow_node (uuid, workflow_id, workflow_component_id, title, node_config) " +
                            "VALUES (?, ?, ?, ?, ?::jsonb)",
                    "n2".repeat(16), 1L, 100L, "mail node plaintext",
                    "{\"sender\":{\"password\":\"raw-plain-pwd\"}}");
            jdbc.update("INSERT INTO adi_workflow_node (uuid, workflow_id, workflow_component_id, title, node_config) " +
                            "VALUES (?, ?, ?, ?, ?::jsonb)",
                    "n3".repeat(16), 1L, 100L, "no secret node", "{\"subject\":\"hello\"}");

            // 3. 真实跑回填：3 个候选节点，2 个有敏感键 → 迁移 2
            CredentialBackfillService service = newBackfillService(jdbc);
            long first = service.backfill();
            Assertions.assertEquals(2, first, "第一次回填应迁移 2 个节点");

            // 4. 幂等：第二次复跑 0 行
            long second = service.backfill();
            Assertions.assertEquals(0, second, "第二次回填必须 0 行（幂等）");

            // 5. grep 确认 jsonb 中原字段已空 + credential_uuid 已写入
            List<Map<String, Object>> nodes = jdbc.queryForList(
                    "SELECT title, node_config->'sender'->>'password' AS pwd, credential_uuid " +
                            "FROM adi_workflow_node WHERE is_deleted = false ORDER BY id");
            Assertions.assertEquals(3, nodes.size());
            for (Map<String, Object> node : nodes) {
                String pwd = (String) node.get("pwd");
                Assertions.assertTrue(pwd == null || pwd.isEmpty(),
                        "jsonb 原密码字段必须已空, node=" + node.get("title"));
                if ("no secret node".equals(node.get("title"))) {
                    Assertions.assertEquals("", node.get("credential_uuid"), "无敏感键节点不写 credential_uuid");
                } else {
                    Assertions.assertEquals(32, ((String) node.get("credential_uuid")).length(),
                            "已迁移节点必须写 credential_uuid");
                }
            }
            // 全表扫描确认无明文残留
            int plaintextLeft = jdbc.queryForObject(
                    "SELECT count(*) FROM adi_workflow_node WHERE node_config::text LIKE '%it-real-db-pwd%' " +
                            "OR node_config::text LIKE '%raw-plain-pwd%'", Integer.class);
            Assertions.assertEquals(0, plaintextLeft, "node_config 中不允许残留任何明文密码");

            // 6. adi_credential 密文可解回原文（两条：一条来自 AesUtil 密文、一条来自明文兜底）
            List<Map<String, Object>> credentials = jdbc.queryForList(
                    "SELECT uuid, type, cipher_text, iv, key_version FROM adi_credential ORDER BY id");
            Assertions.assertEquals(2, credentials.size());
            for (Map<String, Object> c : credentials) {
                String decrypted = CredentialCipher.decrypt((String) c.get("cipher_text"), (String) c.get("iv"));
                Assertions.assertTrue(plainPwd.equals(decrypted) || "raw-plain-pwd".equals(decrypted),
                        "解密结果必须是原始明文之一, got " + decrypted);
                Assertions.assertEquals(1, ((Number) c.get("key_version")).intValue());
                Assertions.assertTrue(!String.valueOf(c.get("cipher_text")).contains(plainPwd),
                        "密文不允许包含明文");
            }
        } finally {
            CredentialCipher.reset();
            AesUtil.AES_KEY = "";
        }
    }

    private CredentialBackfillService newBackfillService(JdbcTemplate jdbc) {
        // 手工装配（不起 Spring）：真实逻辑 + 真实 JDBC，开关显式打开
        CredentialBackfillService service = new CredentialBackfillService();
        AdiProperties props = new AdiProperties();
        props.getCredential().setVaultEnabled(true);
        ReflectionTestUtils.setField(service, "adiProperties", props);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbc);
        return service;
    }

    private JdbcTemplate jdbcTemplate(PostgreSQLContainer<?> pg) {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setURL(pg.getJdbcUrl());
        ds.setUser(pg.getUsername());
        ds.setPassword(pg.getPassword());
        return new JdbcTemplate(ds);
    }

    private void runMigration(PostgreSQLContainer<?> pg, String relPath) throws IOException, InterruptedException {
        Path file = SERVER_DIR.resolve(relPath);
        String containerPath = "/tmp/" + file.getFileName();
        pg.copyFileToContainer(MountableFile.forHostPath(file), containerPath);
        exec(pg, "\\i " + containerPath);
    }

    /** V1 之后按文件名顺序执行 V{n}__*.sql（与 Flyway / SchemaMigrationTest 一致） */
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

    private void exec(PostgreSQLContainer<?> pg, String... sql) throws IOException, InterruptedException {
        List<String> cmds = new ArrayList<>();
        cmds.add("psql");
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
        // 与 SchemaMigrationTest 同口径：仅退出码判定（age 扩展不可用等 NOTICE/ERROR 走 psql 默认容错继续执行）
        if (r.getExitCode() != 0) {
            throw new AssertionError("psql failed: " + r.getStderr());
        }
    }
}
