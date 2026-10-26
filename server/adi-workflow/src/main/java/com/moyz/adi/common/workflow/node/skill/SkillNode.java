package com.moyz.adi.common.workflow.node.skill;

import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.SkillService;
import com.moyz.adi.common.service.skill.SkillToolService;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.WorkflowUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.SkillMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static com.moyz.adi.common.constant.AdiConstant.WorkflowConstant.DEFAULT_OUTPUT_PARAM_NAME;

/**
 * 工作流技能节点：读取某个技能的指令正文，或执行技能目录下的脚本。
 * <p>
 * Workflow Skill node — read a skill's instruction body, or execute one of its scripts.
 * </p>
 * <p>
 * 安全边界：技能的脚本执行完全复用 {@link SkillToolService}（固定参数列表、无 shell、
 * 路径穿越校验、60s 超时、输出截断、仅 .py），本节点不额外开一条执行通道。
 * 归属校验：仅「公开技能」或「当前运行用户自己的技能」可被本节点使用，
 * 否则公开工作流会变成执行他人私有脚本的通道。
 * </p>
 */
@Slf4j
public class SkillNode extends AbstractWfNode {

    private static final String MODE_INSTRUCTION = "instruction";
    private static final String MODE_SCRIPT = "script";

    public SkillNode(WorkflowComponent wfComponent, WorkflowNode node,
                     WfState wfState, WfNodeState nodeState) {
        super(wfComponent, node, wfState, nodeState);
        state.setMetrics(new SkillMetrics());
    }

    @Override
    protected NodeProcessResult onProcess() {
        SkillNodeConfig config = checkAndGetConfig(SkillNodeConfig.class);
        if (StringUtils.isBlank(config.getSkillUuid()) && StringUtils.isBlank(config.getSkillName())) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "skill node requires skill_uuid or skill_name");
        }
        //Workflow nodes run on @Async threads: pass the user explicitly instead of relying on ThreadContext
        User user = getWfState().getUser();
        if (null == user) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "skill node cannot resolve the workflow user");
        }

        Skill skill = resolveSkill(config, user.getId());
        SkillToolService skillToolService = SpringUtil.getBean(SkillToolService.class);

        String mode = StringUtils.defaultIfBlank(config.getMode(), MODE_INSTRUCTION);
        String content;
        if (MODE_SCRIPT.equalsIgnoreCase(mode)) {
            if (StringUtils.isBlank(config.getScript())) {
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "skill node script is empty");
            }
            //脚本参数支持 {name} 占位符；脚本内容/路径仍由 SkillToolService 做安全校验
            String args = WorkflowUtil.renderTemplate(StringUtils.defaultString(config.getArgs()), state.getInputs());
            content = skillToolService.runScript(skill, config.getScript(), args);
            //工具层用 "ERROR: ..." 表达失败（面向 LLM 的约定）；工作流节点必须显式失败，
            //否则错误字符串会顺着下游静默流走，运行时看着是「成功」。
            if (content != null && content.startsWith("ERROR:")) {
                throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "skill script failed: " + content);
            }
        } else {
            content = skillToolService.readSkill(skill);
        }
        content = StringUtils.defaultString(content);

        SkillMetrics metrics = (SkillMetrics) state.getMetrics();
        metrics.setSkillName(skill.getName());
        metrics.setMode(mode);
        metrics.setScript(config.getScript());
        metrics.setResultLength(content.length());

        List<NodeIOData> outputData = new ArrayList<>();
        outputData.add(NodeIOData.createByText(DEFAULT_OUTPUT_PARAM_NAME, "技能输出", content));
        outputData.add(NodeIOData.createByText("skill_name", "技能名", StringUtils.defaultString(skill.getName())));
        outputData.add(NodeIOData.createByText("mode", "执行模式", mode));
        return NodeProcessResult.builder().content(outputData).build();
    }

    /**
     * 解析技能并做归属/启用校验：
     * 1. 必须存在、未删除；
     * 2. 必须 is_enable（禁用技能不应被工作流执行）；
     * 3. 必须是公开技能，或归属当前运行用户 —— 否则公开工作流会成为读取/执行他人私有技能的通道。
     * Resolve the skill and enforce existence, enablement and ownership (public skill, or owned by
     * the running user).
     */
    private Skill resolveSkill(SkillNodeConfig config, Long runUserId) {
        SkillService skillService = SpringUtil.getBean(SkillService.class);
        Skill skill = StringUtils.isNotBlank(config.getSkillUuid())
                ? skillService.getByUuid(config.getSkillUuid())
                : skillService.getByName(config.getSkillName());
        if (null == skill) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        if (!Boolean.TRUE.equals(skill.getIsEnable())) {
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_, "skill is disabled: " + skill.getName());
        }
        boolean ownedByRunner = null != skill.getUserId() && skill.getUserId().equals(runUserId);
        if (!Boolean.TRUE.equals(skill.getIsPublic()) && !ownedByRunner) {
            log.warn("Skill node rejected a non-public skill not owned by the runner, skill:{}, owner:{}, runner:{}",
                    skill.getName(), skill.getUserId(), runUserId);
            throw new BaseException(ErrorEnum.A_PARAMS_INVALID_BY_,
                    "skill is private and not owned by the workflow runner: " + skill.getName());
        }
        return skill;
    }
}
