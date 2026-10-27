-- S1-07a：ShedLock 定时任务防重（多实例共库部署时 @Scheduled 不重复执行）。
-- 表结构遵循 ShedLock JdbcTemplateLockProvider 约定（usingDbTime 模式，时钟以 DB 为准）。
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    CONSTRAINT shedlock_pkey PRIMARY KEY (name)
);
