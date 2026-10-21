-- T7-01（2026-10-21）：把「本次检索的 query_id」落到消息引用行。
-- 目的：回答→引用→👍👎 反馈链路在页面刷新后仍能定位到那次检索（[META] 里的 refs 只是当次会话内可见）。
-- 说明：历史数据无法回填（当时的 query_id 未记录），字段可空；空值时前端不展示反馈按钮。
alter table adi_character_message_ref_embedding
    add column if not exists rag_query_id varchar(64);

comment on column adi_character_message_ref_embedding.rag_query_id is
    'T7-01：本次向量检索的 query_id（adi_rag_query_log.query_id），供 👍👎 反馈定位；历史行为空 | T7-01: query_id of the retrieval, used by thumbs feedback; null for legacy rows';
