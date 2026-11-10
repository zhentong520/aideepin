-- V29: 业务编排 Harness M1 —— 灰度总开关种子
-- 方案：《业务编排Harness-对话驱动业务落地方案》§6 灰度
-- 内容：
--   adi_sys_config 种子 wf_launch_enabled（铁律 10 热开关惯例，sys_config 优先于缺省值）：
--   WorkflowOrchestrationService.isEnabled() 缺省关（无配置行 / 值为 blank 或 false 均视为关），
--   开启后才向对话注入 LaunchWorkflowTool（场景绑定工作流的触发链路总闸）。
--   灰度期可在管理端 sys_config 页将该行改为 false 立即下线触发入口（改后需重启生效，LocalCache 启动加载）。
-- 幂等：WHERE NOT EXISTS，重复执行安全。

INSERT INTO adi_sys_config (name, value)
SELECT 'wf_launch_enabled', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM adi_sys_config WHERE name = 'wf_launch_enabled' AND is_deleted = false
);
