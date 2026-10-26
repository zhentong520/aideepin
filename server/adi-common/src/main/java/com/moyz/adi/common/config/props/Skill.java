package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 技能注入配置（adi.skill.*，T6-07）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Skill} 嵌套类提为顶层类，Spring 绑定键不变。
 * 注意与实体类 {@code com.moyz.adi.common.entity.Skill} 同名但职责无关。
 */
@Data
public class Skill {

    /**
     * 技能描述按需注入开关（adi.skill.on-demand-injection-enabled，默认 true）。
     * true 时技能清单按当前任务相关性检索后只注入 top-k 条描述，不再全量进 prompt；
     * false 时全量注入（历史行为）。
     * <p>
     * On-demand skill injection switch (T6-07). When on, only the top-k most
     * task-relevant skill descriptions are injected into the prompt.
     */
    private boolean onDemandInjectionEnabled = true;

    /**
     * 按需注入的最大技能条数（adi.skill.injection-top-k，默认 8）。
     * 绑定技能数不超过该值时全量注入（不改变小场景行为）。
     */
    @Min(value = 1, message = "adi.skill.injection-top-k 必须 >= 1")
    private int injectionTopK = 8;
}
