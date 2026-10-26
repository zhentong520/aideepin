package com.moyz.adi.common.multiagent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * T7-02 / T7-04（2026-10-20）：Agent 调用链（agent call chain）——委派深度限制 + 双向互调防环。
 * <p>
 * 一条链记录一次嵌套调用经过的全部 Agent（按进入顺序，含发起方）。两条硬规则：
 * <ul>
 *   <li><b>嵌套深度 = 1 硬限制</b>（T7-02）：{@code maxDepth} 是允许的<b>委派层数</b>，
 *       链最多容纳 {@code maxDepth + 1} 个 Agent（发起方 + maxDepth 层被委派方）。
 *       默认 1 即「A 可委派给 B，B 不能再委派给 C」——深度 ≥2 拒绝并返回可读错误，
 *       防止委派套委派失控；</li>
 *   <li><b>链上 id 去重</b>（T7-04 A2A 防环）：A→B→A 或自递归（A→A）时链上出现重复 Agent，
 *       立即拒绝，双向互调不死循环。</li>
 * </ul>
 * 链状态可查询：{@link #snapshot()} / {@link #size()} / {@link #getMaxDepth()}。
 * <p>
 * Agent call chain with two hard rules: bounded nesting depth (T7-02, default 1 layer of
 * delegation, chain capacity maxDepth + 1) and cycle detection by agent-id dedup on the
 * chain (T7-04, rejects A→B→A and self-recursion). Chain state is queryable.
 */
public class AgentCallChain {

    private final List<String> agentIds;
    private final int maxDepth;

    /**
     * @param maxDepth 允许的委派嵌套层数。T7-02 要求默认 1：Agent A 可委派给 B，
     *                 B 不能再委派给 C（链容量 = maxDepth + 1，含发起方）。
     */
    public AgentCallChain(int maxDepth) {
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be >= 1");
        }
        this.maxDepth = maxDepth;
        this.agentIds = new ArrayList<>();
    }

    /**
     * 申请进入链（委派/互调目标 Agent）。
     * <ul>
     *   <li>链上已存在同名 Agent → 抛 {@link AgentCallCycleException}（防环，A→B→A / 自递归）；</li>
     *   <li>进入后委派层数超过 maxDepth（链长超过 maxDepth + 1）→ 抛
     *       {@link DelegationDepthExceededException}（深度硬限制）。</li>
     * </ul>
     * 两种拒绝都携带完整链状态，错误信息可直接展示给调用方。
     */
    public void enter(String agentId) {
        String id = normalize(agentId);
        if (agentIds.contains(id)) {
            throw new AgentCallCycleException(id, snapshot());
        }
        // 链上第一个是发起方（不算委派层），之后每进一个就是多一层委派
        if (agentIds.size() > maxDepth) {
            throw new DelegationDepthExceededException(id, snapshot(), maxDepth);
        }
        agentIds.add(id);
    }

    /**
     * 目标 Agent 出链（本次调用结束，无论成功失败都必须调用，恢复链状态）。
     * 仅当栈顶是该 Agent 时移除；非栈顶（异常残留）时记录告警不动链，避免误删中间节点。
     */
    public void exit(String agentId) {
        String id = normalize(agentId);
        int last = agentIds.size() - 1;
        if (last >= 0 && agentIds.get(last).equals(id)) {
            agentIds.remove(last);
        }
    }

    /** 当前链快照（不可变副本，可查询/可日志化）。 */
    public List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(agentIds));
    }

    /** 当前链长（已进入的 Agent 数）。 */
    public int size() {
        return agentIds.size();
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    /** 链上是否已包含该 Agent（不抛错的预检，供调用方提前判断）。 */
    public boolean contains(String agentId) {
        return agentIds.contains(normalize(agentId));
    }

    private static String normalize(String agentId) {
        if (null == agentId || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId must not be blank");
        }
        return agentId.trim();
    }
}
