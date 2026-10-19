-- =============================================================================
-- 029_qianwen_maas_provider.sql
-- 千问 MaaS 兼容平台 + 模型配置（二开数据初始化脚本）
--
-- 【说明】本脚本是"数据配置"而非"结构变更"，故结构部分（rerank_url 列）并入
--   all_ddl.sql，行为幂等。本文件负责把平台与模型注册进数据库。
--
-- 【执行方式】API Key 通过 psql 变量注入，不在文件内留明文：
--   psql -v ON_ERROR_STOP=1 -v qianwen_api_key="sk-xxxx" -f 029_qianwen_maas_provider.sql
--   直接对运行中的库执行即视为"记录到数据库"。
--
-- 【幂等】可重复执行：列用 IF NOT EXISTS；平台按 name 判重，模型按 (platform, name) 判重。
--
-- 【端点说明·重要】千问 MaaS 的 embedding 与 rerank 分处两个不同 base path：
--   * embedding: https://maas.qianwenaiapi.com/compatible-mode/v1/embeddings  (1024 维，实测)
--   * rerank:    https://maas.qianwenaiapi.com/compatible-api/v1/reranks      (路径为 /reranks 复数)
--   因此平台 base_url 指向 embedding 端点（compatible-mode/v1），而 rerank 走独立的
--   rerank_url 列（compatible-api/v1/reranks）。OpenAiCompatibleScoringModel 已支持：
--   rerank_url 非空时直接用完整 URL，否则回退 base_url + /rerank（兼容 SiliconFlow 等）。
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 0. 结构：adi_model_platform 增加 rerank_url 列（rerank 端点可独立于 base_url）
--    （该列定义同时并入 all_ddl.sql，保证全新安装结构一致）
-- -----------------------------------------------------------------------------
alter table adi_model_platform
    add column if not exists rerank_url varchar(500) default '' not null;

comment on column adi_model_platform.rerank_url is
    '专用 rerank 端点完整 URL；为空时回退 base_url + /rerank。用于 embedding 与 rerank 不在同一 base path 的提供商（如千问 MaaS：embedding 在 /compatible-mode/v1，rerank 在 /compatible-api/v1/reranks）';

-- -----------------------------------------------------------------------------
-- 1. 平台：千问 MaaS
--    base_url 指向 embedding 端点（用户提供的 compatible-mode/v1）；
--    rerank_url 指向 rerank 端点（compatible-api/v1/reranks，复数路径）。
-- -----------------------------------------------------------------------------
insert into adi_model_platform (name, title, base_url, api_key, is_openai_api_compatible, rerank_url, remark)
select 'qianwen',
       '千问 MaaS',
       'https://maas.qianwenaiapi.com/compatible-mode/v1',
       :'qianwen_api_key',
       true,
       'https://maas.qianwenaiapi.com/compatible-api/v1/reranks',
       '二开新增：千问 MaaS 兼容平台，提供 qwen3.7-text-embedding(1024维) 与 qwen3.7-text-rerank。embedding 在 /compatible-mode/v1，rerank 在 /compatible-api/v1/reranks（路径复数）。'
where not exists (
    select 1 from adi_model_platform where name = 'qianwen' and is_deleted = false
);

-- 幂等更新：已存在则刷新 key / base_url / 兼容标记 / rerank_url
update adi_model_platform
set api_key                  = :'qianwen_api_key',
    base_url                 = 'https://maas.qianwenaiapi.com/compatible-mode/v1',
    is_openai_api_compatible = true,
    rerank_url               = 'https://maas.qianwenaiapi.com/compatible-api/v1/reranks',
    update_time              = current_timestamp
where name = 'qianwen'
  and is_deleted = false;

-- -----------------------------------------------------------------------------
-- 2. 向量模型：qwen3.7-text-embedding（输出 1024 维，已实测）
--    注：用户提到的 "qwen3-vl-embedding" 不在该平台模型列表内，可用 embedding 实为
--        qwen3.7-text-embedding（另有 qwen3.7-text-embedding-flash）。此处注册实测可用的。
--    properties.dimension 必须与向量库实际输出维度一致，否则 pgvector 写入会因维度不符报错。
-- -----------------------------------------------------------------------------
insert into adi_ai_model (name, title, type, platform, context_window, max_input_tokens,
                          max_output_tokens, input_types, properties, is_free, is_enable, remark)
select 'qwen3.7-text-embedding',
       '千问向量化',
       'embedding',
       'qianwen',
       0,
       8192,
       0,
       'text',
       '{"dimension": 1024}'::jsonb,
       false,
       true,
       '二开新增：千问 MaaS 文本向量化，输出 1024 维（实测 /embeddings 返回 1024 维向量）。'
where not exists (
    select 1 from adi_ai_model where platform = 'qianwen' and name = 'qwen3.7-text-embedding' and is_deleted = false
);

-- -----------------------------------------------------------------------------
-- 3. 重排序模型：qwen3.7-text-rerank
--    由平台 rerank_url（/compatible-api/v1/reranks）指向，OpenAiCompatibleScoringModel 调用。
--    响应为 Cohere 形状 {"results":[{"index","relevance_score"}]}，解析兼容。
-- -----------------------------------------------------------------------------
insert into adi_ai_model (name, title, type, platform, context_window, max_input_tokens,
                          max_output_tokens, input_types, properties, is_free, is_enable, remark)
select 'qwen3.7-text-rerank',
       '千问重排序',
       'rerank',
       'qianwen',
       0,
       30000,
       0,
       'text',
       '{}'::jsonb,
       false,
       true,
       '二开新增：千问 MaaS 重排序，单文档最大 30000 token。端点 /compatible-api/v1/reranks（复数），由平台 rerank_url 指定。'
where not exists (
    select 1 from adi_ai_model where platform = 'qianwen' and name = 'qwen3.7-text-rerank' and is_deleted = false
);

commit;

-- =============================================================================
-- 执行后自检
-- =============================================================================
\echo '--- platform ---'
select name,
       base_url,
       rerank_url,
       is_openai_api_compatible,
       case when coalesce(api_key, '') <> '' then 'KEY_SET(' || length(api_key) || ' chars)' else 'EMPTY' end as key_status
from adi_model_platform
where name = 'qianwen' and is_deleted = false;

\echo '--- models ---'
select name, type, properties, is_enable
from adi_ai_model
where platform = 'qianwen' and is_deleted = false
order by id;
