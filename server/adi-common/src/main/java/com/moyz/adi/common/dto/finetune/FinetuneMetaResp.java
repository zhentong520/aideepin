package com.moyz.adi.common.dto.finetune;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 微调元数据：基座模型目录、参数预设、默认超参与取值边界。
 * <p>
 * 前端创建表单完全由它驱动——参数的取值范围与后端校验同源，预设也由后端下发，
 * 避免「页面能填 512、后端拒绝 256」这类前后端打架。
 */
@Data
@Builder
public class FinetuneMetaResp {

    /** 基座模型目录（含对话模板与参数量，前端据此推算显存） */
    private List<BaseModel> baseModels;

    /** 参数预设 */
    private List<Preset> presets;

    /** 默认超参（未选预设时的初值，与后端 buildHyperParams 的缺省值一致） */
    private Map<String, Object> defaults;

    /** 参数取值范围：key -> [min, max]（浮点参数含 step） */
    private Map<String, List<Double>> limits;

    @Data
    @Builder
    public static class BaseModel {

        /** 传给 LLaMA-Factory 的 model_name_or_path（HF id 或本地路径） */
        private String name;

        /** 参数量（B），用于显存推算 */
        private Double paramsB;

        /** 对话模板名（LLaMA-Factory template） */
        private String template;

        /** 隐藏层维度，用于激活显存推算 */
        private Integer hiddenSize;

        /** 是否推荐作为起步基座 */
        private Boolean recommended;

        /** 中文能力提示（i18n key 后缀，前端拼 finetune.modelNote.{key}） */
        private String noteKey;
    }

    @Data
    @Builder
    public static class Preset {

        /** 预设 key：balanced / quality / chinese-chat / knowledge-qa / low-vram / fast-preview */
        private String key;

        /** 该预设推荐的训练方式 */
        private String method;

        /** 预设超参覆盖项（key 为 hyper_params 的 snake_case 键） */
        private Map<String, Object> params;
    }
}
