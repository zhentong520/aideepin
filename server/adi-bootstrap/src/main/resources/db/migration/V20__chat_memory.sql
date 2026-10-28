-- S2-04（2026-10-28）：短期记忆外置 Postgres（ADR-003 落地），替代 MapDB 单机文件。
-- 一行 = 一个 memory_id 的完整消息窗口（langchain4j 序列化 JSON）。
-- 设计取舍：langchain4j 的 ChatMemoryStore 接口是「整列表读写」语义（每轮全量 update），
-- 逐条消息存表需要 diff/replace 逻辑，复杂易错且无消费方；先按会话整体存储（与 MapDB
-- 语义完全对齐，写放大 = 窗口大小，通常 ≤ 20 条消息 / 几 KB，可接受），后续有审计需求
-- 再演进为逐条流水表。
CREATE TABLE chat_memory
(
    memory_id   VARCHAR(64) PRIMARY KEY,          -- 会话/角色维度记忆键（characterUuid / chatUuid 等）
    messages    TEXT        NOT NULL,             -- langchain4j ChatMessageSerializer 序列化的消息窗口 JSON
    create_time TIMESTAMP   NOT NULL DEFAULT now(),
    update_time TIMESTAMP   NOT NULL DEFAULT now()
);

COMMENT ON TABLE chat_memory IS '短期记忆存储（S2-04/ADR-003）| Short-term chat memory store';
COMMENT ON COLUMN chat_memory.memory_id IS '记忆键（会话 uuid / 角色 uuid）| memory key';
COMMENT ON COLUMN chat_memory.messages IS '消息窗口 JSON（langchain4j 序列化）| serialized message window';

CREATE TRIGGER trigger_chat_memory_update_time
    BEFORE UPDATE
    ON chat_memory
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();
