package com.moyz.adi.common.multiagent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.interfaces.DelegateAgentSpi;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.CharacterService;
import com.moyz.adi.common.service.UserService;
import com.moyz.adi.common.vo.AgentResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * T7-02（2026-10-20）：{@code delegate_agent} 工具——agent-as-tool 的运行时形态。
 * <p>
 * 注册进对话工具清单后，LLM 可像调普通工具一样把子任务委派给另一个专门 Agent：
 * <pre>{@code
 * {"target_agent": "数据分析师", "objective": "分析上周销售数据并给结论",
 *  "output_format": "不超过 200 字", "context_summary": "...", "must_not_do": "..."}
 * }</pre>
 * 执行链路：解析契约 → 装载当前 Agent/用户 → 走 {@link DelegationService#delegate}
 * （开关 / ACL / 嵌套深度 / 防环 / effort-scaling / 落库审计全部在门面收口）。
 * <p>
 * 错误约定：被拒（开关关闭 / ACL 拒绝 / 深度超限 / 成环 / 契约缺 objective）
 * 返回 {@code ERROR: <可读原因>} 而不是抛异常——让调用方 LLM 能读到原因并改道，
 * 同时拒绝记录已由门面落库。与 create_skill 工具的错误返回风格一致。
 * <p>
 * The runtime {@code delegate_agent} tool: parses the contract from tool arguments,
 * loads the current agent/user, and delegates through {@link DelegationService}.
 * Rejections return a readable {@code ERROR: ...} text so the calling LLM can adapt.
 */
@Slf4j
@Component
public class DelegateAgentTool implements DelegateAgentSpi {

    public static final String TOOL_NAME = "delegate_agent";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private DelegationService delegationService;

    @Resource
    private CharacterService characterService;

    @Resource
    private UserService userService;

    /**
     * 工具是否对当前对话可用（铁律 10 热开关：关闭时不注册，模型看不到该工具）。
     */
    public boolean enabled() {
        try {
            return delegationService.isEnabled();
        } catch (Exception ignored) {
            // 启动早期/单测无 Spring 上下文：按门面默认（开）处理，真正执行仍会兜底校验
            return true;
        }
    }

    public ToolSpecification buildSpecification() {
        return ToolSpecification.builder()
                .name(TOOL_NAME)
                .description("把当前子任务委派给另一个专门 Agent 执行（agent-as-tool）。"
                        + "适合当前任务需要别的专业能力时使用：给出明确的委派目标（objective，必填）、"
                        + "期望产出格式（output_format）、必要的背景摘要（context_summary）"
                        + "和禁止事项（must_not_do）。委派结果即目标 Agent 的回答文本。"
                        + "注意：不允许把委派再层层嵌套下去（嵌套深度最多 1 层），也不能回调链上出现过的 Agent。")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("target_agent", "目标 Agent 的名称或唯一标识（uuid）")
                        .addStringProperty("objective", "委派目标：要对方完成的具体任务，一句话说清（必填）")
                        .addStringProperty("output_format", "期望产出格式，如「JSON 数组」「不超过 200 字的结论」（可选）")
                        .addStringProperty("context_summary", "交给对方的背景摘要，避免重复劳动（可选）")
                        .addStringProperty("must_not_do", "显式禁止事项，如「不要访问用户知识库」（可选）")
                        .required("target_agent", "objective")
                        .build())
                .build();
    }

    /**
     * 执行委派（DelegateAgentSpi#invoke 实现）。userId/characterUuid/chain 由 LLM 服务装配层显式传入
     * （工具在回调线程执行，ThreadContext 不保证可达，与 create_skill 同理）。
     *
     * @param request             LLM 发出的工具调用请求（JSON 参数）
     * @param userId              当前对话用户 id（null = 无用户上下文，直接拒绝）
     * @param sourceCharacterUuid 当前正在执行的 Agent uuid（链根；嵌套时链上已含父链）
     * @param chain               当前调用链（null = 以 sourceCharacterUuid 为链根播种新链）
     * @return 目标 Agent 的回答文本；被拒/失败返回 {@code ERROR: <原因>}
     */
    @Override
    public String invoke(ToolExecutionRequest request, Long userId, String sourceCharacterUuid, AgentCallChain chain) {
        JsonNode args;
        try {
            args = MAPPER.readTree(StringUtils.defaultIfBlank(request.arguments(), "{}"));
        } catch (IOException e) {
            log.warn("delegate_agent arguments parse failed:{}", StringUtils.abbreviate(request.arguments(), 200));
            return "ERROR: invalid arguments (must be a JSON object)";
        }
        String targetAgent = args.path("target_agent").asText("").trim();
        if (targetAgent.isEmpty()) {
            return "ERROR: target_agent is required (name or uuid of the agent to delegate to)";
        }
        if (null == userId) {
            return "ERROR: no user context, cannot delegate";
        }
        if (StringUtils.isBlank(sourceCharacterUuid)) {
            return "ERROR: no source agent context, cannot delegate";
        }
        DelegationContract contract;
        try {
            if (!(args instanceof com.fasterxml.jackson.databind.node.ObjectNode objectNode)) {
                return "ERROR: invalid arguments (must be a JSON object)";
            }
            contract = DelegationContract.fromJson(objectNode);
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        }

        Character source = loadSource(sourceCharacterUuid);
        if (null == source) {
            return "ERROR: source agent not found: " + sourceCharacterUuid;
        }
        User user = loadUser(userId);
        if (null == user) {
            return "ERROR: user not found: " + userId;
        }

        // 无链（用户直接发起的对话，未走 LocalAgentService 装配）时以当前 Agent 为链根播种；
        // 播种失败（如入参为空）按拒绝处理，链规则由门面统一兜底
        AgentCallChain effectiveChain = chain;
        if (null == effectiveChain) {
            effectiveChain = DelegationService.newChain();
            effectiveChain.enter(sourceCharacterUuid);
        }

        try {
            AgentResult result = delegationService.delegate(targetAgent, contract, source, user, effectiveChain);
            return StringUtils.defaultIfBlank(null == result ? null : result.getAnswer(), "(empty answer from delegated agent)");
        } catch (RuntimeException e) {
            // 可读错误回给 LLM：深度超限 / 成环 / ACL / 开关关闭 / 目标执行失败。
            // 拒绝与失败记录已由 DelegationService 落库，这里不重复审计。
            log.warn("delegate_agent rejected or failed, target:{}, error:{}", targetAgent, e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }

    /** 装载发起委派的源 Agent（protected 便于单测覆写，绕开 MyBatis 链式查询）。 */
    protected Character loadSource(String sourceCharacterUuid) {
        return characterService.lambdaQuery()
                .eq(Character::getUuid, sourceCharacterUuid)
                .eq(Character::getIsDeleted, false)
                .oneOpt()
                .orElse(null);
    }

    /** 装载当前用户（protected 便于单测覆写）。 */
    protected User loadUser(Long userId) {
        return userService.getById(userId);
    }
}
