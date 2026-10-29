package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Agent/工具循环预算配置（adi.agent.*，T6-10）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Agent} 嵌套类提为顶层类，Spring 绑定键不变。
 * 注意与实体类 {@code com.moyz.adi.common.entity.Agent} 同名但职责无关。
 */
@Data
public class Agent {

    /**
     * 工具循环最大深度（次）（adi.agent.tool-loop-max-depth，默认 10）。
     * <p>
     * 2026-10-29 真实 E2E 调整 5 → 10：实测多工具导购流程（列分类 → 查热销 →
     * 逐个商品详情 → 查活动/优惠券）天然需要 5~6 轮 LLM↔工具往返，5 会在
     * 最终答案生成前被截断（Tool call loop aborted: budget exceeded (DEPTH)）。
     * 安全性不降级：墙钟时间（tool-loop-max-seconds）与 token 累计
     * （tool-loop-max-tokens）两道兜底预算不变，失控循环仍会被先掐断。
     */
    @Min(value = 1, message = "adi.agent.tool-loop-max-depth 必须 >= 1")
    private int toolLoopMaxDepth = 10;

    /**
     * 工具循环墙钟时间上限（秒）（adi.agent.tool-loop-max-seconds，默认 300）。
     */
    @Min(value = 10, message = "adi.agent.tool-loop-max-seconds 必须 >= 10")
    private int toolLoopMaxSeconds = 300;

    /**
     * 工具循环 token 累计上限（adi.agent.tool-loop-max-tokens，默认 200000）。
     * 覆盖循环内所有 LLM 调用（含中间工具调用步骤）的 input+output token 之和。
     */
    @Min(value = 1000, message = "adi.agent.tool-loop-max-tokens 必须 >= 1000")
    private int toolLoopMaxTokens = 200000;

    /**
     * T7-02：agent-as-tool 委派机制总开关（adi.agent.delegation-enabled，默认 true）。
     * <p>
     * false 时 delegate_agent 一律拒绝（返回可读错误），行为与改造前一致，可热回滚（铁律 10）。
     * 另有 sys_config「agent_delegation_enabled」做运行期热开关（缺省开），
     * 两者任一关闭即停用；系统配置优先于配置文件。
     * <p>
     * Master switch for the delegate_agent (agent-as-tool) mechanism. When false,
     * every delegation is rejected with a readable error — a safe rollback to
     * pre-T7-02 behavior. The sys_config key agent_delegation_enabled provides the
     * runtime hot switch and takes precedence.
     */
    private boolean delegationEnabled = true;

    /**
     * T7-02：委派嵌套深度硬限制（adi.agent.delegation-max-depth，默认 1）。
     * <p>
     * 1 = Agent A 可委派给 B，B 不能再委派（深度 ≥2 拒绝）。⚠️ 这是任务书规定的
     * 安全硬限制，调大会让委派套委派失控，仅允许调小（更严）。
     * <p>
     * Hard nesting-depth limit for delegation (default 1 per the task spec).
     * Only tighten it; raising it re-enables the runaway nesting the limit exists to prevent.
     */
    @Min(value = 1, message = "adi.agent.delegation-max-depth 必须 >= 1")
    private int delegationMaxDepth = 1;

    /**
     * T7-03：多 Agent 编排节点（Orchestrator）开关（adi.agent.orchestrator-enabled，默认 true）。
     * <p>
     * 关闭后工作流里的 Orchestrator 节点直接返回可读错误（A0086），不再并行拉起子 Agent。
     * 与 sys_config agent_orchestrator_enabled 任一关闭即停用（系统配置优先，铁律 10）。
     * </p>
     */
    private boolean orchestratorEnabled = true;
}
