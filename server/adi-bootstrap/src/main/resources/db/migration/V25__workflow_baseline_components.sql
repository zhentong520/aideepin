-- V25: 补齐工作流基础组件种子数据
-- 背景：上游 langchain4j-aideepin 的 docs/create.sql 含 11 个基础组件种子，
--       但本仓库 V1 baseline 只建表未种数据；V13/V15 仅补了编排类组件
--       （Orchestrator/Loop/Parallel/Code/SubWorkflow）。
--       缺少 Start 组件时 WorkflowService.add() 会抛 B0023「工作流节点定义找不到」
--       （createStartNode → getStartComponent），导致无法创建任何流程。
-- 注意：上游种子里的 Draw（画图）未收录——本仓库引擎 WfNodeFactory 没有 DRAW 分支，
--       画图能力由 OpenAiImage / Tongyiwanx 两个组件承担；种入 Draw 会导致
--       侧栏可拖入一个运行时必炸（unsupported workflow component）的死节点。
-- 幂等：唯一约束 uk_workflow_component_name(name, is_deleted) + ON CONFLICT DO NOTHING，
--       重复执行不会产生重复行。

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'Start', '开始', '流程由此开始', 1, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'End', '结束', '流程由此结束', 2, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'Answer', '生成回答', '调用大语言模型回答问题', 3, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'DocumentExtractor', '文档提取', '从文档中提取信息', 4, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'KeywordExtractor', '关键词提取',
        '从用户的问题中提取关键词，Top N指定需要提取的关键词数量。', 5, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'KnowledgeRetrieval', '知识检索', '从知识库中检索信息，需选中知识库', 6, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'Switcher', '条件分支', '根据设置的条件引导执行不同的流程', 7, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'Classifier', '内容归类',
        '使用大语言模型对输入信息进行分析并归类，根据类别调用对应的下游节点', 8, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'Template', '模板转换',
        '将多个变量合并成一个输出内容', 9, true)
ON CONFLICT (name, is_deleted) DO NOTHING;

INSERT INTO adi_workflow_component (uuid, name, title, remark, display_order, is_enable)
VALUES (replace(gen_random_uuid()::text, '-', ''), 'Google', 'Google搜索', '从Google中检索信息', 10, true)
ON CONFLICT (name, is_deleted) DO NOTHING;
