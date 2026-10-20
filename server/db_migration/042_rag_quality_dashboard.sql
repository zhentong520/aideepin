-- ============================================================
-- 042_rag_quality_dashboard.sql（V12__rag_quality_dashboard.sql，二开 T7-01，2026-10-20）
-- RAG 质量看板 + 失败闭环 DDL：
--   1) adi_rag_query_log 补 query_text：失败/低分案例一键转评测题需要原始问句
--      （hit_segments 只有命中明细，没有问句本身，转评测题时无从取题面）。
--   2) 新建 adi_rag_feedback：👍👎 反馈表（用户聊天页对每条带知识库来源的回答评价）。
--      独立成表而非复用 adi_rag_query_log.feedback 列的原因：
--        - comment 文字说明 varchar(16) 的旧列放不下；
--        - message_uuid（回答消息）只有用户端知道，需要落库关联；
--        - 唯一部分索引 (query_id, user_uuid) 保证「同一用户同一检索一票，可改票」。
--      旧列 feedback 保留不再写入（注释同步标注取代关系），仅为兼容。
-- 幂等：全部 IF NOT EXISTS 写法，可重复执行。
-- ============================================================

-- ---------- 1. 检索日志补 query_text ----------
ALTER TABLE adi_rag_query_log ADD COLUMN IF NOT EXISTS query_text text NOT NULL DEFAULT '';

COMMENT ON COLUMN adi_rag_query_log.query_text IS 'Raw user query text of this retrieval, truncated to 500 chars | 本次检索的用户原始问句（截断至500字符），失败案例转评测题的题面来源';

-- 旧列 feedback 被 adi_rag_feedback 表取代：更新注释说明关系，列本身保留仅为兼容（不再写入）
COMMENT ON COLUMN adi_rag_query_log.feedback IS 'Legacy inline feedback, superseded by table adi_rag_feedback (T7-01); kept for compatibility only, never written again | 旧版内联反馈字段，已被 adi_rag_feedback 表取代（T7-01），保留仅为兼容、不再写入';

-- ---------- 2. 👍👎 反馈表 ----------
CREATE TABLE IF NOT EXISTS adi_rag_feedback
(
    id            bigserial primary key,
    feedback_uuid varchar(64)  default '' not null,
    query_id      varchar(64)  default '' not null,
    message_uuid  varchar(64)  default '' not null,
    user_uuid     varchar(64)  default '' not null,
    rating        varchar(16)  default '' not null,
    comment       varchar(500) default '' not null,
    create_time   timestamp    default CURRENT_TIMESTAMP not null,
    update_time   timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted    boolean      default false not null
);

COMMENT ON TABLE adi_rag_feedback IS 'RAG answer feedback (thumbs up/down) from the user chat page | RAG回答的用户👍👎反馈（用户聊天页提交）';
COMMENT ON COLUMN adi_rag_feedback.feedback_uuid IS 'Feedback row unique uuid | 反馈行唯一uuid';
COMMENT ON COLUMN adi_rag_feedback.query_id IS 'Related adi_rag_query_log.query_id (one retrieval = one row there) | 关联 adi_rag_query_log.query_id（一次检索一行）';
COMMENT ON COLUMN adi_rag_feedback.message_uuid IS 'Answer message uuid from the user chat page (blank until user-web wiring, T7-01) | 用户聊天页回答消息uuid（user-web 接入前可为空）';
COMMENT ON COLUMN adi_rag_feedback.user_uuid IS 'Feedback author uuid (must be a logged-in user) | 反馈提交用户uuid（必须为登录用户）';
COMMENT ON COLUMN adi_rag_feedback.rating IS 'PASS (thumbs up) or FAIL (thumbs down) | PASS=👍 或 FAIL=👎';
COMMENT ON COLUMN adi_rag_feedback.comment IS 'Optional free-text explanation, max 500 chars | 可选文字说明，最长500字符';

-- 同一用户对同一检索只保留一票（可改票：PASS 可改 FAIL），软删行不占位
CREATE UNIQUE INDEX IF NOT EXISTS uk_rag_feedback_query_user
    ON adi_rag_feedback (query_id, user_uuid) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_rag_feedback_user ON adi_rag_feedback (user_uuid);
CREATE INDEX IF NOT EXISTS idx_rag_feedback_time ON adi_rag_feedback (create_time);

DROP TRIGGER IF EXISTS trigger_rag_feedback_update_time ON adi_rag_feedback;
CREATE TRIGGER trigger_rag_feedback_update_time
    BEFORE UPDATE
    ON adi_rag_feedback
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();
