-- ============================================================
-- V13__agent_delegation_orchestrator.sql（二开 T7-02/T7-03，2026-10-20）
-- 多 Agent 三卡（T7-02 delegate_agent / T7-03 OrchestratorNode / T7-04 A2A 防环）：
--   1. adi_agent_delegation_log：委派契约记录表（objective/output_format/context_summary/
--      must_not_do 四要素 + effort 档位 + 调用链快照 + 结果），保证委派可追溯。
--      T7-04 的 A2A 双向互调防环与 T7-02 的深度限制共用链快照字段做审计。
--   2. adi_workflow_component 注册 Orchestrator 编排节点（T7-03）：
--      未注册的组件名会让 WfNodeFactory 抛 "unsupported workflow component"，
--      因此本 INSERT 是该节点生效的必要条件。
-- 幂等：IF NOT EXISTS / WHERE NOT EXISTS 写法，可重复执行。
-- ============================================================

-- ---------- T7-02: Agent 委派记录表 ----------
CREATE TABLE IF NOT EXISTS adi_agent_delegation_log
(
    id                bigserial primary key,
    uuid              varchar(32)      NOT NULL DEFAULT '',
    trace_id          varchar(64)      NOT NULL DEFAULT '',
    source_agent_id   varchar(64)      NOT NULL DEFAULT '',
    source_agent_name varchar(200)     NOT NULL DEFAULT '',
    target_agent_id   varchar(64)      NOT NULL DEFAULT '',
    target_agent_name varchar(200)     NOT NULL DEFAULT '',
    objective         text             NOT NULL DEFAULT '',
    output_format     varchar(500)     NOT NULL DEFAULT '',
    context_summary   text             NOT NULL DEFAULT '',
    must_not_do       text             NOT NULL DEFAULT '',
    effort_level      varchar(16)      NOT NULL DEFAULT '',
    chain_depth       int              NOT NULL DEFAULT 0,
    chain_path        varchar(500)     NOT NULL DEFAULT '',
    status            varchar(16)      NOT NULL DEFAULT '',
    error_message     text             NOT NULL DEFAULT '',
    duration_ms       bigint           NOT NULL DEFAULT 0,
    input_tokens      int              NOT NULL DEFAULT 0,
    output_tokens     int              NOT NULL DEFAULT 0,
    user_id           bigint           NOT NULL DEFAULT 0,
    detail            jsonb            NOT NULL DEFAULT '{}'::jsonb,
    create_time       timestamp        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       timestamp        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_deleted        boolean          NOT NULL DEFAULT false
);

COMMENT ON TABLE adi_agent_delegation_log IS 'Agent delegation log: contract four fields + effort tier + call chain snapshot | Agent委派记录：契约四要素+投入档位+调用链快照';
COMMENT ON COLUMN adi_agent_delegation_log.uuid IS 'Record unique uuid | 记录唯一uuid';
COMMENT ON COLUMN adi_agent_delegation_log.trace_id IS 'Full-chain trace id shared with agent trace spans | 全链路traceId，与agent trace span共用';
COMMENT ON COLUMN adi_agent_delegation_log.source_agent_id IS 'Delegating agent uuid, empty when user-initiated | 发起委派的Agent uuid，用户直接发起为空';
COMMENT ON COLUMN adi_agent_delegation_log.source_agent_name IS 'Delegating agent name | 发起委派的Agent名称';
COMMENT ON COLUMN adi_agent_delegation_log.target_agent_id IS 'Target agent uuid | 目标Agent uuid';
COMMENT ON COLUMN adi_agent_delegation_log.target_agent_name IS 'Target agent name | 目标Agent名称';
COMMENT ON COLUMN adi_agent_delegation_log.objective IS 'Delegation objective (required contract field) | 委派目标（契约必填）';
COMMENT ON COLUMN adi_agent_delegation_log.output_format IS 'Expected output format (contract field) | 期望产出格式（契约要素）';
COMMENT ON COLUMN adi_agent_delegation_log.context_summary IS 'Context summary handed to target (contract field) | 背景摘要（契约要素）';
COMMENT ON COLUMN adi_agent_delegation_log.must_not_do IS 'Explicit prohibitions (contract field) | 禁止事项（契约要素）';
COMMENT ON COLUMN adi_agent_delegation_log.effort_level IS 'Effort tier LOW/MEDIUM/HIGH from objective difficulty | 投入档位（按objective难度）';
COMMENT ON COLUMN adi_agent_delegation_log.chain_depth IS 'Call chain depth at delegation time (T7-02 depth-limit audit) | 委派时调用链深度（深度限制审计）';
COMMENT ON COLUMN adi_agent_delegation_log.chain_path IS 'Call chain snapshot agent ids joined by -> (T7-04 cycle-prevention audit) | 调用链快照（防环审计）';
COMMENT ON COLUMN adi_agent_delegation_log.status IS 'SUCCESS / FAIL / REJECTED | 执行结果';
COMMENT ON COLUMN adi_agent_delegation_log.error_message IS 'Rejection or failure reason | 拒绝/失败原因';
COMMENT ON COLUMN adi_agent_delegation_log.detail IS 'Extra detail json (difficulty signals, budget params) | 补充信息json（难度信号/预算参数）';

CREATE INDEX IF NOT EXISTS idx_agent_delegation_trace ON adi_agent_delegation_log (trace_id);
CREATE INDEX IF NOT EXISTS idx_agent_delegation_target ON adi_agent_delegation_log (target_agent_id);
CREATE INDEX IF NOT EXISTS idx_agent_delegation_user ON adi_agent_delegation_log (user_id, id);

DROP TRIGGER IF EXISTS trigger_agent_delegation_log_update_time ON adi_agent_delegation_log;
CREATE TRIGGER trigger_agent_delegation_log_update_time
    BEFORE UPDATE
    ON adi_agent_delegation_log
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ---------- T7-03: Orchestrator 编排节点注册 ----------
-- 实现见 adi-common：com.moyz.adi.common.workflow.node.orchestrator.OrchestratorNode
-- 编排型节点：把子任务分派给多个子 Agent 并行执行，经共享黑板（blackboard）回写结果并汇总。
INSERT INTO adi_workflow_component(uuid, name, title, remark, display_order, is_enable)
SELECT replace(gen_random_uuid()::text, '-', ''), 'Orchestrator', '多 Agent 编排',
       '把子任务分派给多个子 Agent 并行执行，通过共享黑板回写结果并汇总',
       20, true
WHERE NOT EXISTS (SELECT 1 FROM adi_workflow_component WHERE name = 'Orchestrator');

-- ---------- 铁律 10 热开关种子 ----------
-- 两个多 Agent 开关写进 adi_sys_config：管理端系统配置页可直接改值，保存即生效（无需重启）；
-- 删除该行或置空时代码回退 application.yml 的 adi.agent.* 默认值（缺省开）。
-- 幂等：NOT EXISTS 守卫，可重复执行（与 V10 llm_streaming_timeout_seconds 同款写法）。
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
