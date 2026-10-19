-- ============================================================
-- V4__rag_query_log.sql（二开 T1-01，2026-10-16）
-- 检索日志表：让 RAG 行为可观测（ACL 上线后若过滤失效，靠它定位）。
-- 写入点：AdiEmbeddingStoreContentRetriever.retrieve()（知识库链路，与 T1-05 同一入口），
-- 异步写入（mainExecutor），失败仅 warn 不阻断检索。
-- Idempotent: safe to run repeatedly
-- ============================================================

create table if not exists adi_rag_query_log
(
    id                 bigserial primary key,
    query_id           varchar(64)  default ''  not null,
    user_uuid          varchar(64)  default ''  not null,
    kb_uuids           jsonb        default '[]' not null,
    filter_expression  text         default ''  not null,
    hit_segments       jsonb        default '[]' not null,
    hit_count          int          default 0   not null,
    acl_filtered_count int          default 0   not null,
    elapsed_ms         int          default 0   not null,
    prompt_tokens      int          default 0   not null,
    embedding_model    varchar(255) default ''  not null,
    feedback           varchar(16)  default ''  not null,
    create_time        timestamp    default CURRENT_TIMESTAMP not null,
    update_time        timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted         boolean      default false not null
);

comment on table adi_rag_query_log is 'RAG query log: one row per knowledge-base vector retrieval, written async | 检索日志：知识库向量检索一行一条，异步写入';
comment on column adi_rag_query_log.query_id is 'Unique id of this retrieval request | 本次检索请求唯一id';
comment on column adi_rag_query_log.user_uuid is 'Requesting user uuid (blank when context unavailable) | 发起检索的用户uuid';
comment on column adi_rag_query_log.kb_uuids is 'Knowledge base uuids passed to the vector filter | 本次检索的知识库uuid数组';
comment on column adi_rag_query_log.filter_expression is 'langchain4j Filter expression actually sent to the vector store (KB filter + ACL conditions) | 下发到向量库的过滤表达式（含ACL条件）';
comment on column adi_rag_query_log.hit_segments is 'Raw vector hits after ACL double-check, before expansion: [{kb_uuid,segment_id,doc_uuid,score}] | ACL双保险过滤后、展开前的原始命中';
comment on column adi_rag_query_log.hit_count is 'Number of hits kept | 保留命中数';
comment on column adi_rag_query_log.acl_filtered_count is 'Hits dropped by the ACL application-level double-check | 双保险剔除的命中数';
comment on column adi_rag_query_log.elapsed_ms is 'Retrieval elapsed time in ms | 检索耗时（毫秒）';
comment on column adi_rag_query_log.prompt_tokens is 'Prompt tokens of the following LLM call; 0 until wired up (T7-01) | 后续LLM调用的prompt token，暂为0';
comment on column adi_rag_query_log.embedding_model is 'Embedding model config when the query ran | 检索时的embedding模型配置';
comment on column adi_rag_query_log.feedback is 'User feedback: empty / PASS / FAIL | 用户反馈：空/PASS/FAIL';

create index if not exists idx_rag_query_log_time on adi_rag_query_log (create_time);
create index if not exists idx_rag_query_log_user on adi_rag_query_log (user_uuid);
create index if not exists idx_rag_query_log_kb on adi_rag_query_log using gin (kb_uuids);

drop trigger if exists trigger_rag_query_log_update_time on adi_rag_query_log;
create trigger trigger_rag_query_log_update_time
    before update
    on adi_rag_query_log
    for each row
execute procedure update_modified_column();
