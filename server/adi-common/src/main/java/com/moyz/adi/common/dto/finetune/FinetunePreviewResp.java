package com.moyz.adi.common.dto.finetune;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 训练参数预演结果：前端创建表单在参数变化时调用，用同一套计算逻辑给出
 * 「跑得动吗、要多久、参数有没有坑」的即时反馈。
 * <p>
 * 与 create 完全复用同一份默认值填充与派生逻辑，因此页面上看到的步数/显存
 * 就是任务创建后写入 hyper_params 的结果，不会出现「显示 500 步、实际 1000 步」。
 */
@Data
@Builder
public class FinetunePreviewResp {

    /** 参与训练的样本数（已扣除验证集） */
    private Integer trainSamples;

    /** 验证集样本数 */
    private Integer evalSamples;

    /** 有效批次 = batchSize × gradientAccumulation */
    private Integer effectiveBatchSize;

    /** 总优化步数 = ceil(trainSamples × epochs / 有效批次) */
    private Integer totalSteps;

    /** 实际生效的日志步数（自动派生后的值） */
    private Integer loggingSteps;

    /** 实际生效的保存步数 */
    private Integer saveSteps;

    /** 实际生效的评估步数（0=关闭评估） */
    private Integer evalSteps;

    /** 落盘的超参快照（与任务创建后的 hyper_params 一致，供前端「查看最终配置」） */
    private Map<String, Object> hyperParams;

    /** 显存粗估 */
    private Vram vram;

    /** 参数体检结论：level = info / warn / error */
    private List<Warning> warnings;

    @Data
    @Builder
    public static class Vram {

        /** 基座权重占用（GB） */
        private Double weightGb;

        /** 激活值占用（GB），受 batch / 序列长度 / 梯度检查点影响最大 */
        private Double activationGb;

        /** 框架与通信等固定开销（GB） */
        private Double overheadGb;

        /** 当前配置估算峰值显存（GB） */
        private Double totalGb;

        /** 同参数改用 QLoRA 的峰值（GB），用于提示降档空间 */
        private Double qloraTotalGb;

        /** 解析出的基座参数量（B），null=无法识别 */
        private Double paramsB;

        /** 单卡适配表 */
        private List<Tier> tiers;

        private String level;
    }

    @Data
    @Builder
    public static class Tier {
        private String gpu;
        private Integer vramGb;
        /** 该卡按当前参数是否放得下 */
        private Boolean ok;
        /** 建议手段：如「开梯度检查点 / 降 cutoff_len / 换 QLoRA」 */
        private String adviceKey;
    }

    @Data
    @Builder
    public static class Warning {
        /** info / warn / error */
        private String level;
        /** i18n key 后缀，前端拼 finetune.warn.{code} */
        private String code;
        private Map<String, Object> args;
    }
}
