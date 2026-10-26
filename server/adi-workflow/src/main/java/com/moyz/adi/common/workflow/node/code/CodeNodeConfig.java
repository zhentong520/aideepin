package com.moyz.adi.common.workflow.node.code;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * T9-04：Code 代码节点配置（nodeConfig 即 DSL）。
 * <pre>{@code
 * {
 *   "language": "python",           // 仅 python（复用 Skill 沙箱的解释器白名单）
 *   "code": "import os\nprint(os.environ['SKILL_SCRIPT_ARGS'])",
 *   "timeout_seconds": 60           // 沙箱超时，与 Skill 一致（缺省 60，上限 60）
 * }
 * }</pre>
 * 代码节点不新造执行器：代码写入专用沙箱目录后，
 * 完全复用 Skill 节点的 {@code SkillScriptRunner}（固定参数列表、无 shell、
 * 环境变量透传、超时强杀、10KB 输出截断、仅 .py）。
 * 输入输出映射：上游输入序列化为 JSON 写入环境变量 {@code SKILL_SCRIPT_ARGS}
 * （与 Skill 节点 args 透传同一通道）；stdout 作为节点输出。
 */
@Data
public class CodeNodeConfig {

    public static final String LANGUAGE_PYTHON = "python";

    /** 代码语言，当前仅 python（Skill 沙箱解释器白名单） */
    @NotBlank
    private String language = LANGUAGE_PYTHON;

    /** 代码正文（作为沙箱任务执行） */
    @NotBlank
    private String code;

    /** 沙箱超时（秒），与 Skill 脚本一致（缺省 60，上限 60） */
    @JsonProperty("timeout_seconds")
    @Min(value = 1, message = "timeout_seconds must be >= 1")
    @Max(value = 60, message = "timeout_seconds must be <= 60 (same as skill sandbox)")
    private Integer timeoutSeconds = 60;
}
