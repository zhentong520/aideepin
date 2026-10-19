-- ============================================================
-- V3: finetune sample pool（会话收集 -> LLM 改写 -> 审核 -> 导出微调数据集）
-- 修复：035 迁移被放进旧手工目录 db_migration/，Flyway 目录缺失此表建表语句，
-- 全新库（含 Testcontainers 集成测试实库）查 adi_finetune_sample_pool 即报错。
-- 内容与 db_migration/035_finetune_sample_pool.sql 等价（全部 IF NOT EXISTS，幂等）。
-- ============================================================
-- =====================================================================

CREATE TABLE IF NOT EXISTS adi_finetune_sample_pool
(
    id                bigserial primary key,
    uuid              varchar(32)  default ''                     not null,
    user_id           bigint       default 0                      not null,
    source_type       varchar(30)  default 'character_chat'       not null,
    character_uuid    varchar(32)  default ''                     not null,
    character_name    varchar(120) default ''                     not null,
    source_message_ids text       default ''                      not null,
    raw_dialog        jsonb        default '{"turns": []}'        not null,
    generated_sample  jsonb                                       null,
    status            varchar(30)  default 'pending_rewrite'      not null,
    quality_note      varchar(500) default ''                     not null,
    reject_reason     varchar(500) default ''                     not null,
    dataset_id        bigint       default 0                      not null,
    remark            varchar(500) default ''                     not null,
    create_time       timestamp    default CURRENT_TIMESTAMP      not null,
    update_time       timestamp    default CURRENT_TIMESTAMP      not null,
    is_deleted        boolean      default false                  not null
);

COMMENT ON TABLE adi_finetune_sample_pool IS 'Finetune sample pool: dialogs collected from sessions, rewritten by LLM, reviewed by admin, exported as dataset | 会话收集的训练样本候选池';
COMMENT ON COLUMN adi_finetune_sample_pool.uuid IS 'UUID of the pool row';
COMMENT ON COLUMN adi_finetune_sample_pool.user_id IS 'Owner user ID (who collected it)';
COMMENT ON COLUMN adi_finetune_sample_pool.source_type IS 'Source dialog type: character_chat | agent_conv';
COMMENT ON COLUMN adi_finetune_sample_pool.character_uuid IS 'Character UUID the dialog belongs to';
COMMENT ON COLUMN adi_finetune_sample_pool.character_name IS 'Character name snapshot for display';
COMMENT ON COLUMN adi_finetune_sample_pool.source_message_ids IS 'Comma separated source adi_character_message.uuid list (question + answer)';
COMMENT ON COLUMN adi_finetune_sample_pool.raw_dialog IS 'Original dialog turns JSON: {"turns":[{"role":"user|assistant","content":"..."}]}';
COMMENT ON COLUMN adi_finetune_sample_pool.generated_sample IS 'Rewritten alpaca sample JSON: {"instruction","input","output"}; null before rewrite';
COMMENT ON COLUMN adi_finetune_sample_pool.status IS 'State machine: pending_rewrite -> generated -> confirmed/rejected; discarded on rewrite failure; in_dataset after export';
COMMENT ON COLUMN adi_finetune_sample_pool.quality_note IS 'Rule-based quality check notes (dedup/length/repeat), empty means passed';
COMMENT ON COLUMN adi_finetune_sample_pool.reject_reason IS 'Admin reject reason';
COMMENT ON COLUMN adi_finetune_sample_pool.dataset_id IS 'Reference to adi_finetune_dataset.id after export; 0 = not exported';
COMMENT ON COLUMN adi_finetune_sample_pool.remark IS 'Remark';

-- 幂等防护：存量库可能已手工执行过 035（触发器已存在），直接 CREATE 会炸掉 Flyway
DROP TRIGGER IF EXISTS trigger_finetune_sample_pool_update_time ON adi_finetune_sample_pool;
CREATE TRIGGER trigger_finetune_sample_pool_update_time
    BEFORE UPDATE
    ON adi_finetune_sample_pool
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX IF NOT EXISTS uk_finetune_sample_pool_uuid ON adi_finetune_sample_pool (uuid);
CREATE INDEX IF NOT EXISTS idx_finetune_sample_pool_status ON adi_finetune_sample_pool (status);
CREATE INDEX IF NOT EXISTS idx_finetune_sample_pool_character ON adi_finetune_sample_pool (character_uuid);
