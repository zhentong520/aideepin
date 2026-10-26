package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 容错与降级路由配置（adi.llm.*，T6-08/T6-09）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Llm} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Llm {

    /**
     * 模型降级路由开关（adi.llm.fallback-enabled，默认 true）。
     * RETRIABLE 错误按降级链重试备用模型；PERMANENT 错误直接报错不浪费额度（T6-09）。
     * <p>
     * LLM fallback routing switch (T6-09). RETRIABLE errors walk the fallback chain;
     * PERMANENT errors (401/403/400/config) fail fast without burning quota.
     */
    private boolean fallbackEnabled = true;

    /**
     * 降级模型链（adi.llm.fallback-models，按顺序尝试的备用模型名列表）。
     * 留空表示未配置降级链（只做同模型重试，不跨模型降级）。
     */
    private List<String> fallbackModels = new ArrayList<>();

    /**
     * streaming 首 token 前的同模型重试次数（adi.llm.stream-same-model-retries，默认 1）。
     * ⚠️ 首 token 输出后绝不重试（会产生重复输出），此值只作用于首 token 之前（T6-08）。
     */
    @Min(value = 0, message = "adi.llm.stream-same-model-retries 必须 >= 0")
    private int streamSameModelRetries = 1;
}
