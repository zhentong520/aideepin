package com.moyz.adi.common.workflow.metrics;

import com.moyz.adi.common.workflow.NodeExecutionMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 技能节点可观测指标 | Skill node observability metrics
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetrics extends NodeExecutionMetrics {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 技能名 | Skill name
     */
    private String skillName;

    /**
     * 执行模式：instruction / script | Execution mode
     */
    private String mode;

    /**
     * 执行的脚本相对路径（mode=script 时） | Script path when mode=script
     */
    private String script;

    /**
     * 返回内容长度（字符） | Length of the returned text (chars)
     */
    private Integer resultLength;
}
