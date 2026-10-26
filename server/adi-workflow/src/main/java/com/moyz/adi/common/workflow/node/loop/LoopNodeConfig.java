package com.moyz.adi.common.workflow.node.loop;

import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * T9-02：Loop/Each 循环节点配置（nodeConfig 即 DSL）。
 * <pre>{@code
 * {
 *   "mode": "each",                      // each=对数组逐项执行 | while=按条件循环
 *   "items_param": "files",              // each 模式：承载 JSON 数组的输入参数名，空 = 默认输入
 *   "body_template": "处理第 {index} 项：{item}",   // 逐项执行的子链体（模板渲染）
 *   "condition_template": "{should_continue}",     // while 模式：每轮渲染，结果为 false 时停止
 *   "item_param_name": "item",           // 逐项渲染时的当前项变量名（默认 item）
 *   "max_iterations": 100,               // 最大次数上限（熔断，防死循环；缺省 100，上限 1000）
 *   "parallel": false,                   // each 模式是否并行执行（可选，默认串行）
 *   "max_parallel": 4                    // 并行度（parallel=true 时生效，缺省 4）
 * }
 * }</pre>
 * 最大次数上限复用 T6-10 预算思想：任一轮超过 {@code max_iterations} 即熔断停止，
 * 已产出的部分结果仍然聚合输出（stop_reason=max_iterations），不静默丢失。
 */
@Data
public class LoopNodeConfig {

    public static final String MODE_EACH = "each";
    public static final String MODE_WHILE = "while";

    /** 执行模式：each（数组逐项）| while（按条件循环） */
    @NotBlank
    private String mode = MODE_EACH;

    /** each 模式：承载 JSON 数组的输入参数名；空 = 使用默认输入参数 */
    @JsonProperty("items_param")
    private String itemsParam;

    /** 逐项/逐轮执行的模板（{item} / {index} / {iteration} 及上游变量占位） */
    @JsonProperty("body_template")
    private String bodyTemplate;

    /** while 模式：条件模板，渲染结果（trim 后）等于 false 时停止循环 */
    @JsonProperty("condition_template")
    private String conditionTemplate;

    /** 逐项渲染时的当前项变量名，默认 item */
    @JsonProperty("item_param_name")
    private String itemParamName = "item";

    /** 最大循环次数上限（熔断）；缺省 100，硬上限 1000 */
    @JsonProperty("max_iterations")
    @Min(value = 1, message = "max_iterations must be >= 1")
    @Max(value = 1000, message = "max_iterations must be <= 1000")
    private Integer maxIterations = 100;

    /** each 模式是否并行执行（可选，默认 false 串行，保持结果顺序稳定） */
    private Boolean parallel = false;

    /** 并行度上限（parallel=true 时生效），缺省 4 */
    @JsonProperty("max_parallel")
    @Min(value = 1, message = "max_parallel must be >= 1")
    @Max(value = 16, message = "max_parallel must be <= 16")
    private Integer maxParallel = 4;

    public boolean isEachMode() {
        return !MODE_WHILE.equalsIgnoreCase(mode);
    }

    /** 数组元素转文本：标量取 asText，对象/数组取规范化 JSON 串 */
    public static String itemToText(JsonNode item) {
        if (item.isValueNode()) {
            return item.asText();
        }
        return JsonUtil.toJson(item);
    }
}
