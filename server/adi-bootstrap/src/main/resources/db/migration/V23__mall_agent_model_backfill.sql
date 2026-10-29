-- V23: 商城导购智能体默认模型回填（真实浏览器 E2E 暴露的数据缺陷）
--
-- 现象：用户端商城页发送消息时前端报「当前场景的智能体未绑定模型，请联系管理员」，
--       消息发送被 InputEditor.createChatTask 静默拦截，真实 LLM 链路完全走不通。
-- 根因：V21 种子无法预知部署环境有哪些可用模型，INSERT 时 model_platform/model_name
--       留空；前端 sceneStore.resolveModelByCharacter 解析为 null 即拦截发送。
-- 修复：为绑定为空的商城智能体回填「第一个启用且平台配有 api_key 的文本模型」。
--       幂等：仅当 model_name 为空时回填，已手工绑定模型的部署不受影响。

UPDATE adi_agent a
SET model_platform = sel.pname,
    model_name     = sel.mname
FROM (
    SELECT p.name AS pname, m.name AS mname
    FROM adi_ai_model m
    JOIN adi_model_platform p ON m.platform = p.name
    WHERE m.is_enable = true
      AND p.is_deleted = false
      AND p.api_key <> ''
      AND m.type IN ('text', '')
    ORDER BY p.id, m.id
    LIMIT 1
) sel
WHERE a.uuid = 'adi_mall_agent_00000000000000001'
  AND a.is_deleted = false
  AND (a.model_name = '' OR a.model_name IS NULL);
