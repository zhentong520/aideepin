package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.entity.AgentDelegationLog;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.languagemodel.data.ToolLoopBudget;
import com.moyz.adi.common.service.AgentService;
import com.moyz.adi.common.service.SysConfigService;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.util.TraceUtil;
import com.moyz.adi.common.vo.AgentRequest;
import com.moyz.adi.common.vo.AgentResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * T7-02（2026-10-20）：delegate_agent —— agent-as-tool 委派门面。
 * <p>
 * 一个 Agent 把自己的工具位置换成「调用另一个专门 Agent」时走本服务：
 * <ol>
 *   <li><b>开关</b>：adi.agent.delegation-enabled 与 sys_config agent_delegation_enabled
 *       任一关闭即拒绝（铁律 10，可热回滚）；</li>
 *   <li><b>契约</b>：objective / output_format / context_summary / must_not_do 四要素，
 *       objective 为空直接拒绝（无法执行也无法审计）；</li>
 *   <li><b>目标解析 + ACL</b>：按名称/id 解析目标 Agent，仅「自己的」或「系统预置」的可委派；</li>
 *   <li><b>调用链</b>：enter 时做嵌套深度硬限制（默认 1）与防环去重（A→B→A / 自递归拒绝），
 *       拒绝时错误信息带完整链状态；T7-04 的双向互调复用同一机制；</li>
 *   <li><b>effort-scaling</b>：按契约 objective 难度信号分 LOW/MEDIUM/HIGH 档，
 *       生成 T6-10 式三重预算（深度+墙钟+token）交给本次调用；</li>
 *   <li><b>落库</b>：每次委派（含被拒的 REJECTED）异步写 adi_agent_delegation_log，
 *       契约四要素 + 链快照 + 结果，全程可追溯。</li>
 * </ol>
 * <p>
 * The delegate_agent facade: contract validation, target resolution with ACL, call-chain
 * depth/cycle enforcement (T7-02/T7-04), effort scaling via ToolLoopBudget-style budgets,
 * and an async delegation log for full traceability.
 */
@Slf4j
@Service
public class DelegationService {

    @Resource
    private DelegationAgentResolver agentResolver;

    @Resource
    private AgentService agentService;

    @Resource
    private DelegationLogService delegationLogService;

    @Resource
    private AdiProperties adiProperties;

    /**
     * 构造一条新的调用链（嵌套深度取配置 adi.agent.delegation-max-depth，默认 1）。
     */
    public static AgentCallChain newChain() {
        return new AgentCallChain(resolveMaxDepth());
    }

    private static int resolveMaxDepth() {
        try {
            AdiProperties props = SpringUtil.getBean(AdiProperties.class);
            int depth = props.getAgent().getDelegationMaxDepth();
            return depth < 1 ? 1 : depth;
        } catch (Exception ignored) {
            // 单测/启动早期无 Spring 上下文，走任务书规定的默认硬限制 1
            return 1;
        }
    }

    /**
     * 委派总入口。
     *
     * @param targetAgent   目标 Agent 名称或 uuid
     * @param contract      委派契约（四要素）
     * @param sourceAgent   发起委派的 Agent（可为 null = 用户直接发起）
     * @param user          当前用户
     * @param chain         当前调用链（调用方已把自己 enter 进链；深度/防环在此生效）
     * @return 目标 Agent 的执行结果
     */
    public AgentResult delegate(String targetAgent, DelegationContract contract,
                                Character sourceAgent, User user, AgentCallChain chain) {
        String traceId = TraceUtil.getTraceId();
        long startMs = System.currentTimeMillis();
        String sourceAgentId = null == sourceAgent ? "" : sourceAgent.getUuid();
        String sourceAgentName = null == sourceAgent ? "" : StringUtils.defaultString(sourceAgent.getTitle(), sourceAgent.getUuid());
        List<String> chainBefore = chain.snapshot();

        if (!isEnabled()) {
            AgentNotAllowedException ex = new AgentNotAllowedException(targetAgent, "delegation is disabled by switch");
            saveLog(buildLog(traceId, sourceAgentId, sourceAgentName, targetAgent, contract, chainBefore,
                    DelegationLogService.STATUS_REJECTED, ex.getMessage(), startMs, null));
            throw ex;
        }
        if (null == contract || StringUtils.isBlank(contract.getObjective())) {
            IllegalArgumentException ex = new IllegalArgumentException("delegation contract requires a non-empty objective");
            saveLog(buildLog(traceId, sourceAgentId, sourceAgentName, targetAgent, contract, chainBefore,
                    DelegationLogService.STATUS_REJECTED, ex.getMessage(), startMs, null));
            throw ex;
        }

        Character target;
        try {
            target = agentResolver.resolve(targetAgent, user);
        } catch (RuntimeException ex) {
            saveLog(buildLog(traceId, sourceAgentId, sourceAgentName, targetAgent, contract, chainBefore,
                    DelegationLogService.STATUS_REJECTED, ex.getMessage(), startMs, null));
            throw ex;
        }

        String effort = DelegationEffortScaler.tierOf(contract);
        // 链检查：深度硬限制 + 防环去重（T7-02 / T7-04 共同的硬规则）。
        // enter 放进 try 是为了让「被拒」也落库审计（REJECTED）且 finally 能清理链状态；
        // enter 失败时链上并无 target，finally 的 exit 对非栈顶元素是 no-op，不会误删。
        AgentResult result;
        try {
            chain.enter(target.getUuid());
            ToolLoopBudget budget = DelegationEffortScaler.budgetForTier(effort);
            log.info("delegate_agent, source:{}, target:{}, effort:{}, chain:{} -> {}, budget: depth {}, {}ms, {} tokens",
                    sourceAgentName, target.getTitle(), effort, chainBefore, chain.snapshot(),
                    budget.getMaxDepth(), budget.getMaxWallMs(), budget.getMaxTotalTokens());

            AgentRequest request = AgentRequest.builder()
                    .characterUuid(target.getUuid())
                    .inputText(contract.toPromptText())
                    .enableRag(true)
                    .enableMcp(true)
                    .enableWebSearch(false)
                    // T7-02/T7-04：把链传给子 Agent——子 Agent 内部再调 delegate_agent 时
                    // 沿用同一条链，深度硬限制与防环才能在真实嵌套里生效
                    .callChain(chain)
                    .build();
            result = agentService.invoke(request, user, traceId);

            AgentDelegationLog successLog = buildLog(traceId, sourceAgentId, sourceAgentName, target.getUuid(), contract, chain.snapshot(),
                    DelegationLogService.STATUS_SUCCESS, null, startMs, result);
            successLog.setTargetAgentName(StringUtils.defaultString(target.getTitle(), target.getUuid()));
            saveLog(successLog);
            return result;
        } catch (RuntimeException ex) {
            AgentDelegationLog failLog = buildLog(traceId, sourceAgentId, sourceAgentName, target.getUuid(), contract, chain.snapshot(),
                    statusOf(ex), ex.getMessage(), startMs, null);
            failLog.setTargetAgentName(StringUtils.defaultString(target.getTitle(), target.getUuid()));
            saveLog(failLog);
            throw ex;
        } finally {
            chain.exit(target.getUuid());
        }
    }

    /**
     * 失败落库时的状态归类：被硬规则拒绝（开关/ACL/深度/防环）记 REJECTED，
     * 目标 Agent 真正执行出错才记 FAIL——审计时可一句话区分「没让进」与「进了但崩了」。
     */
    private static String statusOf(RuntimeException ex) {
        return ex instanceof DelegationDepthExceededException
                || ex instanceof AgentCallCycleException
                || ex instanceof AgentNotAllowedException
                ? DelegationLogService.STATUS_REJECTED
                : DelegationLogService.STATUS_FAIL;
    }

    /**
     * 委派开关：sys_config（热开关，缺省开）优先于配置文件 adi.agent.delegation-enabled。
     */
    public boolean isEnabled() {
        String sysValue = SysConfigService.getByKey(AdiConstant.SysConfigKey.AGENT_DELEGATION_ENABLED);
        if (StringUtils.isNotBlank(sysValue)) {
            return Boolean.parseBoolean(sysValue.trim());
        }
        try {
            return adiProperties.getAgent().isDelegationEnabled();
        } catch (Exception ignored) {
            return true;
        }
    }

    private AgentDelegationLog buildLog(String traceId, String sourceAgentId, String sourceAgentName,
                                        String targetAgent, DelegationContract contract,
                                        List<String> chain, String status, String errorMessage,
                                        long startMs, AgentResult result) {
        AgentDelegationLog delegationLog = new AgentDelegationLog();
        delegationLog.setTraceId(StringUtils.defaultString(traceId));
        delegationLog.setSourceAgentId(StringUtils.defaultString(sourceAgentId));
        delegationLog.setSourceAgentName(StringUtils.defaultString(sourceAgentName));
        // 目标：ACL 解析失败时只有用户输入的名称/id，解析成功后由调用方覆写为 uuid
        delegationLog.setTargetAgentId(StringUtils.defaultString(targetAgent));
        delegationLog.setTargetAgentName(StringUtils.defaultString(targetAgent));
        if (null != contract) {
            delegationLog.setObjective(StringUtils.abbreviate(contract.getObjective(), 2000));
            delegationLog.setOutputFormat(StringUtils.abbreviate(contract.getOutputFormat(), 500));
            delegationLog.setContextSummary(contract.getContextSummary());
            delegationLog.setMustNotDo(contract.getMustNotDo());
            delegationLog.setEffortLevel(DelegationEffortScaler.tierOf(contract));
        }
        delegationLog.setChainDepth(chain.size());
        delegationLog.setChainPath(String.join(" -> ", chain));
        delegationLog.setStatus(status);
        delegationLog.setErrorMessage(StringUtils.abbreviate(errorMessage, 1000));
        delegationLog.setDurationMs(System.currentTimeMillis() - startMs);
        if (null != result) {
            delegationLog.setInputTokens(result.getInputTokens());
            delegationLog.setOutputTokens(result.getOutputTokens());
        }
        return delegationLog;
    }

    private void saveLog(AgentDelegationLog delegationLog) {
        try {
            delegationLogService.saveAsync(delegationLog);
        } catch (Exception e) {
            // 日志失败绝不阻断委派主链路
            log.warn("saveAsync delegation log threw, error:{}", e.getMessage());
        }
    }
}
