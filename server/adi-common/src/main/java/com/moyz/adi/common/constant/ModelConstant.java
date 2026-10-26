package com.moyz.adi.common.constant;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 模型平台域常量（OPT-17 拆分：原 AdiConstant 顶层常量与模型相关嵌套类）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有引用无需改动。
 * 已删除零引用成员：SECRET_KEY_TYPE_*、OPENAI_MESSAGE_DONE_FLAG、DEFAULT_MODEL（gpt-3.5-turbo 硬编码）。
 */
public interface ModelConstant {

    org.slf4j.Logger MODEL_CONST_LOG = org.slf4j.LoggerFactory.getLogger(ModelConstant.class);

    Double LLM_TEMPERATURE_DEFAULT = 0.7D;

    /**
     * <p>Default fallback used only when a model has no explicit maxInputTokens configured.</p>
     */
    int LLM_MAX_INPUT_TOKENS_DEFAULT = 32768;

    String LLM_INPUT_TYPE_TEXT = "text";
    String LLM_INPUT_TYPE_IMAGE = "image";
    String LLM_INPUT_TYPE_AUDIO = "audio";
    String LLM_INPUT_TYPE_VIDEO = "video";

    String RESPONSE_FORMAT_TYPE_TEXT = "text";
    String RESPONSE_FORMAT_TYPE_JSON_OBJECT = "json_object";

    String W_FAILED = "FAILED";

    class ModelPlatform {
        private ModelPlatform() {
        }

        public static final String DEEPSEEK = "deepseek";
        public static final String OPENAI = "openai";
        public static final String DASHSCOPE = "dashscope";

        public static final String OLLAMA = "ollama";
        public static final String SILICONFLOW = "siliconflow";
        public static final String QIANWEN = "qianwen";

        // 获取所有公共静态常量（String类型的值）的列表
        public static List<String> getModelConstants() {
            List<String> list = new ArrayList<>();
            Class<ModelPlatform> clazz = ModelPlatform.class;
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    String value = (String) field.get(null);
                    list.add(value);
                } catch (ReflectiveOperationException e) {
                    MODEL_CONST_LOG.error("error", e);
                }

            }
            return list;
        }
    }

    class ModelType {
        private ModelType() {
        }

        public static final String TEXT = "text";
        public static final String IMAGE = "image";
        public static final String VISION = "vision";
        public static final String EMBEDDING = "embedding";
        public static final String RERANK = "rerank";
        public static final String ASR = "asr";
        public static final String TTS = "tts";

        public static List<String> getModelType() {
            List<String> list = new ArrayList<>();
            Class<ModelType> clazz = ModelType.class;
            for (Field field : clazz.getDeclaredFields()) {
                try {
                    String value = (String) field.get(null);
                    list.add(value);
                } catch (ReflectiveOperationException e) {
                    MODEL_CONST_LOG.error("error", e);
                }

            }
            return list;
        }
    }

    class SearchEngineName {
        private SearchEngineName() {
        }

        public static final String GOOGLE = "google";

        public static final String[] GOOGLE_COUNTRIES = {"cn", "af", "al", "dz", "as", "ad", "ao", "ai", "aq", "ag", "ar", "am", "aw", "au", "at", "az", "bs", "bh", "bd", "bb", "by", "be", "bz", "bj", "bm", "bt", "bo", "ba", "bw", "bv", "br", "io", "bn", "bg", "bf", "bi", "kh", "cm", "ca", "cv", "ky", "cf", "td", "cl", "cx", "cc", "co", "km", "cg", "cd", "ck", "cr", "ci", "hr", "cu", "cy", "cz", "dk", "dj", "dm", "do", "ec", "eg", "sv", "gq", "er", "ee", "et", "fk", "fo", "fj", "fi", "fr", "gf", "pf", "tf", "ga", "gm", "ge", "de", "gh", "gi", "gr", "gl", "gd", "gp", "gu", "gt", "gn", "gw", "gy", "ht", "hm", "va", "hn", "hk", "hu", "is", "in", "id", "ir", "iq", "ie", "il", "it", "jm", "jp", "jo", "kz", "ke", "ki", "kp", "kr", "kw", "kg", "la", "lv", "lb", "ls", "lr", "ly", "li", "lt", "lu", "mo", "mk", "mg", "mw", "my", "mv", "ml", "mt", "mh", "mq", "mr", "mu", "yt", "mx", "fm", "md", "mc", "mn", "ms", "ma", "mz", "mm", "na", "nr", "np", "nl", "an", "nc", "nz", "ni", "ne", "ng", "nu", "nf", "mp", "no", "om", "pk", "pw", "ps", "pa", "pg", "py", "pe", "ph", "pn", "pl", "pt", "pr", "qa", "re", "ro", "ru", "rw", "sh", "kn", "lc", "pm", "vc", "ws", "sm", "st", "sa", "sn", "rs", "sc", "sl", "sg", "sk", "si", "sb", "so", "za", "gs", "es", "lk", "sd", "sr", "sj", "sz", "se", "ch", "sy", "tw", "tj", "tz", "th", "tl", "tg", "tk", "to", "tt", "tn", "tr", "tm", "tc", "tv", "ug", "ua", "ae", "uk", "gb", "us", "um", "uy", "uz", "vu", "ve", "vn", "vg", "vi", "wf", "eh", "ye", "zm", "zw"};
        public static final String[] GOOGLE_LANGUAGES = {"zh-cn", "zh-tw", "af", "ak", "sq", "ws", "am", "ar", "hy", "az", "eu", "be", "bem", "bn", "bh", "xx-bork", "bs", "br", "bg", "bt", "km", "ca", "chr", "ny", "co", "hr", "cs", "da", "nl", "xx-elmer", "en", "eo", "et", "ee", "fo", "tl", "fi", "fr", "fy", "gaa", "gl", "ka", "de", "el", "kl", "gn", "gu", "xx-hacker", "ht", "ha", "haw", "iw", "hi", "hu", "is", "ig", "id", "ia", "ga", "it", "ja", "jw", "kn", "kk", "rw", "rn", "xx-klingon", "kg", "ko", "kri", "ku", "ckb", "ky", "lo", "la", "lv", "ln", "lt", "loz", "lg", "ach", "mk", "mg", "ms", "ml", "mt", "mv", "mi", "mr", "mfe", "mo", "mn", "sr-me", "my", "ne", "pcm", "nso", "no", "nn", "oc", "or", "om", "ps", "fa", "xx-pirate", "pl", "pt", "pt-br", "pt-pt", "pa", "qu", "ro", "rm", "nyn", "ru", "gd", "sr", "sh", "st", "tn", "crs", "sn", "sd", "si", "sk", "sl", "so", "es", "es-419", "su", "sw", "sv", "tg", "ta", "tt", "te", "th", "ti", "to", "lua", "tum", "tr", "tk", "tw", "ug", "uk", "ur", "uz", "vu", "vi", "cy", "wo", "xh", "yi", "yo", "zu"};
    }

    class TokenEstimator {
        public static String OPENAI = "openai";
        public static String HUGGING_FACE = "huggingface";
        public static String QWEN = "qwen";

        public static List<String> ALL = List.of(OPENAI, HUGGING_FACE, QWEN);
    }

    class EmbeddingModel {
        public static String ALL_MINILM_L6 = "local:all-minilm-l6-v2";
        public static String BGE_SMALL_ZH_V15 = "local:bge-small-zh-v1.5";
        public static int BGE_SMALL_ZH_V15_DIMENSION = 512;
        public static List<String> LOCAL_MODELS = List.of(ALL_MINILM_L6, BGE_SMALL_ZH_V15);

        /**
         * bge-m3 模型名（T7-05）：作为可选 embedding provider 接入。
         * <p>
         * langchain4j 未内置 bge-m3 的 ONNX 推理类（权重约 2GB，亦不适合随发行包分发），
         * 因此 bge-m3 走「OpenAI 协议兼容端点」这唯一的现实接入方式：由 Xinference /
         * vLLM / One-API 等以 /v1/embeddings 协议 serving，配置形如
         * {@code adi.embedding-model=<platform>:bge-m3}（platform 标记
         * is_openai_api_compatible，AiModel.properties.dimension 显式配维度）。
         * 本地 ONNX 路线留待 langchain4j 上游提供对应 EmbeddingModel 实现后再补分支，
         * 不自行伪造一个不可用的本地 provider。
         */
        public static String BGE_M3 = "bge-m3";
        public static int BGE_M3_DIMENSION = 1024;
        /**
         * bge-m3 支持 MRL 截断维度（1024 全精度 / 768 / 512 截断），配置其他维度时告警——
         * 截断维度会改变向量语义，与既有索引不可混用，必须是运维的有意选择。
         */
        public static List<Integer> BGE_M3_ALLOWED_DIMENSIONS = List.of(1024, 768, 512);
    }

    class McpConstant {
        public static final String TRANSPORT_TYPE_SSE = "sse";
        public static final String TRANSPORT_TYPE_STDIO = "stdio";
        public static final String TRANSPORT_TYPE_STREAMABLE_HTTP = "streamable_http";
        public static final String INSTALL_TYPE_REMOTE = "remote";
        public static final String INSTALL_TYPE_WASM = "wasm";
        public static final String INSTALL_TYPE_LOCAL = "local";
        public static final String INSTALL_TYPE_DOCKER = "docker";
    }

    class TtsConstant {

        /**
         * 语音合成器位置-客户端
         */
        public static final String SYNTHESIZER_CLIENT = "client";
        /**
         * 语音合成器位置-服务端
         */
        public static final String SYNTHESIZER_SERVER = "server";

        /**
         * 通义默认语音音色-龙小淳(知性积极女)
         */
        public static final String DASHSCOPE_DEFAULT_VOICE = "longxiaochun_v3";
        public static final String SILICONFLOW_DEFAULT_VOICE = "FunAudioLLM/CosyVoice2-0.5B:alex";
        public static final String OPENAI_DEFAULT_VOICE = "alloy";
    }

    class CustomChatRequestParameterKeys {
        private CustomChatRequestParameterKeys() {
        }

        /**
         * 是否开启思考模式，默认不开启
         */
        public static final String ENABLE_THINKING = "enable_thinking";
        /**
         * 是否启用联网搜索功能
         */
        public static final String ENABLE_WEB_SEARCH = "enable_web_search";
    }
}
