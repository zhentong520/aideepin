package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * External API 限流与配额配置（adi.ext.*，T1-09）。
 * <p>
 * 三层限制：单 Key QPS（Redis 固定 1 秒窗口）、单 Key 日调用次数（Redis INCR + 当日过期）、
 * 单 Key 日 token 配额（累计 prompt+completion，由聊天类 ext 接口在 LLM 调用返回处上报，
 * 纯检索类接口不产生消耗）。超限一律 HTTP 429 + Retry-After。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Ext} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Ext {

    /**
     * 总开关（铁律 10）：false 时三层限制全部关闭，行为与历史版本一致 | Master switch.
     */
    private boolean enabled = true;

    /**
     * 单 Key 每秒最大请求数（adi.ext.qps-limit，默认 5）| Per-key QPS limit.
     */
    @Min(value = 1, message = "adi.ext.qps-limit 必须 >= 1")
    private int qpsLimit = 5;

    /**
     * 单 Key 单日最大调用次数（adi.ext.daily-request-quota，默认 10000）| Per-key daily request quota.
     */
    @Min(value = 1, message = "adi.ext.daily-request-quota 必须 >= 1")
    private int dailyRequestQuota = 10000;

    /**
     * 单 Key 单日 token 配额（adi.ext.daily-token-quota，默认 0 = 不限制）。
     * 聊天类 ext 接口在 LLM 返回后调 {@code ExtApiQuotaService.recordTokens} 上报；
     * 0 表示不限制（纯检索接口不消耗 token）。
     */
    @Min(value = 0, message = "adi.ext.daily-token-quota 必须 >= 0（0 表示不限制）")
    private long dailyTokenQuota = 0;

    /**
     * 对外检索接口单次允许的 topK 上限（adi.ext.max-top-k，默认 50）| Max topK accepted by the retrieve API.
     */
    @Min(value = 1, message = "adi.ext.max-top-k 必须 >= 1")
    private int maxTopK = 50;
}
