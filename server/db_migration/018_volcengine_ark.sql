-- =============================================================================
-- 018_volcengine_ark.sql
-- 火山方舟（VolcEngine ARK）平台 + 模型配置（二开数据初始化脚本）
--
-- 【说明】本脚本是"数据配置"而非"结构变更"，故不并入 all_ddl.sql。
--   all_ddl.sql 定位是全新安装的结构快照，不应包含任何第三方 API Key 等
--   运行时凭据（各部署环境凭据不同，且本脚本明文含密钥，入结构快照有泄露风险）。
--
-- 【执行方式】API Key 通过 psql 变量注入，不在文件内留明文：
--   psql -v ON_ERROR_STOP=1 -v ark_api_key="ark-xxxxxxxx-xxxx-xxxx" -f 018_volcengine_ark.sql
--
-- 【幂等】可重复执行：平台按 name 判重，模型按 (platform, name) 判重。
--
-- 【注意】启用 embedding 模型前请先在 方舟控制台 -> API Key 管理 确认 Key 有效，
--   否则知识库索引会在向量化阶段报错。
-- =============================================================================

begin;

-- -----------------------------------------------------------------------------
-- 1. 平台：火山方舟 ARK
--    【关键修正 2026-09-15】端点路径必须为 /api/plan/v3（Agent Plan 专属端点），
--    不是 /api/v3。实测证据：
--      * 聊天模型 POST /api/plan/v3/chat/completions  -> 200
--      * 向量模型 POST /api/plan/v3/embeddings        -> 200（返回 2048 维向量）
--      * 旧 /api/v3 路径在嵌入/聊天上均返回 401/400，是路径错配的误报。
--    聊天与向量模型都走同一前缀 /api/plan/v3，langchain4j 默认 Content-Type
--    (application/json 不带 charset) 两种端点都能正常识别中文，无需改代码。
-- -----------------------------------------------------------------------------
insert into adi_model_platform (name, title, base_url, api_key, is_openai_api_compatible, remark)
select 'volcengine',
       '火山方舟 ARK',
       'https://ark.cn-beijing.volces.com/api/plan/v3',
       :'ark_api_key',
       true,
       '二开新增：火山方舟（字节跳动）。Agent Plan OpenAI 兼容端点 /api/plan/v3，支持 doubao-seed-2.0-lite、minimax-m3、doubao-embedding-vision 等'
where not exists (
    select 1 from adi_model_platform where name = 'volcengine' and is_deleted = false
);

-- 幂等更新：已存在则刷新 key / base_url / 兼容标记（方便换 Key 后重跑）
update adi_model_platform
set api_key                   = :'ark_api_key',
    base_url                  = 'https://ark.cn-beijing.volces.com/api/plan/v3',
    is_openai_api_compatible  = true,
    update_time               = current_timestamp
where name = 'volcengine'
  and is_deleted = false;

-- -----------------------------------------------------------------------------
-- 2. 大语言模型：doubao-seed-2.0-lite（均衡型全模态）
--    context_window / max_input_tokens / max_output_tokens 按官方公开规格填写，
--    如与实际开通版本不符，请在管理端「模型管理」页面直接调整。
-- -----------------------------------------------------------------------------
insert into adi_ai_model (name, title, type, platform, context_window, max_input_tokens,
                          max_output_tokens, input_types, properties, is_free, is_enable, remark)
select 'doubao-seed-2.0-lite',
       '豆包 Seed 2.0 Lite',
       'text',
       'volcengine',
       256000,
       253952,
       8192,
       'text',
       '{}'::jsonb,
       false,
       true,
       '二开新增：字节豆包 Seed 2.0 均衡型，支持文本/图片/音频/视频全模态理解。方舟 Model Name 如需带版本号可改为 doubao-seed-2-0-lite-260428'
where not exists (
    select 1 from adi_ai_model where platform = 'volcengine' and name = 'doubao-seed-2.0-lite' and is_deleted = false
);

-- -----------------------------------------------------------------------------
-- 3. 大语言模型：minimax-m3
-- -----------------------------------------------------------------------------
insert into adi_ai_model (name, title, type, platform, context_window, max_input_tokens,
                          max_output_tokens, input_types, properties, is_free, is_enable, remark)
select 'minimax-m3',
       'MiniMax M3',
       'text',
       'volcengine',
       200000,
       198000,
       8192,
       'text',
       '{}'::jsonb,
       false,
       true,
       '二开新增：MiniMax M3，经火山方舟 OpenAI 兼容端点调用。方舟控制台显示名为 MiniMax-M3'
where not exists (
    select 1 from adi_ai_model where platform = 'volcengine' and name = 'minimax-m3' and is_deleted = false
);

-- -----------------------------------------------------------------------------
-- 4. 向量模型：doubao-embedding-vision
--    方舟公开规格：默认稠密向量维度 2048，支持 1024 降维。
--    【重要】properties.dimension 必须与 application-*.yml 中 adi.embedding-model
--    指向的模型实际输出维度一致，否则 pgvector 写入会因维度不符报错。
--    【关键修正 2026-09-15】模型名必须用无后缀别名 doubao-embedding-vision。
--    实测带版本后缀的 doubao-embedding-vision-250615 在方舟该账号下返回 400
--    （InvalidParameter / could not parse JSON body），去掉后缀后 200 正常返回
--    2048 维向量。故此处注册名使用 doubao-embedding-vision。
-- -----------------------------------------------------------------------------
insert into adi_ai_model (name, title, type, platform, context_window, max_input_tokens,
                          max_output_tokens, input_types, properties, is_free, is_enable, remark)
select 'doubao-embedding-vision',
       '豆包向量化 Vision',
       'embedding',
       'volcengine',
       0,
       128000,
       0,
       'text',
       '{"dimension": 2048}'::jsonb,
       false,
       true,
       '二开新增：豆包多模态向量化。默认 2048 维，可在 properties.dimension 改为 1024 降维（需同步清空并重建向量库）。模型名用无后缀别名 doubao-embedding-vision（带版本后缀在该账号下报错）'
where not exists (
    select 1 from adi_ai_model where platform = 'volcengine' and name = 'doubao-embedding-vision' and is_deleted = false
);

commit;

-- =============================================================================
-- 执行后自检
-- =============================================================================
\echo '--- platform ---'
select name, base_url, is_openai_api_compatible,
       case when coalesce(api_key, '') <> '' then 'KEY_SET(' || length(api_key) || ' chars)' else 'EMPTY' end as key_status
from adi_model_platform
where name = 'volcengine' and is_deleted = false;

\echo '--- models ---'
select name, type, context_window, properties, is_enable
from adi_ai_model
where platform = 'volcengine' and is_deleted = false
order by id;
