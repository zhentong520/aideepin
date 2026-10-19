-- 030: 将千问重排序(qwen3.7-text-rerank)设为全部知识库的默认 rerank 模型并开启 rerank
-- (2026-09-23) 配合 029 注册的 qianwen 平台模型使用。
-- 设计要点：
--   1. 按 name 定位 rerank 模型 id，避免硬编码（不同环境 id 可能不同）；
--   2. 幂等：仅更新「模型未指向 qwen rerank 或 rerank 未开启」的知识库，可重复执行；
--   3. 仅动数据层（已有知识库），不改 schema；top_n / min_score 保持默认（代码层 null→0/0.0）。
DO $$
DECLARE
    v_rerank_id BIGINT;
BEGIN
    SELECT id INTO v_rerank_id
    FROM adi_ai_model
    WHERE name = 'qwen3.7-text-rerank' AND is_deleted = false
    LIMIT 1;

    IF v_rerank_id IS NOT NULL THEN
        UPDATE adi_knowledge_base
        SET retrieve_rerank_model_id = v_rerank_id,
            retrieve_rerank_enabled    = true,
            update_time                = CURRENT_TIMESTAMP
        WHERE is_deleted = false
          AND (retrieve_rerank_model_id IS DISTINCT FROM v_rerank_id
               OR retrieve_rerank_enabled <> true);
        RAISE NOTICE 'qianwen rerank default applied, model_id=%', v_rerank_id;
    ELSE
        RAISE NOTICE 'qwen3.7-text-rerank not found, skip';
    END IF;
END
$$;
