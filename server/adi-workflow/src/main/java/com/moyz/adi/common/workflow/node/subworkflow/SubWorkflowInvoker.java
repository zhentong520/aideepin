package com.moyz.adi.common.workflow.node.subworkflow;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.stream.Collectors;

import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;

/**
 * T9-07：子工作流引用链守卫（防递归）。
 * <p>
 * 子工作流嵌套执行在同一调用线程上同步进行（{@code WorkflowStarter.blocking} 是阻塞调用），
 * 因此用 ThreadLocal 维护「当前线程的引用链」即可覆盖整棵嵌套树：
 * <ol>
 *   <li><b>引用链去重</b>：目标工作流 uuid 已在链上（自引用或 A→B→A 环）→ 拒绝
 *       （A_WF_SUB_WORKFLOW_RECURSIVE）；</li>
 *   <li><b>深度上限</b>：链长度达到 MAX_DEPTH（3）后继续嵌套 → 拒绝
 *       （A_WF_SUB_WORKFLOW_DEPTH_EXCEEDED）。</li>
 * </ol>
 * 入链/出链必须成对（SubWorkflowNode 在 finally 中出链）；异常路径泄漏时由 clear() 兜底重置。
 * <p>
 * T9-07 recursion guard for sub-workflow references: chain de-duplication + depth cap (3).
 */
public final class SubWorkflowInvoker {

    /** 嵌套深度上限：A(根) → B → C 即深度 3，再深拒绝 */
    public static final int MAX_DEPTH = 3;

    private static final ThreadLocal<Deque<String>> CHAIN = ThreadLocal.withInitial(ArrayDeque::new);

    private SubWorkflowInvoker() {
    }

    /**
     * 进入子工作流前调用：递归/超深即拒绝；通过则入链。
     *
     * @param subWorkflowUuid 目标子工作流 uuid
     */
    public static void checkAndEnter(String subWorkflowUuid) {
        Deque<String> chain = CHAIN.get();
        if (chain.contains(subWorkflowUuid)) {
            throw new BaseException(ErrorEnum.A_WF_SUB_WORKFLOW_RECURSIVE, subWorkflowUuid, chainText());
        }
        if (chain.size() >= MAX_DEPTH) {
            throw new BaseException(ErrorEnum.A_WF_SUB_WORKFLOW_DEPTH_EXCEEDED,
                    String.valueOf(chain.size() + 1), String.valueOf(MAX_DEPTH));
        }
        chain.push(subWorkflowUuid);
    }

    /** 执行结束（含异常）后出链；必须在 finally 中成对调用 */
    public static void exit() {
        Deque<String> chain = CHAIN.get();
        if (!chain.isEmpty()) {
            chain.pop();
        }
        if (chain.isEmpty()) {
            CHAIN.remove();
        }
    }

    /** 当前链深度（0 = 根流程） */
    public static int depth() {
        return CHAIN.get().size();
    }

    /** 引用链快照文本（uuid 以 -> 连接，栈顶在前） */
    public static String chainText() {
        return CHAIN.get().stream().collect(Collectors.joining(" -> "));
    }

    /** 兜底重置（异常恢复/单测清理用） */
    public static void clear() {
        CHAIN.remove();
    }
}
