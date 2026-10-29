-- ============================================================
-- V24: chat_memory 加 summary 槽位（T8-02，与 db_migration/043 同步）
--
-- 真实浏览器 E2E 暴露：T8-02（2026-10-28）给 ChatMemory 实体与
-- ChatMemoryMapper 加了 summary 字段/SQL，并写了手动脚本
-- db_migration/043_chat_memory_summary.sql，但漏了 Flyway 迁移——
-- 走 Flyway 启动的环境 chat_memory 表没有 summary 列，
-- 聊天首轮即报『column "summary" does not exist』，LLM 流式回复中断。
-- 幂等：IF NOT EXISTS，可重复执行。
-- ============================================================

ALTER TABLE chat_memory ADD COLUMN IF NOT EXISTS summary text NULL;

COMMENT ON COLUMN chat_memory.summary IS 'Persisted conversation summary covering the dropped oldest messages (T8-02); NULL until first compression | 已被摘要覆盖并从 messages 窗口收缩掉的旧历史的持久化摘要（T8-02），首次压缩前为 NULL';
