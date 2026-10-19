package com.moyz.adi.common.dto.finetune;

import lombok.Data;

/**
 * 创建微调训练任务请求。
 * <p>
 * 字段分五组，与前端创建表单的分区一一对应：
 * <ul>
 *   <li>基础：name / datasetUuid / baseModelName / method / preset</li>
 *   <li>LoRA 结构：loraRank / loraAlpha / loraDropout / targetModules / useRslora / useDora</li>
 *   <li>优化器：learningRate / numEpochs / batchSize / gradientAccumulation / lrSchedulerType /
 *       warmupRatio / weightDecay / maxGradNorm / optim</li>
 *   <li>数据：maxSeqLen(cutoff_len) / valRatio / packing / neftuneNoiseAlpha</li>
 *   <li>运行与导出：gradientCheckpointing / flashAttn / seed / loggingSteps / saveSteps /
 *       evalSteps / earlyStoppingPatience / exportMerged / exportGguf / ggufQuant / template</li>
 * </ul>
 * 全部可空：缺省值（与推荐预设一致）在 FinetuneJobService.buildHyperParams 里统一填充，
 * 因此前端只传用户改过的字段也能得到一份完整、可复现的超参快照。
 */
@Data
public class FinetuneJobCreateReq {

    private String name;

    private String datasetUuid;

    /**
     * 基座模型名，如 Qwen2.5-7B-Instruct（LLaMA-Factory model name 或 HF id / 本地路径）
     */
    private String baseModelName;

    /**
     * lora / qlora
     */
    private String method;

    /**
     * 参数预设 key（balanced / quality / chinese-chat / knowledge-qa / low-vram / fast-preview）。
     * 仅作来源标记随超参一起存档，便于复现与统计；参数本身由前端按预设填充后显式提交。
     */
    private String preset;

    // ===== LoRA 结构 =====

    /**
     * LoRA 秩 r：越大容量越强、显存与过拟合风险越高。常用 8 / 16 / 32 / 64。
     */
    private Integer loraRank;

    /**
     * LoRA 缩放系数 alpha：通常取 2×rank。
     */
    private Integer loraAlpha;

    /**
     * LoRA dropout，0~0.5，小数据集可调高抑制过拟合。
     */
    private Double loraDropout;

    /**
     * 注入的目标模块：all=全部线性层，或逗号分隔白名单（q_proj,k_proj,v_proj,o_proj,gate_proj,up_proj,down_proj）
     */
    private String targetModules;

    /**
     * 启用 rsLoRA：高秩（rank>=64）时稳定缩放、提升收敛质量
     */
    private Boolean useRslora;

    /**
     * 启用 DoRA：权重分解式 LoRA，同等秩下效果更好但更慢、更吃显存
     */
    private Boolean useDora;

    // ===== 优化器 =====

    /**
     * 学习率。SFT + LoRA 常用 1e-4 ~ 2e-4；过大易发散，过小收敛不足。
     */
    private Double learningRate;

    /**
     * 训练轮数（epoch）
     */
    private Integer numEpochs;

    /**
     * 单卡批次大小（per_device_train_batch_size）
     */
    private Integer batchSize;

    /**
     * 梯度累积步数：有效批次 = batchSize × gradientAccumulation
     */
    private Integer gradientAccumulation;

    /**
     * 学习率调度器：linear / cosine / cosine_with_restarts / polynomial / constant / constant_with_warmup
     */
    private String lrSchedulerType;

    /**
     * 预热比例 0~0.5，通常 0.03~0.1
     */
    private Double warmupRatio;

    /**
     * 权重衰减，通常 0~0.1
     */
    private Double weightDecay;

    /**
     * 梯度裁剪阈值，通常 0.5~1.0
     */
    private Double maxGradNorm;

    /**
     * 优化器：adamw_torch / adamw_hf / paged_adamw_8bit / adafactor
     */
    private String optim;

    // ===== 数据 =====

    /**
     * 截断长度 cutoff_len：样本超过即被截断，建议覆盖 95% 样本长度
     */
    private Integer maxSeqLen;

    /**
     * 验证集比例 0~0.5（0 表示不切分、关闭 eval）
     */
    private Double valRatio;

    /**
     * 样本打包（packing）：把多条短样本拼进同一序列，训练吞吐显著提升，短样本场景强烈建议开启
     */
    private Boolean packing;

    /**
     * NEFTune 噪声 alpha（0=关闭）。5~10 可提升指令跟随与生成多样性，代价是训练略慢。
     */
    private Double neftuneNoiseAlpha;

    // ===== 运行与稳定性 =====

    /**
     * 梯度检查点：显存换时间，可把激活显存降到约 1/3，长序列训练几乎必开
     */
    private Boolean gradientCheckpointing;

    /**
     * 注意力实现：auto / fa2 / sdpa / disabled
     */
    private String flashAttn;

    /**
     * 随机种子，保证可复现
     */
    private Integer seed;

    /**
     * 日志打印步数；<=0 表示按总步数自动推导
     */
    private Integer loggingSteps;

    /**
     * 断点保存步数；<=0 表示按总步数自动推导
     */
    private Integer saveSteps;

    /**
     * 评估步数；<=0 表示按总步数自动推导
     */
    private Integer evalSteps;

    /**
     * 早停耐心（按 eval_loss 判断，0=关闭）。可在过拟合前停下，是提升成品质量的关键开关。
     */
    private Integer earlyStoppingPatience;

    // ===== 量化与导出 =====

    /**
     * QLoRA 量化位宽：4 / 8
     */
    private Integer quantBit;

    /**
     * 量化后端：bitsandbytes / hqq
     */
    private String quantMethod;

    /**
     * 训练结束是否合并导出完整模型目录（merged）
     */
    private Boolean exportMerged;

    /**
     * 训练结束是否转换 GGUF（供 Ollama / llama.cpp 直接部署）
     */
    private Boolean exportGguf;

    /**
     * GGUF 量化档位：q4_k_m / q5_k_m / q8_0 / f16
     */
    private String ggufQuant;

    /**
     * 对话模板覆盖（留空按基座模型名自动推断，如 qwen / llama3 / glm4）
     */
    private String template;
}
