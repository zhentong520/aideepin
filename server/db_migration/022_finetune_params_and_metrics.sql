-- =====================================================================
-- 022_finetune_params_and_metrics.sql
-- 微调模块增强：超参预设来源标记 + 训练报告（trainer_state 关键指标）
-- Finetune enhancement: preset marker + training report metrics
-- =====================================================================

ALTER TABLE adi_finetune_job
    ADD COLUMN IF NOT EXISTS preset  varchar(40) default '' not null,
    ADD COLUMN IF NOT EXISTS metrics jsonb       default '{}' not null;

COMMENT ON COLUMN adi_finetune_job.preset IS 'Parameter preset key: balanced/quality/chinese-chat/knowledge-qa/low-vram/fast-preview (traceability only, hyper_params is authoritative)';
COMMENT ON COLUMN adi_finetune_job.metrics IS 'Training report summarized by the worker from trainer_state.json: final/best loss, total steps, trainable params, runtime, best checkpoint, GPU';

-- hyper_params 的键集合在本版扩展（步数派生 + 调度器/正则化/早停/导出等），注释同步更新，
-- 否则 check_init 会因为列注释与 all_ddl.sql 不一致而报 DIFF。
COMMENT ON COLUMN adi_finetune_job.hyper_params IS 'Full hyper parameter snapshot (defaults filled, steps derived): lora_rank/lora_alpha/lora_dropout/lora_target/use_rslora/use_dora, learning_rate/num_epochs/batch_size/gradient_accumulation/lr_scheduler_type/warmup_ratio/weight_decay/max_grad_norm/optim, cutoff_len/val_ratio/packing/neftune_noise_alpha, gradient_checkpointing/flash_attn/seed/logging_steps/save_steps/eval_steps/early_stopping_patience, quant_bit/quant_method/export_merged/export_gguf/gguf_quant/template';
