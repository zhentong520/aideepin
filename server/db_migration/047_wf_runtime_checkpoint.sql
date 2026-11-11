-- ============================================================
-- 047_wf_runtime_checkpoint.sql（业务编排 Harness M2-T4，2026-11-11）
-- 工作流运行检查点：仅在停驻态（PAUSED=8 / WAITING_INPUT=5）落盘。
-- 收益：进程重启（kill -9 / 部署）后，停驻实例不再永久卡死——「继续」按钮
-- 触发 L2 重建路径：按 checkpoint 记录的停驻节点 + runtime_node 输出上下文，
-- 重建引擎从断点节点起跑（复用 T3-10 指定节点执行机制）。
-- 决策点①：只在停驻时落一行（upsert），终态即删；不做逐节点 checkpoint。
-- Idempotent: safe to run repeatedly
-- ============================================================

create table if not exists adi_workflow_runtime_checkpoint
(
    id               bigserial primary key,
    runtime_uuid     varchar(64) default '' not null,
    workflow_uuid    varchar(64) default '' not null,
    process_status   integer     default 0  not null,
    paused_node_uuid varchar(64) default '' not null,
    state_snapshot   jsonb       default '{}'::jsonb not null,
    create_time      timestamp   default CURRENT_TIMESTAMP not null,
    update_time      timestamp   default CURRENT_TIMESTAMP not null,
    is_deleted       boolean     default false not null
);

comment on table adi_workflow_runtime_checkpoint is 'Workflow runtime checkpoint, one row per parked runtime (PAUSED/WAITING_INPUT), deleted on terminal | 工作流运行检查点：每个停驻实例一行，终态即删';
comment on column adi_workflow_runtime_checkpoint.runtime_uuid is 'runtime uuid (unique among live rows) | 运行实例uuid（活跃行唯一）';
comment on column adi_workflow_runtime_checkpoint.workflow_uuid is 'workflow uuid for engine rebuild | 工作流uuid（重建引擎用）';
comment on column adi_workflow_runtime_checkpoint.process_status is 'parked status: 5=WAITING_INPUT / 8=PAUSED | 停驻状态';
comment on column adi_workflow_runtime_checkpoint.paused_node_uuid is 'node uuid the flow stopped at (next node to execute) | 停驻节点（恢复时从该节点起跑）';
comment on column adi_workflow_runtime_checkpoint.state_snapshot is 'lightweight state snapshot (completed node uuids etc.) | 轻量状态快照';

create unique index if not exists uk_wf_runtime_checkpoint_runtime
    on adi_workflow_runtime_checkpoint (runtime_uuid) where is_deleted = false;

create index if not exists idx_wf_runtime_checkpoint_workflow
    on adi_workflow_runtime_checkpoint (workflow_uuid);

drop trigger if exists trigger_adi_workflow_runtime_checkpoint_update_time on adi_workflow_runtime_checkpoint;
create trigger trigger_adi_workflow_runtime_checkpoint_update_time
    before update
    on adi_workflow_runtime_checkpoint
    for each row
execute procedure update_modified_column();
