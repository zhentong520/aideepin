-- =====================================================================
-- 015_finetune.sql
-- LoRA 微调训练模块：数据集 / 训练任务 / 模型产物 三张表
-- Finetune module: dataset / training job / model artifact
-- =====================================================================

CREATE TABLE adi_finetune_dataset
(
    id           bigserial primary key,
    uuid         varchar(32)  default ''                not null,
    user_id      bigint       default 0                 not null,
    name         varchar(120) default ''                not null,
    description  varchar(500) default ''                not null,
    format       varchar(30)  default 'alpaca-jsonl'    not null,
    file_uuid    varchar(32)  default ''                not null,
    sample_count integer      default 0                 not null,
    eval_count   integer      default 0                 not null,
    token_stat   jsonb        default '{}'              not null,
    create_time  timestamp    default CURRENT_TIMESTAMP not null,
    update_time  timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted   boolean      default false             not null
);

COMMENT ON TABLE adi_finetune_dataset IS 'Finetune dataset (JSONL)';
COMMENT ON COLUMN adi_finetune_dataset.uuid IS 'UUID of the dataset';
COMMENT ON COLUMN adi_finetune_dataset.user_id IS 'Owner user ID';
COMMENT ON COLUMN adi_finetune_dataset.name IS 'Dataset name';
COMMENT ON COLUMN adi_finetune_dataset.description IS 'Dataset description';
COMMENT ON COLUMN adi_finetune_dataset.format IS 'Data format: alpaca-jsonl, sharegpt-jsonl';
COMMENT ON COLUMN adi_finetune_dataset.file_uuid IS 'UUID of the raw file row in adi_file';
COMMENT ON COLUMN adi_finetune_dataset.sample_count IS 'Total training samples';
COMMENT ON COLUMN adi_finetune_dataset.eval_count IS 'Validation samples reserved (derived from job val_ratio at train time)';
COMMENT ON COLUMN adi_finetune_dataset.token_stat IS 'Rough statistics in JSON, e.g. {"total_chars":123,"est_tokens":456}';
COMMENT ON COLUMN adi_finetune_dataset.create_time IS 'Creation time';
COMMENT ON COLUMN adi_finetune_dataset.update_time IS 'Last update time';
COMMENT ON COLUMN adi_finetune_dataset.is_deleted IS 'Whether the record is soft-deleted';

CREATE TRIGGER trigger_finetune_dataset_update_time
    BEFORE UPDATE
    ON adi_finetune_dataset
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_finetune_dataset_uuid ON adi_finetune_dataset (uuid);

CREATE TABLE adi_finetune_job
(
    id             bigserial primary key,
    uuid           varchar(32)   default ''                not null,
    user_id        bigint        default 0                 not null,
    dataset_id     bigint        default 0                 not null,
    name           varchar(120)  default ''                not null,
    base_model_name varchar(100) default ''                not null,
    method         varchar(20)   default 'lora'            not null,
    hyper_params   jsonb         default '{}'              not null,
    status         varchar(20)   default 'pending'         not null,
    progress       numeric(5, 2) default 0                 not null,
    current_epoch  integer       default 0                 not null,
    train_loss     numeric(10, 4) default 0                not null,
    eval_loss      numeric(10, 4) default 0                not null,
    worker_id      varchar(64)   default ''                not null,
    worker_epoch   varchar(64)   default ''                not null,
    stop_flag      boolean       default false             not null,
    fail_reason    varchar(500)  default ''                not null,
    log_path       varchar(250)  default ''                not null,
    create_time    timestamp     default CURRENT_TIMESTAMP not null,
    update_time    timestamp     default CURRENT_TIMESTAMP not null,
    claim_time     timestamp,
    heartbeat_time timestamp,
    start_time     timestamp,
    end_time       timestamp,
    is_deleted     boolean       default false             not null
);

COMMENT ON TABLE adi_finetune_job IS 'Finetune training job queue (external GPU worker pulls via HTTP)';
COMMENT ON COLUMN adi_finetune_job.uuid IS 'UUID of the job';
COMMENT ON COLUMN adi_finetune_job.user_id IS 'Creator user ID';
COMMENT ON COLUMN adi_finetune_job.dataset_id IS 'Reference to adi_finetune_dataset.id';
COMMENT ON COLUMN adi_finetune_job.name IS 'Job name';
COMMENT ON COLUMN adi_finetune_job.base_model_name IS 'Base model name, e.g. Qwen2.5-7B-Instruct (LLaMA-Factory model name or HF id)';
COMMENT ON COLUMN adi_finetune_job.method IS 'Training method: lora, qlora';
COMMENT ON COLUMN adi_finetune_job.hyper_params IS 'Hyper parameters JSON: lora_rank/lora_alpha/lora_dropout/target_modules/learning_rate/num_epochs/batch_size/gradient_accumulation/max_seq_len/val_ratio/cutoff_len';
COMMENT ON COLUMN adi_finetune_job.status IS 'State machine: pending -> running -> success/failed/stopped';
COMMENT ON COLUMN adi_finetune_job.progress IS 'Overall progress percent 0-100';
COMMENT ON COLUMN adi_finetune_job.current_epoch IS 'Current epoch reported by the worker';
COMMENT ON COLUMN adi_finetune_job.train_loss IS 'Latest training loss';
COMMENT ON COLUMN adi_finetune_job.eval_loss IS 'Latest evaluation loss';
COMMENT ON COLUMN adi_finetune_job.worker_id IS 'Claiming worker identity';
COMMENT ON COLUMN adi_finetune_job.worker_epoch IS 'Claim epoch (regenerated per claim); late writes from a stale worker are rejected by (id, epoch)';
COMMENT ON COLUMN adi_finetune_job.stop_flag IS 'Cooperative stop flag: admin requests stop, worker aborts at next checkpoint';
COMMENT ON COLUMN adi_finetune_job.fail_reason IS 'Failure reason';
COMMENT ON COLUMN adi_finetune_job.log_path IS 'Relative path of the training log file under local storage';
COMMENT ON COLUMN adi_finetune_job.claim_time IS 'First claim time';
COMMENT ON COLUMN adi_finetune_job.heartbeat_time IS 'Worker liveness proof, refreshed on progress/heartbeat calls';
COMMENT ON COLUMN adi_finetune_job.start_time IS 'Latest (re)start time';
COMMENT ON COLUMN adi_finetune_job.end_time IS 'Terminal state time';

CREATE TRIGGER trigger_finetune_job_update_time
    BEFORE UPDATE
    ON adi_finetune_job
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_finetune_job_uuid ON adi_finetune_job (uuid);
CREATE INDEX idx_finetune_job_status ON adi_finetune_job (status);

CREATE TABLE adi_finetune_artifact
(
    id            bigserial primary key,
    uuid          varchar(32)  default ''                not null,
    job_id        bigint       default 0                 not null,
    job_uuid      varchar(32)  default ''                not null,
    user_id       bigint       default 0                 not null,
    type          varchar(20)  default 'adapter'         not null,
    name          varchar(120) default ''                not null,
    file_uuid     varchar(32)  default ''                not null,
    external_path varchar(250) default ''                not null,
    size_bytes    bigint       default 0                 not null,
    base_model    varchar(100) default ''                not null,
    remark        varchar(500) default ''                not null,
    create_time   timestamp    default CURRENT_TIMESTAMP not null,
    update_time   timestamp    default CURRENT_TIMESTAMP not null,
    is_deleted    boolean      default false             not null
);

COMMENT ON TABLE adi_finetune_artifact IS 'Finetune output artifact (LoRA adapter / merged model / GGUF)';
COMMENT ON COLUMN adi_finetune_artifact.uuid IS 'UUID of the artifact';
COMMENT ON COLUMN adi_finetune_artifact.job_id IS 'Reference to adi_finetune_job.id';
COMMENT ON COLUMN adi_finetune_artifact.job_uuid IS 'Reference to adi_finetune_job.uuid';
COMMENT ON COLUMN adi_finetune_artifact.user_id IS 'Owner user ID';
COMMENT ON COLUMN adi_finetune_artifact.type IS 'Artifact type: adapter (zip on server), merged (dir on GPU host), gguf (file on GPU host)';
COMMENT ON COLUMN adi_finetune_artifact.name IS 'Artifact name, usually the exported model tag';
COMMENT ON COLUMN adi_finetune_artifact.file_uuid IS 'UUID of the uploaded file row in adi_file (adapter zip); empty for artifacts kept on the GPU host';
COMMENT ON COLUMN adi_finetune_artifact.external_path IS 'Path on the GPU host for large artifacts (merged/GGUF), empty for server-stored ones';
COMMENT ON COLUMN adi_finetune_artifact.size_bytes IS 'Artifact size in bytes';
COMMENT ON COLUMN adi_finetune_artifact.base_model IS 'Base model this artifact was trained on';
COMMENT ON COLUMN adi_finetune_artifact.remark IS 'Remark';

CREATE TRIGGER trigger_finetune_artifact_update_time
    BEFORE UPDATE
    ON adi_finetune_artifact
    FOR EACH ROW
EXECUTE PROCEDURE update_modified_column();

CREATE UNIQUE INDEX uk_finetune_artifact_uuid ON adi_finetune_artifact (uuid);
CREATE INDEX idx_finetune_artifact_job ON adi_finetune_artifact (job_id);
