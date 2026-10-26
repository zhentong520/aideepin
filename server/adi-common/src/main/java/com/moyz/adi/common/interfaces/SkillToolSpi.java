package com.moyz.adi.common.interfaces;

import com.moyz.adi.common.entity.Skill;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;

import java.util.List;
import java.util.Map;

/**
 * Agent Skills 工具的 SPI（OPT-15 批3 接口倒置）。
 * <p>
 * 实现在业务层（adi-common-biz 的 SkillToolService，依赖 CharacterService/SkillService），
 * 模型接入域（adi-model 的 AbstractLLMService）只面向本接口装配工具，
 * 避免 model → biz 的反向依赖。运行时经 Spring 按类型解析唯一实现。
 */
public interface SkillToolSpi {

    /**
     * 技能工具包：注入 prompt 的技能清单文本 + 工具规格 + name→Skill 映射。
     */
    record SkillBundle(String manifest, List<ToolSpecification> toolSpecifications, Map<String, Skill> skills) {
    }

    /**
     * 为一组技能构造工具执行器（read_skill / read_skill_file / run_skill_script）。
     */
    ToolExecutor executorFor(Map<String, Skill> skills);

    /**
     * create_skill 工具定义（常驻注册，不依赖角色已绑定技能）。
     */
    ToolSpecification buildCreateSkillSpecification();

    /**
     * 执行 create_skill。userId/characterUuid 由对话装配层显式传入
     * （工具在 LLM 回调线程执行，ThreadContext 不保证可达）。
     */
    String executeCreateSkill(ToolExecutionRequest request, Long userId, String characterUuid);

    /**
     * 为一组技能构造工具包（prompt 清单 + 工具规格 + 映射）。
     * 带 query 时按任务相关性按需注入（T6-07），否则全量注入。
     */
    SkillBundle prepare(List<Skill> skills, String query);
}
