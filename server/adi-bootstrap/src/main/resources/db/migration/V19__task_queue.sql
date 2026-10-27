-- S2-01（2026-10-27）：统一异步任务底座 task_queue（ADR-002 落地）。
-- 生产方在业务事务内 INSERT（同库即天然 Outbox：业务回滚任务不残留）；
-- 消费方用 FOR UPDATE SKIP LOCKED 抢占（Postgres 原生，多实例安全，无需分布式锁）；
-- 失败按指数退避重试（next_run_at = now() + 30s * 2^n），超 max_retry 转 DEAD；
-- RUNNING 任务由心跳刷 update_time（BEFORE UPDATE 触发器），超时无心跳视为孤儿重回 PENDING。
CREATE TABLE task_queue
(
    id             BIGSERIAL PRIMARY KEY,
    task_type      VARCHAR(64)  NOT NULL,                        -- KB_INDEX / DRAW / FINETUNE_POLL ...
    payload        JSONB        NOT NULL,                        -- 任务参数（JsonNode）
    status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',      -- PENDING/RUNNING/SUCCESS/FAILED/DEAD
    priority       SMALLINT     NOT NULL DEFAULT 5,              -- 数字小 = 优先级高；批量任务设大值防饥饿
    retry_count    INT          NOT NULL DEFAULT 0,
    max_retry      INT          NOT NULL DEFAULT 3,
    next_run_at    TIMESTAMP    NOT NULL DEFAULT now(),
    owner_instance VARCHAR(64),                                  -- 抢占实例标识（hostname:pid）
    last_error     TEXT,
    created_by     BIGINT,
    create_time    TIMESTAMP    NOT NULL DEFAULT now(),
    update_time    TIMESTAMP    NOT NULL DEFAULT now(),
    is_deleted     BOOLEAN      NOT NULL DEFAULT FALSE
);

-- 消费扫描索引：只覆盖待消费状态（部分索引，体量与队列深度成正比而非历史总量）
CREATE INDEX idx_task_consume ON task_queue (status, priority, next_run_at)
    WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_task_type ON task_queue (task_type, status);

COMMENT ON TABLE task_queue IS '统一异步任务队列（S2-01/ADR-002）| Unified async task queue';
COMMENT ON COLUMN task_queue.priority IS '数字小=优先级高 | lower value = higher priority';

CREATE TRIGGER trigger_task_queue_update_time
    BEFORE UPDATE
    ON task_queue
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();
