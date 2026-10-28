-- ============================================================
-- 043_chat_memory_summary.sql（二开 T8-02，2026-10-28）
-- chat_memory 加 summary 槽位：压缩摘要持久化回写，消除每轮重复摘要。
--   背景：T6-06 的会话压缩只发生在「组装上下文」的内存副本上，不回写 store，
--   同一批旧历史每轮都被重新 LLM 摘要（token 成本 ×N）。
--   方案 A：chat_memory 加 summary 列——压缩产出的摘要落此槽位；
--   同时把已被摘要覆盖的旧消息从 messages 窗口收缩掉（两者由调用方
--   PgChatMemoryStore#saveSummaryAndShrink 在同一次 upsert 里原子完成）；
--   后续轮次装配上下文时从槽位回注摘要消息，仅当「未被摘要覆盖的新历史」
--   再次超阈值时才增量合并摘要（merge prior + 新增 older），一次压缩一次 LLM。
-- 幂等：IF NOT EXISTS，可重复执行。
-- ============================================================

ALTER TABLE chat_memory ADD COLUMN IF NOT EXISTS summary text NULL;

COMMENT ON COLUMN chat_memory.summary IS 'Persisted conversation summary covering the dropped oldest messages (T8-02); NULL until first compression | 已被摘要覆盖并从 messages 窗口收缩掉的旧历史的持久化摘要（T8-02），首次压缩前为 NULL';
