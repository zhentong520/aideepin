package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ObjectNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 微调训练任务：由外部 GPU Worker 通过 HTTP 拉取执行（claim -> 汇报进度 -> 终态），
 * 服务端只维护状态机与存活判定，不复用进程内索引队列（训练是跨机长任务）。
 * 状态机：pending -> running -> success / failed / stopped；
 * pending 可被停止直接置 stopped；running 置 stop_flag 由 Worker 协作中止。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_finetune_job", autoResultMap = true)
public class FinetuneJob extends BaseEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_STOPPED = "stopped";

    @TableField("uuid")
    private String uuid;

    @TableField("user_id")
    private Long userId;

    @TableField("dataset_id")
    private Long datasetId;

    @TableField("name")
    private String name;

    /**
     * 基座模型名，如 Qwen2.5-7B-Instruct（LLaMA-Factory 的 model_name 或 HF id）
     */
    @TableField("base_model_name")
    private String baseModelName;

    /**
     * lora / qlora
     */
    @TableField("method")
    private String method;

    /**
     * 参数预设 key（balanced / quality / chinese-chat / knowledge-qa / low-vram / fast-preview）。
     * 只是来源标记，真正的超参在 hyper_params 里已展开，便于复现与按预设统计效果。
     */
    @TableField("preset")
    private String preset;

    /**
     * 完整超参快照（默认值已填充、步数已派生）：
     * lora_rank/lora_alpha/lora_dropout/lora_target/use_rslora/use_dora、
     * learning_rate/num_epochs/batch_size/gradient_accumulation/lr_scheduler_type/warmup_ratio/
     * weight_decay/max_grad_norm/optim、cutoff_len/val_ratio/packing/neftune_noise_alpha、
     * gradient_checkpointing/flash_attn/seed/logging_steps/save_steps/eval_steps/
     * early_stopping_patience、quant_bit/quant_method/export_merged/export_gguf/gguf_quant/template
     */
    @TableField(value = "hyper_params", jdbcType = org.apache.ibatis.type.JdbcType.JAVA_OBJECT, typeHandler = ObjectNodeTypeHandler.class)
    private ObjectNode hyperParams;

    /**
     * 训练报告（Worker 从 trainer_state.json 汇总：最终/最优 loss、总步数、可训练参数、
     * 训练耗时、最佳检查点、GPU 型号等），展示在任务详情的「训练报告」里。
     */
    @TableField(value = "metrics", jdbcType = org.apache.ibatis.type.JdbcType.JAVA_OBJECT, typeHandler = ObjectNodeTypeHandler.class)
    private ObjectNode metrics;

    @TableField("status")
    private String status;

    /**
     * 总进度百分比 0-100
     */
    @TableField("progress")
    private Double progress;

    @TableField("current_epoch")
    private Integer currentEpoch;

    @TableField("train_loss")
    private Double trainLoss;

    @TableField("eval_loss")
    private Double evalLoss;

    @TableField("worker_id")
    private String workerId;

    /**
     * 领取代次（每次 claim 重新生成）：心跳/终态/停止都以 (id, epoch) 为准，
     * 僵死 Worker 被回收重派后，其迟到写入全部落空。
     */
    @TableField("worker_epoch")
    private String workerEpoch;

    /**
     * 协作式停止标志：管理端请求停止时对 running 行置 true，
     * Worker 在下一次 progress 回调的响应中读取到后自行中止并上报 stopped。
     */
    @TableField("stop_flag")
    private Boolean stopFlag;

    @TableField("fail_reason")
    private String failReason;

    /**
     * 训练日志文件相对路径（本地存储 local.base-data-path 下）
     */
    @TableField("log_path")
    private String logPath;

    @TableField("claim_time")
    private LocalDateTime claimTime;

    @TableField("heartbeat_time")
    private LocalDateTime heartbeatTime;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;
}
