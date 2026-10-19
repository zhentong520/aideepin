package com.moyz.adi.common.workflow.node.skill;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 技能节点配置。
 * <p>
 * Skill node configuration — read a skill's instruction body, or run one of its scripts.
 * </p>
 * <p>
 * nodeConfig 示例：
 * <pre>{@code
 * {
 *   "skill_uuid": "1a2b3c...",        // 与 skill_name 二选一，uuid 优先
 *   "skill_name": "pdf-summary",
 *   "mode": "script",                  // instruction（读指令正文）/ script（执行脚本）
 *   "script": "scripts/main.py",       // mode=script 时必填
 *   "args": "{input}",                 // mode=script 时的脚本参数，支持 {name} 占位符
 *   "output_format": "text"
 * }
 * }</pre>
 * </p>
 */
@Data
public class SkillNodeConfig {

    /**
     * 技能 uuid（与 skillName 二选一，同时存在时以 uuid 为准）
     * Skill uuid (mutually exclusive with skillName; uuid wins when both are present).
     */
    @JsonProperty("skill_uuid")
    private String skillUuid;

    /**
     * 技能名（如 pdf-summary）
     * Skill name.
     */
    @JsonProperty("skill_name")
    private String skillName;

    /**
     * 执行模式：instruction = 输出技能指令正文；script = 执行技能脚本
     * Mode: instruction (return the skill instruction body) or script (run a skill script).
     */
    private String mode = "instruction";

    /**
     * 脚本相对路径（mode=script 时必填），如 scripts/main.py；仅支持 .py
     * Script path relative to the skill directory (required when mode=script).
     */
    private String script;

    /**
     * 脚本参数文本（mode=script 时使用），支持 {name} 占位符引用上游参数
     * Script argument text; {name} placeholders reference upstream params.
     */
    private String args;
}
