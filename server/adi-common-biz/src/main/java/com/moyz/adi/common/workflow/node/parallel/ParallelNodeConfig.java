package com.moyz.adi.common.workflow.node.parallel;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * T9-03：Parallel 并行节点配置（nodeConfig 即 DSL）。
 * <pre>{@code
 * {
 *   "branches": [
 *     {"name": "summary",  "template": "摘要：{input}"},
 *     {"name": "translate", "template": "翻译：{input}"},
 *     {"name": "slow-mock", "template": "慢分支", "delay_ms": 5000}   // 故障注入位：模拟慢分支
 *   ],
 *   "join": "all",          // all=全成功才算成功 | any=任一成功即成功
 *   "timeout_seconds": 60,  // 单分支等待上限，超时按失败分支处理（缺省 60，上限 300）
 *   "max_parallel": 4       // 并发线程数（缺省 4，上限 16）
 * }
 * }</pre>
 * 分支体是模板（渲染即执行），失败定义 = 渲染抛异常或超时；
 * {@code delay_ms} 是显式的故障注入位，用于验证「分支互不阻塞」与「超时」语义（生产一般不配）。
 */
@Data
public class ParallelNodeConfig {

    public static final String JOIN_ALL = "all";
    public static final String JOIN_ANY = "any";

    @Valid
    @NotEmpty(message = "branches must not be empty")
    private List<Branch> branches;

    /** join 语义：all（全成功）| any（任一成功） */
    private String join = JOIN_ALL;

    /** 单分支等待上限（秒），超时按失败处理 */
    @JsonProperty("timeout_seconds")
    @Min(value = 1, message = "timeout_seconds must be >= 1")
    @Max(value = 300, message = "timeout_seconds must be <= 300")
    private Integer timeoutSeconds = 60;

    /** 并发线程数上限，缺省 4 */
    @JsonProperty("max_parallel")
    @Min(value = 1, message = "max_parallel must be >= 1")
    @Max(value = 16, message = "max_parallel must be <= 16")
    private Integer maxParallel = 4;

    public boolean isJoinAny() {
        return JOIN_ANY.equalsIgnoreCase(join);
    }

    @Data
    public static class Branch {

        /** 分支名（输出 key，重复时自动加序号去重） */
        private String name;

        /** 分支体模板（渲染 = 执行；引用缺失变量会抛错 → 该分支失败） */
        private String template;

        /** 故障注入：分支执行前模拟耗时（毫秒），用于验证并行/超时语义；生产不配 */
        @JsonProperty("delay_ms")
        @Min(0)
        @Max(300000)
        private Integer delayMs = 0;
    }
}
