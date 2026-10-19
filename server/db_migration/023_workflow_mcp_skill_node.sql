-- ============================================================
-- 023: Workflow MCP / Skill nodes
-- 二次开发扩展：工作流新增 MCP 工具节点与技能节点
-- Extension: register the workflow Mcp and Skill components
-- Idempotent: safe to run repeatedly
-- ============================================================
-- 说明：Database 节点已由 019 注册，本迁移只补 MCP / Skill 两个节点。
-- 节点实现位于 adi-common：
--   com.moyz.adi.common.workflow.node.mcp.McpNode
--   com.moyz.adi.common.workflow.node.skill.SkillNode
-- 未注册组件名会导致 WfNodeFactory 抛 "unsupported workflow component"，
-- 因此本迁移是这两个节点生效的必要条件。
-- Note: the Database node was registered by 019. Unregistered component names make
-- WfNodeFactory throw, so this migration is required for the two new nodes to work.
-- ============================================================

-- ------------------------------------------------------------
-- 1. MCP 工具节点
--    Calls one tool exposed by the current user's MCP servers
-- ------------------------------------------------------------
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Mcp', 'MCP 工具',
       '调用当前用户 MCP 服务暴露的某个工具一次，把结果传给下游节点',
       8, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Mcp');

-- ------------------------------------------------------------
-- 2. 技能节点
--    Reads a skill instruction body, or executes one of its scripts
-- ------------------------------------------------------------
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Skill', '技能',
       '读取技能（SKILL.md）指令正文，或执行技能目录下的脚本',
       9, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Skill');
