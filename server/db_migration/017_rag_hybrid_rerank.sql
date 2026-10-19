-- 017_rag_hybrid_rerank.sql
-- RAG 检索增强：混合检索（关键词 bigram 召回 + RRF 融合）与重排（rerank）
--
-- 依赖：016_skill.sql 之后执行
-- 说明：
--   1) 关键词路由应用层做中文 bigram 切分，只写入 search_text；search_tsv 由 PG 内置触发器
--      tsvector_update_trigger 自动维护，应用层不直接操作 tsvector，避免切分口径不一致。
--   2) 使用 simple 配置（不做词干化、不过滤停用词），配合 bigram 对中文生效。
--   3) 知识库级检索配置：混合检索开关、RRF k、rerank 开关与模型绑定。

-- ========== 1. 分段表：全文检索列 ==========

alter table adi_document_segment add column if not exists search_text text default '' not null;
comment on column adi_document_segment.search_text is
  'Bigram-tokenized content for keyword retrieval (written by app layer; search_tsv is derived from it)';

alter table adi_document_segment add column if not exists search_tsv tsvector;
comment on column adi_document_segment.search_tsv is
  'Full-text vector auto-maintained by trigger (config=pg_catalog.simple). Query with to_tsquery(''simple'', ''a|b|c'') - OR semantics, never plainto_tsquery (AND semantics returns 0 rows for long queries)';

-- 触发器：search_text 变化时自动重算 search_tsv
drop trigger if exists trg_segment_search_tsv on adi_document_segment;
create trigger trg_segment_search_tsv
    before insert or update of search_text
    on adi_document_segment
    for each row
-- 注意：第二个参数是 regconfig，必须写全限定名 'pg_catalog.simple'。
-- 写 'simple' → 运行时报 `text search configuration name "simple" must be schema-qualified`；
-- 写 'public.simple' → 运行时报 `text search configuration "public.simple" does not exist`。
-- 两种写法 CREATE TRIGGER 阶段都不报错，要到插入/更新时才炸，会让分段入库整条链路失败——务必实测。
execute function tsvector_update_trigger(search_tsv, 'pg_catalog.simple', search_text);

-- GIN 索引（部分索引：只索引未删除且启用的分段）
drop index if exists idx_segment_search_tsv;
create index idx_segment_search_tsv
    on adi_document_segment using gin (search_tsv)
    where is_deleted = false and is_enabled = true;

-- ========== 2. 知识库表：检索配置 ==========

alter table adi_knowledge_base add column if not exists retrieve_hybrid_enabled boolean default false not null;
comment on column adi_knowledge_base.retrieve_hybrid_enabled is
  'Enable hybrid retrieval: vector + keyword (bigram) fused by RRF. false = vector-only (legacy behavior)';

alter table adi_knowledge_base add column if not exists retrieve_rrf_k int default 60 not null;
comment on column adi_knowledge_base.retrieve_rrf_k is
  'RRF fusion constant k (score = sum(1/(k+rank))). Larger k flattens rank differences';

alter table adi_knowledge_base add column if not exists retrieve_keyword_limit int default 20 not null;
comment on column adi_knowledge_base.retrieve_keyword_limit is
  'Max results fetched from the keyword path before RRF fusion';

alter table adi_knowledge_base add column if not exists retrieve_rerank_enabled boolean default false not null;
comment on column adi_knowledge_base.retrieve_rerank_enabled is
  'Enable rerank stage after fusion. Requires a usable rerank model';

alter table adi_knowledge_base add column if not exists retrieve_rerank_model_id bigint default 0 not null;
comment on column adi_knowledge_base.retrieve_rerank_model_id is
  'Foreign key to adi_ai_model.id where type=rerank. 0 means use the first enabled rerank model';

alter table adi_knowledge_base add column if not exists retrieve_rerank_top_n int default 0 not null;
comment on column adi_knowledge_base.retrieve_rerank_top_n is
  'Keep only top-N after rerank; 0 means keep all (no truncation)';

alter table adi_knowledge_base add column if not exists retrieve_rerank_min_score numeric(3, 2) default 0.0 not null;
comment on column adi_knowledge_base.retrieve_rerank_min_score is
  'Drop reranked contents whose normalized score is below this threshold; 0.0 means keep all';

-- ========== 3. 存量数据回填 ==========
-- 新列 search_text 为空串，search_tsv 为 NULL，检索时自然不命中关键词路（等价于旧行为）。
-- 存量分段如需启用混合检索，须由应用层重跑一次「重建关键词索引」（把 content 切分后写回 search_text）。
-- 增量分段在入库时由应用层直接写入 search_text，无需手工回填。
