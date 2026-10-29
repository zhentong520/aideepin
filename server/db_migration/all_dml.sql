-- ============================================================
-- Initial Data: seed data inserts
-- ============================================================

-- 管理员账号：catkeeper@aideepin.com  密码：123456
INSERT INTO adi_user (name, password, uuid, email, user_status, is_admin)
VALUES ('catkeeper', '$2a$10$z44gncmQk6xCBCeDx55gMe1Zc8uYtOKcoT4/HE2F92VcF7wP2iquG',
        replace(gen_random_uuid()::text, '-', ''), 'catkeeper@aideepin.com', 2, true);

-- 配置信息
-- https://api-docs.deepseek.com/zh-cn/
INSERT INTO adi_sys_config (name, value)
VALUES ('google_setting',
        '{"url":"https://www.googleapis.com/customsearch/v1","key":"","cx":""}');
INSERT INTO adi_sys_config (name, value)
VALUES ('request_text_rate_limit', '{"times":24,"minutes":3}');
INSERT INTO adi_sys_config (name, value)
VALUES ('request_image_rate_limit', '{"times":6,"minutes":3}');
INSERT INTO adi_sys_config (name, value)
VALUES ('character_max_num', '50');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_token_daily', '10000');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_token_monthly', '200000');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_request_daily', '150');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_request_monthly', '3000');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_image_daily', '30');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_image_monthly', '300');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_qa_ask_daily', '50');
INSERT INTO adi_sys_config (name, value)
VALUES ('quota_by_qa_item_monthly', '100');

-- 文件存储位置，默认存储到本地
-- store_location: 1表示存储到本地,2表示存储到阿里云oss
INSERT INTO adi_sys_config (name, value)
VALUES ('storage_location', '1');
-- endpoint: 如：oss-cn-hangzhou.aliyuncs.com
INSERT INTO adi_sys_config (name, value)
VALUES ('storage_location_ali_oss', '{"access_key_id":"","access_key_secret":"","endpoint":"","bucket_name":""}');

-- ASR settings
-- Supported platforms and models:
--   siliconflow  -> FunAudioLLM/SenseVoiceSmall
--   dashscope    -> paraformer-v2
--   openai       -> gpt-4o-mini-transcribe
-- Max file size: 10MB, max recording duration: 60s (TODO)
INSERT INTO adi_sys_config (name, value)
VALUES ('asr_setting',
        '{"model_name":"FunAudioLLM/SenseVoiceSmall","platform":"siliconflow","max_record_duration":60,"max_file_size":10485760}');

-- TTS settings
-- Supported platforms and models:
--   siliconflow  -> FunAudioLLM/CosyVoice2-0.5B
--   dashscope    -> cosyvoice-v3-flash
--   openai       -> gpt-4o-mini-tts
-- synthesizer_side: "client" uses browser TTS (free), "server" uses models (paid)
INSERT INTO adi_sys_config (name, value)
VALUES ('tts_setting', '{"synthesizer_side":"client","model_name":"","platform":""}');
-- Uncomment one of the following to enable server-side TTS with a specific model:
-- INSERT INTO adi_sys_config (name, value)
-- VALUES ('tts_setting', '{"synthesizer_side":"server","model_name":"cosyvoice-v3-flash","platform":"dashscope"}');
-- INSERT INTO adi_sys_config (name, value)
-- VALUES ('tts_setting', '{"synthesizer_side":"server","model_name":"gpt-4o-mini-tts","platform":"openai"}');
-- INSERT INTO adi_sys_config (name, value)
-- VALUES ('tts_setting', '{"synthesizer_side":"server","model_name":"FunAudioLLM/CosyVoice2-0.5B","platform":"siliconflow"}');

-- LLM streaming timeout (T6-08, 2026-10-20): hot-configurable via admin system config page.
-- Unset/absent row falls back to the built-in default of 60s.
INSERT INTO adi_sys_config (name, value)
SELECT 'llm_streaming_timeout_seconds', '60'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'llm_streaming_timeout_seconds' AND is_deleted = false
);

-- Multi-Agent hot switches (T7-02/T7-03, 2026-10-20): delegate_agent and Orchestrator node.
-- Unset/absent row falls back to application.yml adi.agent.* defaults (enabled).
INSERT INTO adi_sys_config (name, value)
SELECT 'agent_delegation_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'agent_delegation_enabled' AND is_deleted = false
);
INSERT INTO adi_sys_config (name, value)
SELECT 'agent_orchestrator_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'agent_orchestrator_enabled' AND is_deleted = false
);

-- 模型平台
insert into adi_model_platform (name, title, base_url)
values ('openai', 'OpenAI', 'https://api.openai.com/v1');
insert into adi_model_platform (name, title, base_url)
values ('deepseek', 'DeepSeek', 'https://api.deepseek.com/v1');
insert into adi_model_platform (name, title, base_url)
values ('dashscope', 'DashScope', 'https://dashscope.aliyuncs.com/api/v1');
insert into adi_model_platform (name, title, base_url)
values ('siliconflow', 'SiliconFlow', 'https://api.siliconflow.cn/v1');
insert into adi_model_platform (name, title, base_url)
values ('ollama', 'ollama', 'http://localhost:11434');
-- SiliconFlow text models are compatible with OpenAI API; this row tests whether dynamically created platforms/models use OpenAiCompatibleLLMService correctly
insert into adi_model_platform (name, title, base_url, is_openai_api_compatible)
values ('openai-compatible-platform-test', 'OpenAI-Compatible Platform', 'https://api.siliconflow.cn/v1', true);
-- 火山方舟 ARK（二开新增）
-- 端点必须使用 Agent Plan 专属前缀 /api/plan/v3，而非 /api/v3：
--   聊天  POST /api/plan/v3/chat/completions -> 200
--   向量  POST /api/plan/v3/embeddings       -> 200（2048 维）
--   /api/v3 在聊天与向量上均返回 401/400。
-- api_key 不在初始化脚本中写入，请在管理端「模型平台」页面填写，避免密钥入库泄露。
insert into adi_model_platform (name, title, base_url, is_openai_api_compatible)
values ('volcengine', '火山方舟 ARK', 'https://ark.cn-beijing.volces.com/api/plan/v3', true);
-- 千问 MaaS 兼容平台（二开新增）
-- embedding 与 rerank 分处两个不同 base path：
--   embedding: https://maas.qianwenaiapi.com/compatible-mode/v1  (base_url 指向此处)
--   rerank:    https://maas.qianwenaiapi.com/compatible-api/v1/reranks  (路径复数，由 rerank_url 列指向)
-- api_key 不在初始化脚本中写入，请在管理端「模型平台」页面填写（或执行 029 迁移注入）。
-- rerank_url 列定义见 all_ddl.sql；OpenAiCompatibleScoringModel 已支持 rerank_url 优先于 base_url + /rerank。
insert into adi_model_platform (name, title, base_url, is_openai_api_compatible, rerank_url)
values ('qianwen', '千问 MaaS', 'https://maas.qianwenaiapi.com/compatible-mode/v1', true,
        'https://maas.qianwenaiapi.com/compatible-api/v1/reranks');

-- 火山方舟 — 大语言模型
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          input_types, properties, remark, is_enable)
VALUES ('doubao-seed-2.0-lite', '豆包 Seed 2.0 Lite', 'text', 'volcengine', 256000, 253952, 8192, 'text', '{}',
        '二开新增：字节豆包 Seed 2.0 均衡型，支持文本/图片/音频/视频全模态理解。方舟 Model Name 如需带版本号可改为 doubao-seed-2-0-lite-260428',
        true);
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          input_types, properties, remark, is_enable)
VALUES ('minimax-m3', 'MiniMax M3', 'text', 'volcengine', 200000, 198000, 8192, 'text', '{}',
        '二开新增：MiniMax M3，经火山方舟 OpenAI 兼容端点调用。方舟控制台显示名为 MiniMax-M3', true);

-- 大语言模型
-- https://api-docs.deepseek.com/zh-cn/quick_start/pricing
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, is_reasoner,
                          is_thinking_closable, is_enable)
VALUES ('deepseek-v4-flash', 'DeepSeek-V4-Flash', 'text', 'deepseek', 1000000, 980000, 384000, 'text,json_object', true, true,
        false);

-- https://developers.openai.com/api/docs/models/gpt-5-mini (superseded by gpt-5.4-mini)
-- INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
--                           response_format_types, remark,
--                           is_enable)
-- VALUES ('gpt-5-mini', 'gpt-5-mini', 'text', 'openai', 400000, 272000, 128000, 'text,json_object',
--         'GPT-5 mini is a faster, more cost-efficient version of GPT-5. It''s great for well-defined tasks and precise prompts.',
--         false);

-- https://developers.openai.com/api/docs/models/gpt-5.4-mini
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, remark,
                          is_enable)
VALUES ('gpt-5.4-mini', 'gpt-5.4-mini', 'text', 'openai', 400000, 272000, 128000, 'text,json_object',
        'GPT-5.4 mini brings the strengths of GPT-5.4 to a faster, more efficient model designed for high-volume workloads.',
        false);


INSERT INTO adi_ai_model (name, title, type, platform, properties, is_enable)
VALUES ('gpt-image-2', 'GPT-Image-2', 'image', 'openai', '{"max_images": 10}', false);
-- OpenAI 语音识别 (ASR)
INSERT INTO adi_ai_model (name, title, type, platform, input_types, is_enable)
VALUES ('gpt-4o-mini-transcribe', 'OpenAI-ASR', 'asr', 'openai', 'audio', false);
-- OpenAI 语音合成 (TTS)
-- gpt-4o-mini-tts supports all 13 voices
INSERT INTO adi_ai_model (name, title, type, platform, input_types, properties, is_enable)
VALUES ('gpt-4o-mini-tts', 'OpenAI-TTS', 'tts', 'openai', 'text', '{
  "voices": [
    {"name": "Alloy", "remark": "Alloy", "param_name": "alloy", "lang": "multilingual"},
    {"name": "Ash", "remark": "Ash", "param_name": "ash", "lang": "multilingual"},
    {"name": "Ballad", "remark": "Ballad", "param_name": "ballad", "lang": "multilingual"},
    {"name": "Coral", "remark": "Coral", "param_name": "coral", "lang": "multilingual"},
    {"name": "Echo", "remark": "Echo", "param_name": "echo", "lang": "multilingual"},
    {"name": "Fable", "remark": "Fable", "param_name": "fable", "lang": "multilingual"},
    {"name": "Nova", "remark": "Nova", "param_name": "nova", "lang": "multilingual"},
    {"name": "Onyx", "remark": "Onyx", "param_name": "onyx", "lang": "multilingual"},
    {"name": "Sage", "remark": "Sage", "param_name": "sage", "lang": "multilingual"},
    {"name": "Shimmer", "remark": "Shimmer", "param_name": "shimmer", "lang": "multilingual"},
    {"name": "Verse", "remark": "Verse", "param_name": "verse", "lang": "multilingual"},
    {"name": "Marin", "remark": "Marin", "param_name": "marin", "lang": "multilingual"},
    {"name": "Cedar", "remark": "Cedar", "param_name": "cedar", "lang": "multilingual"}
  ]
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-3-small', 'OpenAI-Embedding-Small', 'embedding', 'openai', 8191, '{
  "dimension": 1536
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-3-large', 'OpenAI-Embedding-Large', 'embedding', 'openai', 8191, '{
  "dimension": 3072
}', false);
-- https://help.aliyun.com/zh/dashscope/developer-reference/model-introduction?spm=a2c4g.11186623.0.i39
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, is_support_web_search, is_reasoner,
                          is_thinking_closable, is_enable)
VALUES ('qwen-turbo', 'Qwen-Turbo', 'text', 'dashscope', 131072, 98304, 16384, 'text,json_object', true, true, true,
        false);
-- 图片识别
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens, input_types,
                          is_enable)
VALUES ('qwen2-vl-7b-instruct', 'Qwen-Vision', 'vision', 'dashscope', 32768, 16384, 16384, 'text,image', false);
-- 通义万相-文生图 wan2.7-image
INSERT INTO adi_ai_model (name, title, type, platform, properties, is_enable)
VALUES ('wan2.7-image', 'Qwen-Image', 'image', 'dashscope', '{"max_images": 4, "sizes": [{"value":"1K","label":"1K · 1024×1024"},{"value":"2K","label":"2K · 2048×2048"}]}', false);
-- 通义万相-切换背景
INSERT INTO adi_ai_model (name, title, type, platform, input_types, is_enable)
VALUES ('wanx-background-generation-v2', 'Qwen-Background', 'image', 'dashscope', 'text,image', false);
-- 通义千问-向量（可选模型名：text-embedding-v1,text-embedding-v2,text-embedding-v3）
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-v1', 'Qwen-Embedding-V1', 'embedding', 'dashscope', 2048, '{
  "dimension": 1536
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, is_enable)
VALUES ('text-embedding-v3', 'Qwen-Embedding-V3', 'embedding', 'dashscope', 8192, '{
  "dimension": 1024
}', false);
-- 火山方舟 — 向量模型（adi.embedding-model 默认值，必须保持启用）
-- 注意事项：
--   * properties.dimension 必须与 application-*.yml 中 adi.embedding-model 指向模型的实际输出维度一致，
--     否则 pgvector 写入会因维度不符报错。
--   * 模型名必须使用无后缀别名 doubao-embedding-vision；带版本后缀的
--     doubao-embedding-vision-250615 在该账号下方舟返回 400（InvalidParameter）。
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, remark, is_enable)
VALUES ('doubao-embedding-vision', '豆包向量化 Vision', 'embedding', 'volcengine', 128000, '{
  "dimension": 2048
}', '二开新增：豆包多模态向量化，默认 2048 维。降维请改 properties.dimension 并同步清空重建向量库', true);
-- 千问 MaaS — 向量模型（用户提到的 qwen3-vl-embedding 不在该平台模型列表，实际可用为 qwen3.7-text-embedding，实测 1024 维）
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, remark, is_enable)
VALUES ('qwen3.7-text-embedding', '千问向量化', 'embedding', 'qianwen', 8192, '{
  "dimension": 1024
}', '二开新增：千问 MaaS 文本向量化，输出 1024 维（实测 /embeddings 返回 1024 维）', true);
-- 千问 MaaS — 重排序模型（端点 /compatible-api/v1/reranks 复数，由平台 rerank_url 指向）
INSERT INTO adi_ai_model (name, title, type, platform, max_input_tokens, properties, remark, is_enable)
VALUES ('qwen3.7-text-rerank', '千问重排序', 'rerank', 'qianwen', 30000, '{}',
        '二开新增：千问 MaaS 重排序，单文档最大 30000 token；端点 /compatible-api/v1/reranks（复数）', true);
-- 语音识别
-- paraformer-v2 只支持公网可访问的音频文件，如需要传输本地音频文件，请激活使用下面硅基流动的SenseVoiceSmall
INSERT INTO adi_ai_model (name, title, type, platform, input_types, is_enable)
VALUES ('paraformer-v2', 'Qwen-ASR', 'asr', 'dashscope', 'audio', false);
-- 语音合成
INSERT INTO adi_ai_model (name, title, type, platform, input_types, properties, is_enable)
VALUES ('cosyvoice-v3-flash', 'Qwen-TTS', 'tts', 'dashscope', 'text', '{
  "voices": [
    {
      "name": "龙安洋",
      "remark": "阳光大男孩",
      "param_name": "longanyang",
      "lang": "中、英"
    },
    {
      "name": "龙安欢",
      "remark": "欢脱元气女",
      "param_name": "longanhuan",
      "lang": "中、英"
    },
    {
      "name": "龙呼呼",
      "remark": "天真烂漫女童",
      "param_name": "longhuhu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙泡泡",
      "remark": "飞天泡泡音",
      "param_name": "longpaopao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙杰力豆",
      "remark": "阳光顽皮男",
      "param_name": "longjielidou_v3",
      "lang": "中、英"
    },
    {
      "name": "龙仙",
      "remark": "豪放可爱女",
      "param_name": "longxian_v3",
      "lang": "中、英"
    },
    {
      "name": "龙铃",
      "remark": "稚气呆板女",
      "param_name": "longling_v3",
      "lang": "中、英"
    },
    {
      "name": "龙闪闪",
      "remark": "戏剧化童声",
      "param_name": "longshanshan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙牛牛",
      "remark": "阳光男童声",
      "param_name": "longniuniu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙嘉欣",
      "remark": "优雅粤语女",
      "param_name": "longjiaxin_v3",
      "lang": "中（粤语）、英"
    },
    {
      "name": "龙嘉怡",
      "remark": "知性粤语女",
      "param_name": "longjiayi_v3",
      "lang": "中（粤语）、英"
    },
    {
      "name": "龙安粤",
      "remark": "欢脱粤语男",
      "param_name": "longanyue_v3",
      "lang": "中（粤语）、英"
    },
    {
      "name": "龙老铁",
      "remark": "东北直率男",
      "param_name": "longlaotie_v3",
      "lang": "中（东北话）、英"
    },
    {
      "name": "龙陕哥",
      "remark": "原味陕北男",
      "param_name": "longshange_v3",
      "lang": "中（陕西话）、英"
    },
    {
      "name": "龙安闽",
      "remark": "清纯萝莉女",
      "param_name": "longanmin_v3",
      "lang": "中（闽南话）、英"
    },
    {
      "name": "龙飞",
      "remark": "热血磁性男",
      "param_name": "longfei_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应笑",
      "remark": "清甜推销女",
      "param_name": "longyingxiao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应询",
      "remark": "年轻青涩男",
      "param_name": "longyingxun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应静",
      "remark": "低调冷静女",
      "param_name": "longyingjing_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应聆",
      "remark": "温和共情女",
      "param_name": "longyingling_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应桃",
      "remark": "温柔淡定女",
      "param_name": "longyingtao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙小淳",
      "remark": "知性积极女",
      "param_name": "longxiaochun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙小夏",
      "remark": "沉稳权威女",
      "param_name": "longxiaoxia_v3",
      "lang": "中、英"
    },
    {
      "name": "YUMI",
      "remark": "正经青年女",
      "param_name": "longyumi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安昀",
      "remark": "居家暖男",
      "param_name": "longanyun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安温",
      "remark": "优雅知性女",
      "param_name": "longanwen_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安莉",
      "remark": "利落从容女",
      "param_name": "longanli_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安朗",
      "remark": "清爽利落男",
      "param_name": "longanlang_v3",
      "lang": "中、英"
    },
    {
      "name": "龙应沐",
      "remark": "优雅知性女",
      "param_name": "longyingmu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安台",
      "remark": "嗲甜台湾女",
      "param_name": "longantai_v3",
      "lang": "中、英"
    },
    {
      "name": "龙华",
      "remark": "元气甜美女",
      "param_name": "longhua_v3",
      "lang": "中、英"
    },
    {
      "name": "龙橙",
      "remark": "智慧青年男",
      "param_name": "longcheng_v3",
      "lang": "中、英"
    },
    {
      "name": "龙泽",
      "remark": "温暖元气男",
      "param_name": "longze_v3",
      "lang": "中、英"
    },
    {
      "name": "龙哲",
      "remark": "呆板大暖男",
      "param_name": "longzhe_v3",
      "lang": "中、英"
    },
    {
      "name": "龙颜",
      "remark": "温暖春风女",
      "param_name": "longyan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙星",
      "remark": "温婉邻家女",
      "param_name": "longxing_v3",
      "lang": "中、英"
    },
    {
      "name": "龙天",
      "remark": "磁性理智男",
      "param_name": "longtian_v3",
      "lang": "中、英"
    },
    {
      "name": "龙婉",
      "remark": "细腻柔声女",
      "param_name": "longwan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙嫱",
      "remark": "浪漫风情女",
      "param_name": "longqiang_v3",
      "lang": "中、英"
    },
    {
      "name": "龙菲菲",
      "remark": "甜美娇气女",
      "param_name": "longfeifei_v3",
      "lang": "中、英"
    },
    {
      "name": "龙浩",
      "remark": "多情忧郁男",
      "param_name": "longhao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安柔",
      "remark": "温柔闺蜜女",
      "param_name": "longanrou_v3",
      "lang": "中、英"
    },
    {
      "name": "龙寒",
      "remark": "温暖痴情男",
      "param_name": "longhan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安智",
      "remark": "睿智轻熟男",
      "param_name": "longanzhi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安灵",
      "remark": "思维灵动女",
      "param_name": "longanling_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安雅",
      "remark": "高雅气质女",
      "param_name": "longanya_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安亲",
      "remark": "亲和活泼女",
      "param_name": "longanqin_v3",
      "lang": "中、英"
    },
    {
      "name": "龙妙",
      "remark": "抑扬顿挫女",
      "param_name": "longmiao_v3",
      "lang": "中、英"
    },
    {
      "name": "龙三叔",
      "remark": "沉稳质感男",
      "param_name": "longsanshu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙媛",
      "remark": "温暖治愈女",
      "param_name": "longyuan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙悦",
      "remark": "温暖磁性女",
      "param_name": "longyue_v3",
      "lang": "中、英"
    },
    {
      "name": "龙修",
      "remark": "博才说书男",
      "param_name": "longxiu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙楠",
      "remark": "睿智青年男",
      "param_name": "longnan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙婉君",
      "remark": "细腻柔声女",
      "param_name": "longwanjun_v3",
      "lang": "中、英"
    },
    {
      "name": "龙逸尘",
      "remark": "洒脱活力男",
      "param_name": "longyichen_v3",
      "lang": "中、英"
    },
    {
      "name": "龙老伯",
      "remark": "沧桑岁月爷",
      "param_name": "longlaobo_v3",
      "lang": "中、英"
    },
    {
      "name": "龙老姨",
      "remark": "烟火从容阿姨",
      "param_name": "longlaoyi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙机器",
      "remark": "呆萌机器人",
      "param_name": "longjiqi_v3",
      "lang": "中、英"
    },
    {
      "name": "龙猴哥",
      "remark": "经典猴哥",
      "param_name": "longhouge_v3",
      "lang": "中、英"
    },
    {
      "name": "龙黛玉",
      "remark": "娇率才女音",
      "param_name": "longdaiyu_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安燃",
      "remark": "活泼质感女",
      "param_name": "longanran_v3",
      "lang": "中、英"
    },
    {
      "name": "龙安宣",
      "remark": "经典直播女",
      "param_name": "longanxuan_v3",
      "lang": "中、英"
    },
    {
      "name": "龙硕",
      "remark": "博才干练男",
      "param_name": "longshuo_v3",
      "lang": "中、英"
    },
    {
      "name": "龙书",
      "remark": "沉稳青年男",
      "param_name": "longshu_v3",
      "lang": "中、英"
    },
    {
      "name": "Bella3.0",
      "remark": "精准干练女",
      "param_name": "loongbella_v3",
      "lang": "中、英"
    }
  ]
}', false);
INSERT INTO adi_ai_model (name, title, type, platform, context_window, max_input_tokens, max_output_tokens,
                          response_format_types, is_enable)
VALUES ('qwen3:8b', 'Qwen3-8B(Ollama)', 'text', 'ollama', 131072, 98304, 16384, 'text,json_object', false);
INSERT INTO adi_ai_model (name, title, type, platform, response_format_types, remark, is_free, is_enable)
VALUES ('THUDM/GLM-Z1-9B-0414', 'SiliconFlow-GLM-Z1-9B', 'text', 'siliconflow', 'text,json_object',
        'GLM-Z1-9B-0414 是 GLM 系列的小型模型，仅有 90 亿参数，但保持了开源传统的同时展现出惊人的能力。', true, false);
INSERT INTO adi_ai_model (name, title, type, platform, input_types, remark, is_free, is_enable)
VALUES ('THUDM/GLM-4.1V-9B-Thinking', 'SiliconFlow-Vision', 'vision', 'siliconflow', 'text,image',
        'GLM-4.1V-9B-Thinking 是由智谱 AI 和清华大学 KEG 实验室联合发布的一款开源视觉语言模型（VLM），专为处理复杂的多模态认知任务而设计。该模型基于 GLM-4-9B-0414 基础模型，通过引入“思维链”（Chain-of-Thought）推理机制和采用强化学习策略，显著提升了其跨模态的推理能力和稳定性。作为一个 9B 参数规模的轻量级模型，它在部署效率和性能之间取得了平衡，在 28 项权威评测基准中，有 18 项的表现持平甚至超越了 72B 参数规模的 Qwen-2.5-VL-72B。该模型不仅在图文理解、数学科学推理、视频理解等任务上表现卓越，还支持高达 4K 分辨率的图像和任意宽高比输入',
        true, false);

INSERT INTO adi_ai_model (name, title, type, platform, properties, remark, is_free, is_enable)
VALUES ('Kwai-Kolors/Kolors', 'SiliconFlow-Image', 'image', 'siliconflow', '{
  "image_sizes": [
    "1024x1024",
    "960x1280",
    "768x1024",
    "720x1440",
    "720x1280"
  ],
  "max_images": 4
}',
        'Kolors 是由快手 Kolors 团队开发的基于潜在扩散的大规模文本到图像生成模型。该模型通过数十亿文本-图像对的训练，在视觉质量、复杂语义准确性以及中英文字符渲染方面展现出显著优势。它不仅支持中英文输入，在理解和生成中文特定内容方面也表现出色。',
        true, false);

-- Qwen/Qwen-Image 为收费模型，详细评估后再判断是否要启用
-- INSERT INTO adi_ai_model (name, title, type, platform, properties, remark, is_free, is_enable)
-- VALUES ('Qwen/Qwen-Image', '硅基流动-文生图（qwen）', 'image', 'siliconflow', '{
--   "image_size": [
--     "1328x1328",
--     "1664x928",
--     "928x1664",
--     "1472x1140",
--     "1140x1472",
--     "1584x1056",
--     "1056x1584"
--   ]
-- }',
--         'Qwen-Image 是由阿里巴巴通义千问团队发布的图像生成基础模型，拥有 200 亿参数。该模型在复杂的文本渲染和精确的图像编辑方面取得了显著进展，尤其擅长生成包含高保真度中英文文字的图像。', false, false);

-- test data
INSERT INTO adi_ai_model (name, title, type, platform, response_format_types, is_enable)
VALUES ('THUDM/GLM-Z1-9B-0414', 'openai-compatible-model-test', 'text', 'openai-compatible-platform-test',
        'text,json_object', false);
-- test data end


-- 语音识别
INSERT INTO adi_ai_model (name, title, type, platform, input_types, response_format_types, is_free, is_enable)
VALUES ('FunAudioLLM/SenseVoiceSmall', 'SiliconFlow-ASR', 'asr', 'siliconflow', 'audio', 'text,json_object', true,
        true);
-- 语音合成
INSERT INTO adi_ai_model (name, title, type, platform, input_types, properties, is_enable)
values ('FunAudioLLM/CosyVoice2-0.5B', 'SiliconFlow-TTS', 'tts', 'siliconflow', 'text', '{
  "voices": [
    {
      "name": "fnlp/MOSS-TTSD-v0.5:alex"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:anna"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:bella"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:benjamin"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:charles"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:claire"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:david"
    },
    {
      "name": "fnlp/MOSS-TTSD-v0.5:diana"
    }
  ]
}', true);

-- Workflow P3 node registrations & hot switches (T9-02/03/04/07, 2026-10-20): Loop / Parallel / Code / SubWorkflow nodes.
-- Unset/absent rows fall back to application.yml adi.wf.* defaults (enabled).
-- Container (T9-08) is canvas organization only, NOT a runtime node — no component row.
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Loop', 'Loop',
       'Iterate over an array input item by item (Each, parallel optional) or loop by condition (Loop), with max-iteration fuse', 21, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Loop');
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Parallel', 'Parallel',
       'Run branches in parallel and join with configurable semantics (all / any)', 22, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Parallel');
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Code', 'Code',
       'Run a code snippet as a Skill sandbox task; inputs via SKILL_SCRIPT_ARGS, stdout as output', 23, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Code');
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'SubWorkflow', 'SubWorkflow',
       'Run another workflow as a sub-process (reference-chain dedup, depth cap 3)', 24, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'SubWorkflow');

INSERT INTO adi_sys_config (name, value) SELECT 'wf_loop_enabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM adi_sys_config WHERE name = 'wf_loop_enabled' AND is_deleted = false);
INSERT INTO adi_sys_config (name, value) SELECT 'wf_parallel_enabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM adi_sys_config WHERE name = 'wf_parallel_enabled' AND is_deleted = false);
INSERT INTO adi_sys_config (name, value) SELECT 'wf_code_enabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM adi_sys_config WHERE name = 'wf_code_enabled' AND is_deleted = false);
INSERT INTO adi_sys_config (name, value) SELECT 'wf_sub_workflow_enabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM adi_sys_config WHERE name = 'wf_sub_workflow_enabled' AND is_deleted = false);
INSERT INTO adi_sys_config (name, value) SELECT 'wf_error_branch_enabled', 'true'
WHERE NOT EXISTS (SELECT 1 FROM adi_sys_config WHERE name = 'wf_error_branch_enabled' AND is_deleted = false);

-- =====================================================================
-- V44__mall_plugin.sql（二开 T9-M1，2026-10-29）种子数据
-- 商城导购：分类/商品/优惠券/活动 + 种子场景「商城导购」+ 种子智能体
-- （plugin_ids=mall，不绑模型——后台绑定模型后用户侧生效）；幂等可重复执行。
-- =====================================================================

-- ---------- 3. 种子数据：分类 ----------
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'mobile', '手机数码', '', 1, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'mobile' AND is_deleted = false);
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'home',   '家用电器', '', 2, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'home'   AND is_deleted = false);
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'cloth',  '服饰鞋包', '', 3, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'cloth'  AND is_deleted = false);
INSERT INTO mall_category(code, name, icon, sort, is_enable) SELECT 'food',   '食品生鲜', '', 4, true  WHERE NOT EXISTS (SELECT 1 FROM mall_category WHERE code = 'food'   AND is_deleted = false);

-- ---------- 4. 种子数据：商品（每分类 3~4 款，monthly_sales 决定热销排序） ----------
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '旗舰智能手机 Pro Max 256G', 'mobile', 5999.00, 6499.00, 8600, '限时直降,24期免息', '满5000减300', '6.7英寸 OLED / 骁龙旗舰芯 / 100W 快充', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '旗舰智能手机 Pro Max 256G' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '轻薄本 14英寸 16G+512G', 'mobile', 4299.00, 4599.00, 5400, '赠原装包鼠', '满4000减200', '2.8K 高刷屏 / 金属机身 / 长续航', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '轻薄本 14英寸 16G+512G' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '真无线降噪耳机 3代', 'mobile', 899.00, 999.00, 12800, '爆款热卖', '满800减80', '48dB 主动降噪 / 36h 续航', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '真无线降噪耳机 3代' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '智能手表 运动版', 'mobile', 1299.00, 1499.00, 4200, '新品', '', '血氧心率监测 / 14天续航', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '智能手表 运动版' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '变频对开门冰箱 502L', 'home', 3299.00, 3799.00, 2600, '以旧换新补贴', '满3000减250', '一级能效 / 风冷无霜 / 双循环', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '变频对开门冰箱 502L' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '洗烘一体滚筒洗衣机 10KG', 'home', 2899.00, 3299.00, 3100, '限时直降', '满2500减200', '直驱电机 / 除菌洗 / 智能投放', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '洗烘一体滚筒洗衣机 10KG' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '壁挂空调 1.5匹 新一级', 'home', 2599.00, 2899.00, 6800, '爆销机型,免费安装', '满2000减150', 'AI 节能 / 静音 22dB / 自清洁', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '壁挂空调 1.5匹 新一级' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '扫地机器人 旗舰款', 'home', 3999.00, 4499.00, 1900, '赠配件礼包', '满3500减300', '自集尘 / 拖布自洗 / 结构光避障', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '扫地机器人 旗舰款' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '男士休闲夹克 秋冬款', 'cloth', 399.00, 499.00, 5600, '两件8折', '满300减40', '防泼水面料 / 轻量保暖', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '男士休闲夹克 秋冬款' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '女士羊绒围巾', 'cloth', 259.00, 329.00, 3400, '热卖', '', '100% 羊绒 / 多色可选', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '女士羊绒围巾' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '轻便双肩背包 20L', 'cloth', 199.00, 259.00, 7200, '爆款热卖', '满200减20', '防泼水 / 15.6寸笔电仓', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '轻便双肩背包 20L' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '智利车厘子 JJ级 2kg', 'food', 168.00, 199.00, 15600, '生鲜直发,坏果包赔', '满150减15', '空运直达 / 冷链配送', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '智利车厘子 JJ级 2kg' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '有机纯牛奶 250ml*24盒', 'food', 89.00, 109.00, 9800, '整箱立减', '', '3.6g 乳蛋白 / 有机认证', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '有机纯牛奶 250ml*24盒' AND is_deleted = false);
INSERT INTO mall_product(name, category_code, price, original_price, monthly_sales, promo_tags, coupon_hint, remark, is_enable) SELECT
    '坚果大礼包 1.5kg', 'food', 99.00, 139.00, 6100, '礼盒装', '满99减10', '6种坚果 / 每日一包', true
    WHERE NOT EXISTS (SELECT 1 FROM mall_product WHERE name = '坚果大礼包 1.5kg' AND is_deleted = false);

-- ---------- 5. 种子数据：优惠券 / 活动 ----------
INSERT INTO mall_coupon(code, title, amount, min_spend, total_count, claimed_count, expire_time, is_enable)
SELECT 'MALL300', '满5000减300数码券', 300.00, 5000.00, 1000, 0, now() + interval '30 days', true
WHERE NOT EXISTS (SELECT 1 FROM mall_coupon WHERE code = 'MALL300' AND is_deleted = false);
INSERT INTO mall_coupon(code, title, amount, min_spend, total_count, claimed_count, expire_time, is_enable)
SELECT 'MALL100', '满1000减100通用券', 100.00, 1000.00, 5000, 0, now() + interval '30 days', true
WHERE NOT EXISTS (SELECT 1 FROM mall_coupon WHERE code = 'MALL100' AND is_deleted = false);
INSERT INTO mall_coupon(code, title, amount, min_spend, total_count, claimed_count, expire_time, is_enable)
SELECT 'MALL20', '无门槛20元新人券', 20.00, 0.00, -1, 0, now() + interval '15 days', true
WHERE NOT EXISTS (SELECT 1 FROM mall_coupon WHERE code = 'MALL20' AND is_deleted = false);

INSERT INTO mall_activity(title, description, icon, sort, is_enable) SELECT
    '年终数码狂欢节', '手机数码类目限时直降，最高立减 600 元，叠加 24 期免息。', '', 1, true
    WHERE NOT EXISTS (SELECT 1 FROM mall_activity WHERE title = '年终数码狂欢节' AND is_deleted = false);
INSERT INTO mall_activity(title, description, icon, sort, is_enable) SELECT
    '家电以旧换新补贴', '旧机抵扣最高 800 元，叠加国家补贴更划算。', '', 2, true
    WHERE NOT EXISTS (SELECT 1 FROM mall_activity WHERE title = '家电以旧换新补贴' AND is_deleted = false);
INSERT INTO mall_activity(title, description, icon, sort, is_enable) SELECT
    '生鲜周周抢', '每周三生鲜专场，车厘子 / 牛奶等爆品 5 折起。', '', 3, true
    WHERE NOT EXISTS (SELECT 1 FROM mall_activity WHERE title = '生鲜周周抢' AND is_deleted = false);

-- ---------- 6. 种子场景「商城导购」 + 种子智能体（plugin_ids='mall'，不绑模型） ----------
INSERT INTO adi_scene(uuid, user_id, title, remark, icon, sort, is_enable)
SELECT 'adi_mall_scene_0000000000000000001', 0,
       '商城导购',
       '查产品→选分类→看热销→比价格优惠→问问题→领优惠券，一站式智能导购客服。',
       'ShoppingCartOutlined', 99, true
WHERE NOT EXISTS (SELECT 1 FROM adi_scene WHERE uuid = 'adi_mall_scene_0000000000000000001' AND is_deleted = false);

INSERT INTO adi_agent(uuid, user_id, title, remark, avatar_url, system_message, work_design,
                      model_platform, model_name, llm_temperature, kb_ids, mcp_ids, plugin_ids,
                      enable_rag, enable_mcp, enable_web_search, character_uuid, sort, is_enable)
SELECT 'adi_mall_agent_0000000000000000001', 0,
       '商城导购助手',
       '商城智能导购客服：商品推荐、价格优惠查询、知识库问答、优惠券领取。',
       '',
       '你是「商城导购助手」，一个专业、友好的电商导购客服。请遵循以下原则：
1. 用户想找商品但没说品类时，先调用 mall_list_categories 展示分类让用户选择；
2. 用户选定品类后，调用 mall_top_products 给出该分类热销榜（默认 10 个，含价格与优惠标签）；
3. 用户询问某个商品的价格/优惠时，用 mall_product_detail 查询真实数据，禁止编造价格或促销信息；
4. 用户咨询售后、配送、退换等问题时，优先根据绑定的知识库回答；知识库未命中且问题模糊时，反问澄清（最多两轮），仍无法回答则如实告知并引导用户联系人工客服，禁止编造答案；
5. 用户问最新优惠时，调用 mall_latest_activities 介绍活动，并调用 mall_list_coupons 展示可领优惠券；
6. 用户明确要领某张券时，用券编码调用 mall_claim_coupon 帮用户领取，并告知领取结果；
7. 回答简洁清晰，价格用人民币符号 ¥ 表示，商品推荐不超过 10 个。',
       '', '', '', 0.70, '', '', 'mall',
       true, false, false, '', 99, true
WHERE NOT EXISTS (SELECT 1 FROM adi_agent WHERE uuid = 'adi_mall_agent_0000000000000000001' AND is_deleted = false);

INSERT INTO adi_scene_agent(uuid, scene_uuid, agent_uuid, sort)
SELECT 'adi_mall_sceneagent_000000000000001', 'adi_mall_scene_0000000000000000001', 'adi_mall_agent_0000000000000000001', 1
WHERE NOT EXISTS (SELECT 1 FROM adi_scene_agent
                  WHERE scene_uuid = 'adi_mall_scene_0000000000000000001'
                    AND agent_uuid = 'adi_mall_agent_0000000000000000001'
                    AND is_deleted = false);

-- ---------- 2. 种子数据：商城导购示例页（绑定 044 的种子场景） ----------
INSERT INTO adi_chat_page(uuid, name, icon, page_schema, scene_uuid, agent_uuid, sort, is_enable)
SELECT 'adi_chatpage_mall_000000000000000001',
       '商城导购',
       'ShoppingCartOutlined',
       '{
         "blocks": [
           { "type": "welcome", "title": "欢迎来到商城导购", "subtitle": "我可以帮你找商品、比价格、查优惠、领优惠券" },
           { "type": "quickPrompts", "items": ["看看手机分类的热销榜", "最近有什么优惠活动？", "有可以领的优惠券吗？"] },
           { "type": "richText", "content": "咨询售后、配送、退换等问题会优先查阅帮助知识库；解决不了会引导你联系人工客服。" },
           { "type": "sceneInfo" }
         ]
       }'::jsonb,
       'adi_mall_scene_0000000000000000001',
       '',
       99, true
WHERE NOT EXISTS (SELECT 1 FROM adi_chat_page WHERE uuid = 'adi_chatpage_mall_000000000000000001' AND is_deleted = false);
