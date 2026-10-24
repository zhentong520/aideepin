package com.moyz.adi.common.workflow.node.subworkflow;

import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_SUB_WORKFLOW_DEPTH_EXCEEDED;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_SUB_WORKFLOW_RECURSIVE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T9-07：子工作流防递归守卫单测。
 * 覆盖任务书验收「递归引用被拒」：引用链去重（自引用/环）与深度上限（3）。
 */
class SubWorkflowInvokerTest {

    @BeforeEach
    void setUp() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.install();
    }

    @AfterEach
    void tearDown() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.uninstall();
        SubWorkflowInvoker.clear();
    }

    @Test
    @DisplayName("自引用被拒：A 引用 A（链上已存在）→ A_WF_SUB_WORKFLOW_RECURSIVE")
    void selfReferenceRejected() {
        SubWorkflowInvoker.checkAndEnter("wf-a");
        BaseException ex = assertThrows(BaseException.class, () -> SubWorkflowInvoker.checkAndEnter("wf-a"));
        assertEquals(A_WF_SUB_WORKFLOW_RECURSIVE.getCode(), ex.getCode());
        assertTrue(ex.getInfo().contains("wf-a"), ex.getInfo());
    }

    @Test
    @DisplayName("环引用被拒：A→B→A（A 已在引用链上）→ A_WF_SUB_WORKFLOW_RECURSIVE")
    void cycleReferenceRejected() {
        SubWorkflowInvoker.checkAndEnter("wf-a");
        SubWorkflowInvoker.checkAndEnter("wf-b");
        BaseException ex = assertThrows(BaseException.class, () -> SubWorkflowInvoker.checkAndEnter("wf-a"));
        assertEquals(A_WF_SUB_WORKFLOW_RECURSIVE.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("深度上限：根→1→2 合法，第 4 层被拒（A_WF_SUB_WORKFLOW_DEPTH_EXCEEDED）")
    void depthCapEnforced() {
        //根流程引用 1 → 链长 1
        SubWorkflowInvoker.checkAndEnter("wf-1");
        SubWorkflowInvoker.checkAndEnter("wf-2");
        SubWorkflowInvoker.checkAndEnter("wf-3");
        assertEquals(3, SubWorkflowInvoker.depth());
        BaseException ex = assertThrows(BaseException.class, () -> SubWorkflowInvoker.checkAndEnter("wf-4"));
        assertEquals(A_WF_SUB_WORKFLOW_DEPTH_EXCEEDED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("正常嵌套放行：不同 uuid 逐层入链不误伤（深度 ≤ 3）")
    void legalNestingPasses() {
        assertDoesNotThrow(() -> {
            SubWorkflowInvoker.checkAndEnter("wf-1");
            SubWorkflowInvoker.checkAndEnter("wf-2");
        });
        assertEquals(2, SubWorkflowInvoker.depth());
        assertTrue(SubWorkflowInvoker.chainText().startsWith("wf-2"), "栈顶应为最近入链的子工作流");
    }

    @Test
    @DisplayName("出链配对：finally 语义下出链后可再次引用同一工作流（上一轮已退出链）")
    void exitAllowsReferenceAgain() {
        SubWorkflowInvoker.checkAndEnter("wf-1");
        SubWorkflowInvoker.exit();
        assertEquals(0, SubWorkflowInvoker.depth());
        assertDoesNotThrow(() -> SubWorkflowInvoker.checkAndEnter("wf-1"));
    }
}
