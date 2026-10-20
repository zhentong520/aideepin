-- ============================================================
-- V16__workflow_trigger_and_version.sql（二开第 9 批 P3，2026-10-20）
-- 一个迁移覆盖本批三卡 DDL（T9-01 / T9-05 / T9-06）：
--
-- 【T9-01】adi_workflow_trigger：工作流触发配置表（定时 cron / webhook token）。
--   每行 = 一个触发器，归属一个工作流；定时触发按 cron_expr 由
--   WorkflowTriggerScheduler 轮询到期即触发；webhook 触发按 webhook_token
--   匹配（唯一索引）。input_params 存触发时的默认入参（与 /workflow/run
--   的 inputs 同构）。last_fire_time/last_fire_status 记录最近一次触发，
--   上次未跑完（存在 DOING 运行记录）不重复触发。
-- 【T9-01】adi_workflow_runtime.trigger_source：触发来源（manual/schedule/webhook/api），
--   触发记录（来源/参数/结果）进运行历史：来源=本列，参数=input，结果=output+status。
-- 【T9-05】adi_workflow_version：版本快照表。每次保存生成一条（version_no 递增，
--   (workflow_id, version_no) 唯一），definition 为完整定义 JSON（含节点/边全量配置）；
--   回滚 = 按历史 definition 重放 + 生成新版本，绝不覆盖历史行。
-- 【T9-06】无新增表：导出脱敏与未知节点标记为纯应用行为；模板为 classpath 资源。
--
-- 幂等：IF NOT EXISTS / add column if not exists，可重复执行。
-- ============================================================

-- ---------- T9-01: 工作流触发配置 ----------
CREATE TABLE IF NOT EXISTS adi_workflow_trigger
(
    id               bigserial primary key,
    uuid             varchar(32)  default ''                not null,
    workflow_id      bigint       default 0                 not null,
    user_id          bigint       default 0                 not null,
    trigger_type     varchar(16)  default ''                not null,
    cron_expr        varchar(64)  default ''                not null,
    webhook_token    varchar(64)  default ''                not null,
    input_params     jsonb        default '[]'              not null,
    is_enable        boolean      default true              not null,
    last_fire_time   timestamp,
    last_fire_status smallint     default 0                 not null,
    create_time      timestamp    default CURRENT_TIMESTAMP not null,
    update_time      timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted       boolean      default false             not null
);
COMMENT ON TABLE adi_workflow_trigger IS 'Workflow trigger config (schedule / webhook) | 工作流触发配置（定时 cron / webhook token）';
COMMENT ON COLUMN adi_workflow_trigger.uuid IS 'Trigger row unique uuid | 触发器唯一uuid';
COMMENT ON COLUMN adi_workflow_trigger.workflow_id IS 'Owning workflow id | 归属工作流id';
COMMENT ON COLUMN adi_workflow_trigger.user_id IS 'Creator user id | 创建人id';
COMMENT ON COLUMN adi_workflow_trigger.trigger_type IS 'schedule | webhook | 触发类型：定时 / webhook';
COMMENT ON COLUMN adi_workflow_trigger.cron_expr IS 'Spring CronExpression (6-field), schedule triggers only | 定时触发 cron 表达式（Spring 6 域格式），仅 schedule 类型';
COMMENT ON COLUMN adi_workflow_trigger.webhook_token IS 'Webhook path token, unique; URL = {backend}/workflow/webhook/{token} | webhook 路径 token，唯一索引';
COMMENT ON COLUMN adi_workflow_trigger.input_params IS 'Default inputs of triggered runs, same shape as /workflow/run inputs | 触发时默认入参（与 /workflow/run inputs 同构）';
COMMENT ON COLUMN adi_workflow_trigger.is_enable IS 'Disabled triggers never fire | 停用后不再触发';
COMMENT ON COLUMN adi_workflow_trigger.last_fire_time IS 'Last fired time (set at dispatch) | 最近一次派发时间';
COMMENT ON COLUMN adi_workflow_trigger.last_fire_status IS '0=none 1=dispatched 2=dispatch-failed | 最近派发状态：0未触发 1已派发 2派发失败';
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_trigger_token ON adi_workflow_trigger (webhook_token) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_workflow_trigger_wf ON adi_workflow_trigger (workflow_id);

-- ---------- T9-01: 运行记录补触发来源 ----------
alter table adi_workflow_runtime add column if not exists trigger_source varchar(16) default 'manual' not null;
COMMENT ON COLUMN adi_workflow_runtime.trigger_source IS 'T9-01: manual / schedule / webhook / api | 触发来源：手动/定时/webhook/内部API';

-- ---------- T9-05: 工作流版本快照 ----------
CREATE TABLE IF NOT EXISTS adi_workflow_version
(
    id               bigserial primary key,
    uuid             varchar(32)  default ''                not null,
    workflow_id      bigint       default 0                 not null,
    version_no       int          default 1                 not null,
    title            varchar(100) default ''                not null,
    remark           varchar(500) default ''                not null,
    definition       jsonb        default '{}'              not null,
    modifier_user_id bigint       default 0                 not null,
    node_count       int          default 0                 not null,
    edge_count       int          default 0                 not null,
    create_time      timestamp    default CURRENT_TIMESTAMP not null,
    update_time      timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted       boolean      default false             not null
);
COMMENT ON TABLE adi_workflow_version IS 'Workflow definition version snapshot (T9-05) | 工作流定义版本快照：每次保存一条，回滚生成新版本不覆盖历史';
COMMENT ON COLUMN adi_workflow_version.version_no IS 'Monotonic per workflow, unique (workflow_id, version_no) | 同一工作流内单调递增，唯一约束';
COMMENT ON COLUMN adi_workflow_version.definition IS 'Full definition JSON (title/remark/nodes/edges, NOT sanitized — rollback needs real configs) | 完整定义JSON（不脱敏：回滚需要真实配置）';
COMMENT ON COLUMN adi_workflow_version.modifier_user_id IS 'Who saved this version | 该版本的修改人';
COMMENT ON COLUMN adi_workflow_version.node_count IS 'Node count at snapshot time, for diff summary | 快照时节点数（diff 概要用）';
COMMENT ON COLUMN adi_workflow_version.edge_count IS 'Edge count at snapshot time, for diff summary | 快照时边数（diff 概要用）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_workflow_version_no ON adi_workflow_version (workflow_id, version_no) WHERE is_deleted = false;
CREATE INDEX IF NOT EXISTS idx_workflow_version_wf ON adi_workflow_version (workflow_id);
