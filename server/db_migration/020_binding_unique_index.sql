-- ============================================================
-- 020: Guard duplicate bindings (Agent<->Workflow, Scene<->Agent)
-- 二次开发扩展：为绑定关系加部分唯一索引，配合服务层的幂等 upsert，
-- 杜绝并发重复绑定。
-- Idempotent: safe to run repeatedly
-- ============================================================

-- 先清理可能存在的历史重复行（保留 id 最大的一条）
-- Collapse possible duplicate active rows first (keep the newest one)
WITH dup AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY scene_uuid, agent_uuid ORDER BY id DESC) AS rn
    FROM adi_scene_agent
    WHERE is_deleted = false
)
UPDATE adi_scene_agent SET is_deleted = true WHERE id IN (SELECT id FROM dup WHERE rn > 1);

WITH dup AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY agent_uuid, workflow_uuid ORDER BY id DESC) AS rn
    FROM adi_agent_workflow
    WHERE is_deleted = false
)
UPDATE adi_agent_workflow SET is_deleted = true WHERE id IN (SELECT id FROM dup WHERE rn > 1);

-- 一个场景对同一 Agent 只能有一条有效绑定
-- One active binding per (scene, agent)
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_scene_agent
    ON adi_scene_agent (scene_uuid, agent_uuid)
    WHERE is_deleted = false;

-- 一个 Agent 对同一工作流只能有一条有效绑定
-- One active binding per (agent, workflow)
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_agent_workflow
    ON adi_agent_workflow (agent_uuid, workflow_uuid)
    WHERE is_deleted = false;
