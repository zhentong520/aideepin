-- ============================================================
-- 040_wf_test_run.sql（= V8__wf_test_run.sql，二开 T3-10，2026-10-19）
-- 单节点试跑：adi_workflow_runtime 加 is_test 标记列。
--
-- 选型说明（is_test 标记 vs 独立记录通道）：
--   试跑复用 WorkflowEngine 全链路（SSE 推流 / runtime_node 明细 / 指标快照），
--   runtime_node.workflow_runtime_id 外键指向 runtime 行，独立通道需要复制整套
--   runtime_node 存储或破坏外键；is_test 一列即可让试跑保留完整可观测性，
--   同时被正式记录查询（page）过滤，不污染正式执行记录。
--   开关 adi.wf.single-node-test-enabled 默认 true（纯前端配合功能，默认开合理）。
-- Idempotent: safe to run repeatedly
-- ============================================================

alter table adi_workflow_runtime add column if not exists is_test boolean default false not null;
comment on column adi_workflow_runtime.is_test is 'T3-10: true = 单节点试跑记录（不在正式执行记录列表展示）| test run, excluded from formal run list';
