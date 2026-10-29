-- V26: adi_workflow_runtime 补 is_test 列
-- 背景：实体 WorkflowRuntime 早已有 isTest 字段（测试运行标记），
--       但从未有迁移为 adi_workflow_runtime 建列，导致任何按 is_test 过滤的查询
--       （如 WorkflowRuntimeController.search 的分页 COUNT）直接 500：
--       ERROR: column "is_test" does not exist。
-- 幂等：IF NOT EXISTS，重复执行安全。

ALTER TABLE adi_workflow_runtime ADD COLUMN IF NOT EXISTS is_test boolean NOT NULL DEFAULT false;
