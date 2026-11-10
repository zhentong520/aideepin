-- V28: 业务编排 Harness M1 —— 场景绑定工作流 + runtime/消息关联
-- 方案：《业务编排Harness-对话驱动业务落地方案》§5.2 / §6
-- 内容：
--   1) adi_scene_workflow 表：场景与业务工作流绑定（照 adi_scene_agent 模式，全量覆盖式绑定）
--   2) adi_workflow_runtime 加 conversation_uuid / message_uuid / scene_uuid：
--      conversation_uuid = characterUuid（aideepin 中会话即 Character），
--      message_uuid = questionUuid（LLM 调用前生成，早于 assistant 消息落库），
--      scene_uuid = 发起对话时所在场景（可空，普通聊天不带）
--   3) adi_character_message 加 wf_runtime_uuid：assistant 消息回填，前端据此渲染 WorkflowCard
--     （一条消息可能关联多次 launch，存逗号分隔多值）
-- 幂等：IF NOT EXISTS，重复执行安全。

-- ---------- 1) adi_scene_workflow ----------
CREATE TABLE IF NOT EXISTS adi_scene_workflow
(
    id           bigserial primary key,
    uuid         varchar(32) default ''                      not null,
    scene_uuid   varchar(32) default ''                      not null,
    workflow_uuid varchar(32) default ''                     not null,
    sort         int         default 0                       not null,
    create_time  timestamp   default CURRENT_TIMESTAMP       not null,
    update_time  timestamp   default CURRENT_TIMESTAMP       not null,
    is_deleted   boolean     default false                   not null
);
COMMENT ON TABLE adi_scene_workflow IS 'Scene to workflow binding | 场景与业务工作流绑定';

CREATE INDEX IF NOT EXISTS idx_adi_scene_workflow_scene
    ON adi_scene_workflow (scene_uuid, is_deleted);
CREATE INDEX IF NOT EXISTS idx_adi_scene_workflow_workflow
    ON adi_scene_workflow (workflow_uuid, is_deleted);
-- One active binding per (scene, workflow): backs the idempotent upsert in SceneService.bindWorkflows
CREATE UNIQUE INDEX IF NOT EXISTS uk_adi_scene_workflow
    ON adi_scene_workflow (scene_uuid, workflow_uuid)
    WHERE is_deleted = false;

DROP TRIGGER IF EXISTS trigger_adi_scene_workflow ON adi_scene_workflow;
CREATE TRIGGER trigger_adi_scene_workflow
    BEFORE UPDATE
    ON adi_scene_workflow
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

-- ---------- 2) adi_workflow_runtime 关联列 ----------
ALTER TABLE adi_workflow_runtime ADD COLUMN IF NOT EXISTS conversation_uuid varchar(32) default '' not null;
ALTER TABLE adi_workflow_runtime ADD COLUMN IF NOT EXISTS message_uuid varchar(32) default '' not null;
ALTER TABLE adi_workflow_runtime ADD COLUMN IF NOT EXISTS scene_uuid varchar(32) default '' not null;
COMMENT ON COLUMN adi_workflow_runtime.conversation_uuid IS 'Origin conversation uuid (= adi_character.uuid) | 发起会话 uuid';
COMMENT ON COLUMN adi_workflow_runtime.message_uuid IS 'Origin question uuid | 发起提问的 questionUuid';
COMMENT ON COLUMN adi_workflow_runtime.scene_uuid IS 'Origin scene uuid, empty for normal chat | 发起时所在场景';

CREATE INDEX IF NOT EXISTS idx_wf_runtime_conversation
    ON adi_workflow_runtime (conversation_uuid, is_deleted);
CREATE INDEX IF NOT EXISTS idx_wf_runtime_message
    ON adi_workflow_runtime (message_uuid, is_deleted);

-- ---------- 3) adi_character_message 回填列 ----------
ALTER TABLE adi_character_message ADD COLUMN IF NOT EXISTS wf_runtime_uuid varchar(512) default '' not null;
COMMENT ON COLUMN adi_character_message.wf_runtime_uuid IS 'Launched workflow runtime uuids (comma separated) | 该消息触发的运行时 uuid，多个逗号分隔';

CREATE INDEX IF NOT EXISTS idx_character_message_wf_runtime
    ON adi_character_message (wf_runtime_uuid);
