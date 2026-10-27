package com.moyz.adi.it;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2-01 实库验证（ADR-002 核心语义，testcontainers 真实 Postgres，不起 Spring）：
 * <ol>
 *   <li>并发抢占：20 线程用同一条 SKIP LOCKED claim 语句抢 N 条任务，
 *       每条任务被且仅被 1 个线程抢到（无重复执行）；</li>
 *   <li>Outbox 一致性：业务事务内 INSERT 任务后回滚 → task_queue 不残留；</li>
 *   <li>孤儿回收：RUNNING 且心跳超时的行被 requeueOrphans 重回 PENDING。</li>
 * </ol>
 * claim 语句与 {@code TaskQueueMapper.xml#claimNext} 同语义——XML 由单测覆盖 SQL 语法，
 * 此处验证的是该语义在 Postgres 真并发下的正确性。需要 Docker；SKIP_INTEGRATION=1 跳过。
 */
class TaskQueueClaimIT {

    private static final Path SERVER_DIR = Paths.get(System.getProperty("user.dir")).getParent();

    /** 与 TaskQueueMapper.xml#claimNext 同语义（同步点见该文件） */
    private static final String CLAIM_SQL = """
            UPDATE task_queue
            SET status = 'RUNNING', owner_instance = ?, next_run_at = now()
            WHERE id = (
                SELECT id FROM task_queue
                WHERE status = 'PENDING' AND next_run_at <= now() AND is_deleted = false
                ORDER BY priority, next_run_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id""";

    @Test
    void concurrentClaimIsExactlyOnceAndOutboxRollbackLeavesNoResidue() throws Exception {
        try (PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("aideepin")
                .withUsername("aideepin")
                .withPassword("aideepin123")) {
            Assumptions.assumeTrue(!"1".equals(System.getenv("SKIP_INTEGRATION")) && !"true".equals(System.getenv("SKIP_INTEGRATION")),
                    "SKIP_INTEGRATION set, skip docker-based task queue IT");
            pg.start();
            runMigration(pg);

            PGSimpleDataSource ds = new PGSimpleDataSource();
            ds.setUrl(pg.getJdbcUrl());
            ds.setUser(pg.getUsername());
            ds.setPassword(pg.getPassword());
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            // ===== 1. 并发抢占：20 线程抢 10 条任务，每条恰被抢一次 =====
            int taskCount = 10;
            for (int i = 0; i < taskCount; i++) {
                jdbc.update("INSERT INTO task_queue (task_type, payload, priority) VALUES (?, ?::jsonb, ?)",
                        "KB_INDEX", "{\"n\":" + i + "}", i % 3);
            }
            int threads = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            Set<Long> claimedIds = ConcurrentHashMap.newKeySet();
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger totalClaims = new AtomicInteger();
            for (int t = 0; t < threads; t++) {
                final String owner = "it-instance-" + t;
                pool.execute(() -> {
                    try {
                        start.await();
                    } catch (InterruptedException ignored) {
                        return;
                    }
                    // 每线程循环抢占直到抢空，断言总抢占数 == 任务数
                    while (true) {
                        List<Long> claimed = jdbc.queryForList(CLAIM_SQL, Long.class, owner);
                        if (claimed.isEmpty()) {
                            return;
                        }
                        claimedIds.add(claimed.get(0));
                        totalClaims.incrementAndGet();
                    }
                });
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "claim threads did not finish in time");

            assertEquals(taskCount, claimedIds.size(), "每条任务应恰被抢占一次（SKIP LOCKED 语义）");
            assertEquals(taskCount, totalClaims.get(), "并发下不应出现同一任务被多次 UPDATE 成功");
            Long runningCount = jdbc.queryForObject(
                    "SELECT count(*) FROM task_queue WHERE status='RUNNING'", Long.class);
            assertEquals(taskCount, runningCount, "全部应处于 RUNNING 且 owner 不为空");

            // ===== 2. Outbox 一致性：事务回滚 → 任务不残留 =====
            PlatformTransactionManager txManager = new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
            TransactionTemplate tx = new TransactionTemplate(txManager);
            tx.execute(status -> {
                jdbc.update("INSERT INTO task_queue (task_type, payload, priority) VALUES (?, ?::jsonb, ?)",
                        "KB_INDEX", "{\"rollback\":true}", 9);
                status.setRollbackOnly();
                return null;
            });
            Long rollbackResidue = jdbc.queryForObject(
                    "SELECT count(*) FROM task_queue WHERE payload->>'rollback' = 'true'", Long.class);
            assertEquals(0L, rollbackResidue, "业务事务回滚时任务行必须一起回滚（Outbox）");

            // ===== 3. 孤儿回收：心跳超时的 RUNNING 重回 PENDING =====
            jdbc.update("UPDATE task_queue SET status='PENDING', owner_instance=NULL, retry_count=0, next_run_at=now()");
            // 模拟进程死亡：心跳停留在 11 分钟前。
            // update_modified_column 触发器会把 UPDATE 的 update_time 强制刷成 now()，
            // 故先禁用触发器再回拨（生产中 RUNNING 行只有心跳会刷 update_time，不受影响）
            jdbc.update("ALTER TABLE task_queue DISABLE TRIGGER trigger_task_queue_update_time");
            jdbc.update("UPDATE task_queue SET update_time = now() - interval '11 minutes', status='RUNNING', owner_instance='dead-instance'");
            jdbc.update("ALTER TABLE task_queue ENABLE TRIGGER trigger_task_queue_update_time");
            int requeued = jdbc.update("""
                    UPDATE task_queue SET status='PENDING', owner_instance=NULL,
                        last_error='orphan requeue: no heartbeat within timeout'
                    WHERE status='RUNNING' AND is_deleted=false
                      AND update_time < now() - make_interval(secs => ?)""", 600);
            assertEquals(taskCount, requeued, "全部孤儿任务应被回收");
            Long pendingAfter = jdbc.queryForObject(
                    "SELECT count(*) FROM task_queue WHERE status='PENDING'", Long.class);
            assertEquals(taskCount, pendingAfter);
        }
    }

    private void runMigration(PostgreSQLContainer<?> pg) throws IOException, InterruptedException {
        Path file = SERVER_DIR.resolve("adi-bootstrap/src/main/resources/db/migration/V19__task_queue.sql");
        // 容器内无 update_modified_column 函数（V1 的触发器函数），补齐依赖后再执行 V19
        String containerPath = "/tmp/" + file.getFileName();
        pg.copyFileToContainer(MountableFile.forHostPath(file), containerPath);
        exec(pg, "CREATE OR REPLACE FUNCTION update_modified_column() RETURNS trigger AS $$ "
                + "BEGIN NEW.update_time = now(); RETURN NEW; END; $$ LANGUAGE plpgsql;");
        exec(pg, "\\i " + containerPath);
    }

    private void exec(PostgreSQLContainer<?> pg, String... sql) throws IOException, InterruptedException {
        for (String s : sql) {
            org.testcontainers.containers.Container.ExecResult result =
                    pg.execInContainer("psql", "-U", pg.getUsername(), "-d", pg.getDatabaseName(), "-c", s);
            assertEquals(0, result.getExitCode(), "psql failed: " + result.getStderr());
        }
    }
}
