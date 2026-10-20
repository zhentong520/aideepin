-- ============================================================
-- V15__workflow_p3_nodes.sql（二开 T9-02/03/04/07/08，2026-10-20）
-- 工作流引擎 P3 五卡：
--   1. adi_workflow_component 注册 4 个新节点（T9-02 Loop / T9-03 Parallel /
--      T9-04 Code / T9-07 SubWorkflow）：未注册的组件名会让 WfNodeFactory 抛
--      "unsupported workflow component"，因此本 INSERT 是节点生效的必要条件。
--      （T9-08 Container 是画布组织，不是运行时节点，不注册组件。）
--   2. adi_workflow_node 加 layout jsonb（T9-08）：Container 分组/折叠等纯布局
--      元数据，引擎构图与执行完全忽略该字段。
--   3. adi_sys_config 种子 5 个热开关（铁律 10）：wf_loop_enabled /
--      wf_parallel_enabled / wf_code_enabled / wf_sub_workflow_enabled /
--      wf_error_branch_enabled，管理端可热停用对应行为。
-- 幂等：IF NOT EXISTS / WHERE NOT EXISTS 写法，可重复执行。
-- ============================================================

-- ---------- T9-08: 节点布局元数据（Container 分组，纯布局不影响执行语义） ----------
ALTER TABLE adi_workflow_node ADD COLUMN IF NOT EXISTS layout jsonb NOT NULL DEFAULT '{}'::jsonb;
COMMENT ON COLUMN adi_workflow_node.layout IS 'Canvas layout metadata (T9-08 container grouping etc.), no execution semantics | 画布布局元数据（T9-08 Container分组等），不参与执行语义';

-- ---------- T9-02/03/04/07: 新节点组件注册 ----------
-- 实现见 adi-common：com.moyz.adi.common.workflow.node.loop.LoopNode
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Loop', '循环节点',
       '对数组输入逐项执行子链（Each，并行可选）或按条件循环（Loop），带最大次数熔断',
       21, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Loop');

-- 实现见 adi-common：com.moyz.adi.common.workflow.node.parallel.ParallelNode
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Parallel', '并行节点',
       '多分支并行执行，全部完成后按 join 语义（全成功/任一成功）汇合',
       22, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Parallel');

-- 实现见 adi-common：com.moyz.adi.common.workflow.node.code.CodeNode（复用 Skill 脚本沙箱）
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Code', '代码节点',
       '把一段代码作为 Skill 沙箱任务执行，输入经 SKILL_SCRIPT_ARGS 注入，stdout 为输出',
       23, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Code');

-- 实现见 adi-common：com.moyz.adi.common.workflow.node.subworkflow.SubWorkflowNode
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'SubWorkflow', '子工作流',
       '引用另一个工作流作为子流程执行（引用链去重，深度上限 3）',
       24, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'SubWorkflow');

-- ---------- 铁律 10 热开关种子（sys_config 优先于 application.yml 的 adi.wf.*） ----------
INSERT INTO adi_sys_config (name, value)
SELECT 'wf_loop_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'wf_loop_enabled' AND is_deleted = false
);

INSERT INTO adi_sys_config (name, value)
SELECT 'wf_parallel_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'wf_parallel_enabled' AND is_deleted = false
);

INSERT INTO adi_sys_config (name, value)
SELECT 'wf_code_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'wf_code_enabled' AND is_deleted = false
);

INSERT INTO adi_sys_config (name, value)
SELECT 'wf_sub_workflow_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'wf_sub_workflow_enabled' AND is_deleted = false
);

INSERT INTO adi_sys_config (name, value)
SELECT 'wf_error_branch_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'wf_error_branch_enabled' AND is_deleted = false
);
