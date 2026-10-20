package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * T9-04：Code 代码节点可观测指标。
 * <p>
 * language / timeoutSeconds 记录沙箱执行参数，resultLength 反映产出体量，
 * exitCode 是脚本退出码（非 0 即失败，与 Skill 脚本约定一致）。
 * <p>
 * T9-04 Code node observability metrics.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class CodeMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 代码语言（当前仅 python） | Code language (python only for now)
     */
    private String language;

    /**
     * 沙箱超时（秒） | Sandbox timeout (seconds)
     */
    private Integer timeoutSeconds;

    /**
     * 输出长度（字符） | Output length (chars)
     */
    private Integer resultLength;

    /**
     * 脚本退出码 | Script exit code
     */
    private Integer exitCode;
}
