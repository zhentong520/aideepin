package com.moyz.adi.common.service.finetune;

import com.moyz.adi.common.dto.finetune.FinetuneMetaResp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 微调参数规范：默认值、取值范围、基座模型目录与参数预设的唯一事实来源。
 * <p>
 * 后端校验、默认值填充、前端表单边界、preset 一键套用全部从这里取值，
 * 因此「页面显示的推荐值」与「任务实际落库的超参」不会漂移。
 * <p>
 * 默认值的选择依据（SFT + LoRA 的实践经验，而非随手填）：
 * <ul>
 *   <li>学习率 1e-4：LoRA SFT 的稳妥区间是 1e-4~2e-4。原默认 5e-4 偏大，小数据集上
 *       容易在 1 个 epoch 内就把 loss 压到很低但泛化变差（过拟合式记忆）。</li>
 *   <li>rank 16 / alpha 32：alpha=2×rank 是社区共识的起点；rank 8 在中文指令数据上
 *       常出现「学得动格式、学不动风格」的情况。</li>
 *   <li>梯度检查点默认开：把激活显存压到约 1/3，是 7B 在 24G 卡上跑得动的关键，代价约 20% 速度。</li>
 *   <li>cutoff_len 2048：覆盖绝大多数中文对话样本，超过则显存与耗时线性上升。</li>
 *   <li>val_ratio 0.05：切一点验证集才能看到 eval_loss 是否回升，是判断过拟合的唯一依据。</li>
 * </ul>
 */
public final class FinetuneParamSpec {

    private FinetuneParamSpec() {
    }

    public static final String METHOD_LORA = "lora";
    public static final String METHOD_QLORA = "qlora";

    public static final Set<String> METHODS = Set.of(METHOD_LORA, METHOD_QLORA);
    public static final Set<String> SCHEDULERS = Set.of(
            "linear", "cosine", "cosine_with_restarts", "polynomial", "constant", "constant_with_warmup");
    public static final Set<String> OPTIMS = Set.of(
            "adamw_torch", "adamw_hf", "paged_adamw_8bit", "adafactor");
    public static final Set<String> GGUF_QUANTS = Set.of("q4_k_m", "q5_k_m", "q8_0", "f16");
    public static final Set<String> QUANT_METHODS = Set.of("bitsandbytes", "hqq");
    public static final Set<String> FLASH_ATTNS = Set.of("auto", "fa2", "sdpa", "disabled");
    public static final Set<Integer> QUANT_BITS = Set.of(4, 8);

    /** 参数取值范围：key -> {min, max, step}，前端直接绑定到 n-input-number */
    public static final Map<String, double[]> LIMITS = new LinkedHashMap<>();

    static {
        LIMITS.put("lora_rank", new double[]{1, 256, 1});
        LIMITS.put("lora_alpha", new double[]{1, 512, 1});
        LIMITS.put("lora_dropout", new double[]{0, 0.5, 0.01});
        LIMITS.put("learning_rate", new double[]{1e-6, 1e-2, 1e-5});
        LIMITS.put("num_epochs", new double[]{1, 100, 1});
        LIMITS.put("batch_size", new double[]{1, 64, 1});
        LIMITS.put("gradient_accumulation", new double[]{1, 128, 1});
        LIMITS.put("warmup_ratio", new double[]{0, 0.5, 0.01});
        LIMITS.put("weight_decay", new double[]{0, 1, 0.001});
        LIMITS.put("max_grad_norm", new double[]{0, 10, 0.1});
        LIMITS.put("cutoff_len", new double[]{128, 8192, 128});
        LIMITS.put("val_ratio", new double[]{0, 0.5, 0.01});
        LIMITS.put("neftune_noise_alpha", new double[]{0, 20, 0.5});
        LIMITS.put("seed", new double[]{0, 999999, 1});
        LIMITS.put("logging_steps", new double[]{0, 10000, 1});
        LIMITS.put("save_steps", new double[]{0, 100000, 1});
        LIMITS.put("eval_steps", new double[]{0, 100000, 1});
        LIMITS.put("early_stopping_patience", new double[]{0, 20, 1});
        LIMITS.put("quant_bit", new double[]{4, 8, 4});
    }

    /**
     * 默认超参（= 均衡预设）。每次调用返回可变的副本，避免调用方互相污染。
     */
    public static Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lora_rank", 16);
        m.put("lora_alpha", 32);
        m.put("lora_dropout", 0.05);
        m.put("lora_target", "all");
        m.put("use_rslora", false);
        m.put("use_dora", false);
        m.put("learning_rate", 1.0e-4);
        m.put("num_epochs", 3);
        m.put("batch_size", 2);
        m.put("gradient_accumulation", 8);
        m.put("lr_scheduler_type", "cosine");
        m.put("warmup_ratio", 0.1);
        m.put("weight_decay", 0.01);
        m.put("max_grad_norm", 1.0);
        m.put("optim", "adamw_torch");
        m.put("cutoff_len", 2048);
        m.put("val_ratio", 0.05);
        m.put("packing", false);
        m.put("neftune_noise_alpha", 0);
        m.put("gradient_checkpointing", true);
        m.put("flash_attn", "auto");
        m.put("seed", 42);
        m.put("logging_steps", 0);
        m.put("save_steps", 0);
        m.put("eval_steps", 0);
        m.put("early_stopping_patience", 0);
        m.put("quant_bit", 4);
        m.put("quant_method", "bitsandbytes");
        m.put("export_merged", true);
        m.put("export_gguf", true);
        m.put("gguf_quant", "q4_k_m");
        m.put("template", "");
        return m;
    }

    /**
     * 参数预设。key 对应的中文/英文名称与说明放在前端 i18n（finetune.preset.{key}.name/desc），
     * 这里只保留与训练效果直接相关的数值，避免中英文双份维护出现偏差。
     */
    public static List<FinetuneMetaResp.Preset> presets() {
        List<FinetuneMetaResp.Preset> list = new ArrayList<>();
        list.add(preset("balanced", METHOD_LORA, Map.of()));
        list.add(preset("quality", METHOD_LORA, Map.of(
                "lora_rank", 32, "lora_alpha", 64, "learning_rate", 8.0e-5, "num_epochs", 5,
                "gradient_accumulation", 16, "cutoff_len", 3072, "warmup_ratio", 0.05,
                "early_stopping_patience", 3, "neftune_noise_alpha", 5)));
        list.add(preset("chinese-chat", METHOD_LORA, Map.of(
                "lora_rank", 16, "lora_alpha", 32, "learning_rate", 1.0e-4, "num_epochs", 3,
                "cutoff_len", 2048, "gradient_accumulation", 8, "packing", true)));
        list.add(preset("knowledge-qa", METHOD_LORA, Map.of(
                "lora_rank", 32, "lora_alpha", 64, "learning_rate", 5.0e-5, "num_epochs", 5,
                "cutoff_len", 3072, "warmup_ratio", 0.03, "weight_decay", 0.02,
                "val_ratio", 0.1, "early_stopping_patience", 2)));
        list.add(preset("low-vram", METHOD_QLORA, Map.of(
                "lora_rank", 8, "lora_alpha", 16, "batch_size", 1, "gradient_accumulation", 16,
                "cutoff_len", 1024, "packing", true, "gradient_checkpointing", true,
                "optim", "paged_adamw_8bit", "quant_bit", 4)));
        list.add(preset("fast-preview", METHOD_LORA, Map.of(
                "lora_rank", 8, "lora_alpha", 16, "learning_rate", 2.0e-4, "num_epochs", 1,
                "gradient_accumulation", 4, "cutoff_len", 1024, "val_ratio", 0.02,
                "early_stopping_patience", 0, "export_gguf", false)));
        return list;
    }

    private static FinetuneMetaResp.Preset preset(String key, String method, Map<String, Object> params) {
        Map<String, Object> merged = defaults();
        merged.putAll(params);
        return FinetuneMetaResp.Preset.builder().key(key).method(method).params(merged).build();
    }

    /**
     * 基座模型目录。template 为 LLaMA-Factory 的对话模板名；Worker 侧若发现该模板不在
     * 当前 LLaMA-Factory 版本中，会回退为自动识别并打日志（见 real_trainer.py）。
     */
    public static List<FinetuneMetaResp.BaseModel> baseModels() {
        List<FinetuneMetaResp.BaseModel> list = new ArrayList<>();
        list.add(model("Qwen2.5-0.5B-Instruct", 0.5, "qwen", 896, "tiny", false));
        list.add(model("Qwen2.5-1.5B-Instruct", 1.5, "qwen", 1536, "tiny", false));
        list.add(model("Qwen2.5-3B-Instruct", 3.0, "qwen", 2048, "cnSmall", false));
        list.add(model("Qwen2.5-7B-Instruct", 7.0, "qwen", 3584, "cnBest", true));
        list.add(model("Qwen2.5-14B-Instruct", 14.0, "qwen", 5120, "cnStrong", false));
        list.add(model("Qwen2.5-32B-Instruct", 32.0, "qwen", 5120, "cnStrong", false));
        list.add(model("Qwen2.5-72B-Instruct", 72.0, "qwen", 8192, "cnStrong", false));
        list.add(model("Qwen3-4B", 4.0, "qwen3", 2560, "cnNew", false));
        list.add(model("Qwen3-8B", 8.0, "qwen3", 4096, "cnNew", false));
        list.add(model("Qwen3-14B", 14.0, "qwen3", 5120, "cnNew", false));
        list.add(model("DeepSeek-R1-Distill-Qwen-7B", 7.0, "qwen", 3584, "reasoning", false));
        list.add(model("Llama-3.1-8B-Instruct", 8.0, "llama3", 4096, "enStrong", false));
        list.add(model("Llama-3.2-3B-Instruct", 3.0, "llama3", 3072, "enSmall", false));
        list.add(model("Mistral-7B-Instruct-v0.3", 7.0, "mistral", 4096, "enStrong", false));
        list.add(model("glm-4-9b-chat", 9.0, "glm4", 4096, "cnStrong", false));
        list.add(model("Yi-1.5-9B-Chat", 9.0, "yi", 4096, "cnStrong", false));
        list.add(model("Baichuan2-7B-Chat", 7.0, "baichuan2", 4096, "cnClassic", false));
        list.add(model("internlm2_5-7b-chat", 7.0, "intern2", 4096, "cnClassic", false));
        list.add(model("gemma-2-9b-it", 9.0, "gemma", 3584, "enStrong", false));
        list.add(model("Phi-3.5-mini-instruct", 3.8, "phi", 3072, "enSmall", false));
        return list;
    }

    private static FinetuneMetaResp.BaseModel model(String name, double paramsB, String template,
                                                    int hiddenSize, String noteKey, boolean recommended) {
        return FinetuneMetaResp.BaseModel.builder()
                .name(name).paramsB(paramsB).template(template).hiddenSize(hiddenSize)
                .noteKey(noteKey).recommended(recommended).build();
    }

    /**
     * 从模型名解析参数量（B）：先查目录，再按名字里的 "7B" / "1.5B" 猜，猜不到返回 null
     * （null 时前端跳过显存估算并提示手填参数量）。
     */
    public static Double resolveParamsB(String baseModelName) {
        if (baseModelName == null || baseModelName.isBlank()) {
            return null;
        }
        for (FinetuneMetaResp.BaseModel m : baseModels()) {
            if (m.getName().equalsIgnoreCase(baseModelName.trim())) {
                return m.getParamsB();
            }
        }
        // 目录外的模型（本地路径 / 自定义 HF id）：从名字里抠 "13B"、"1.5b" 这类数字
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?<![0-9.])(\\d+(?:\\.\\d+)?)\\s*[bB](?![a-zA-Z0-9])")
                .matcher(baseModelName);
        if (matcher.find()) {
            try {
                double v = Double.parseDouble(matcher.group(1));
                return (v > 0 && v <= 1000) ? v : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * 隐藏层维度：目录外的模型按参数量立方根近似（hidden ≈ 5200 × (paramsB/7)^(1/3) 的
     * 经验缩放，7B 约 3584），仅用于激活显存的粗估。
     */
    public static int resolveHiddenSize(String baseModelName, double paramsB) {
        if (baseModelName != null) {
            for (FinetuneMetaResp.BaseModel m : baseModels()) {
                if (m.getName().equalsIgnoreCase(baseModelName.trim())) {
                    return m.getHiddenSize();
                }
            }
        }
        int hidden = (int) Math.round(5200 * Math.cbrt(paramsB / 7.0) / 256.0) * 256;
        return Math.max(512, Math.min(16384, hidden));
    }
}
