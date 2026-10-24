package com.moyz.adi.common.multiagent;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * T7-03（2026-10-20）：编排上下文共享黑板（shared blackboard）。
 * <p>
 * 编排型节点（{@code OrchestratorNode}）把子任务分派给多个子 Agent 并行执行时，
 * 所有协调状态都写在这一块黑板上一任务一分派、结果一回写、汇总一读：
 * <ul>
 *   <li><b>任务分派</b>：{@link #assign(String, String)} 在派发前登记每个子任务；</li>
 *   <li><b>子结果回写</b>：{@link #writeResult} / {@link #writeFailure} 由子任务线程
 *       并行调用，{@link ConcurrentHashMap} 保证多线程写入不丢不覆盖；</li>
 *   <li><b>失败记录</b>：失败与成功一样落黑板（{@link BlackboardEntry.Status#FAIL}），
 *       汇总方读得到完整终态，一个子任务失败不阻塞其他子任务；</li>
 *   <li><b>最终汇总读</b>：{@link #summary()} 在全部子任务终态后渲染整板内容。</li>
 * </ul>
 * 黑板只活在单次编排的上下文里（节点执行期），不跨节点共享——跨节点状态走
 * 工作流自身的 WfState 输入输出链，避免隐式全局可变状态。
 * <p>
 * A thread-safe shared blackboard for one orchestration run: assignments, parallel
 * result/failure writes, and a final summary read. Failures are first-class records
 * on the board and never block sibling subtasks.
 */
public class Blackboard {

    private final ConcurrentHashMap<String, BlackboardEntry> entries = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    /**
     * 登记一个子任务（任务分派）。taskId 重复时后者覆盖前者——调用方负责保证
     * 编排配置里 taskId 唯一（OrchestratorNode 在解析配置时已做去重兜底）。
     */
    public void assign(String taskId, String taskText) {
        if (StringUtils.isBlank(taskId)) {
            throw new IllegalArgumentException("blackboard taskId must not be blank");
        }
        entries.put(taskId, new BlackboardEntry(taskId, null == taskText ? "" : taskText));
        version.incrementAndGet();
    }

    /** 标记子任务开始执行（PENDING → RUNNING）。 */
    public void markRunning(String taskId) {
        BlackboardEntry entry = entries.get(taskId);
        if (null != entry) {
            entry.markRunning();
        }
    }

    /** 子结果回写（成功）。 */
    public void writeResult(String taskId, String result) {
        BlackboardEntry entry = entries.get(taskId);
        if (null != entry) {
            entry.writeResult(result);
        }
    }

    /** 失败记录回写：失败不抛、不阻塞其他子任务，仅落黑板供汇总读取。 */
    public void writeFailure(String taskId, String errorMessage) {
        BlackboardEntry entry = entries.get(taskId);
        if (null != entry) {
            entry.writeFailure(errorMessage);
        }
    }

    /** 读单个任务（无则 empty）。 */
    public Optional<BlackboardEntry> read(String taskId) {
        return Optional.ofNullable(entries.get(taskId));
    }

    /** 全量读（按 taskId 排序，保证汇总输出稳定可 diff）。 */
    public List<BlackboardEntry> allEntries() {
        List<BlackboardEntry> list = new ArrayList<>(entries.values());
        list.sort(Comparator.comparing(BlackboardEntry::getTaskId));
        return list;
    }

    /** 黑板内容版本号（每次分派/回写自增，供测试断言"写入不丢"）。 */
    public long version() {
        return version.get();
    }

    /** 已登记的子任务数。 */
    public int size() {
        return entries.size();
    }

    /** 未终态（PENDING/RUNNING）的任务数。 */
    public int pendingCount() {
        return (int) allEntries().stream().filter(e -> !e.isDone()).count();
    }

    /** 是否全部子任务已到终态（汇总读取的前置条件）。 */
    public boolean allDone() {
        return pendingCount() == 0 && !entries.isEmpty();
    }

    public int successCount() {
        return (int) allEntries().stream().filter(e -> e.getStatus() == BlackboardEntry.Status.SUCCESS).count();
    }

    public int failCount() {
        return (int) allEntries().stream().filter(e -> e.getStatus() == BlackboardEntry.Status.FAIL).count();
    }

    /**
     * 最终汇总读：把整块黑板渲染成一段文本（成功给结果、失败给原因）。
     * 汇总方（下游节点或用户）拿到的不是拼接起来的"部分成功结果"，
     * 而是带失败记录的完整终态视图。
     */
    public String summary() {
        List<BlackboardEntry> all = allEntries();
        if (all.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【子任务汇总】共 ").append(all.size()).append(" 项，成功 ")
                .append(successCount()).append(" 项，失败 ").append(failCount()).append(" 项\n");
        for (BlackboardEntry entry : all) {
            sb.append(entry.toSummaryLine()).append('\n');
        }
        return sb.toString().strip();
    }

    /**
     * 仅成功结果的聚合文本（供 summary_prompt 模板的 {@code {results}} 占位）。
     * 不含失败记录——模板由编排者自定义，失败信息已由 {@link #summary()} 全量视图覆盖。
     */
    public String resultsOnly() {
        StringBuilder sb = new StringBuilder();
        for (BlackboardEntry entry : allEntries()) {
            if (entry.getStatus() == BlackboardEntry.Status.SUCCESS) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("[").append(entry.getTaskId()).append("] ").append(entry.getResult());
            }
        }
        return sb.toString();
    }

    /** 只读快照（Map 副本，调试/审计用）。 */
    public Map<String, BlackboardEntry> snapshot() {
        return Map.copyOf(entries);
    }
}
