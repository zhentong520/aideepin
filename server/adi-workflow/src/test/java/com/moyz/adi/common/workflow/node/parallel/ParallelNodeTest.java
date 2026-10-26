package com.moyz.adi.common.workflow.node.parallel;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.ParallelMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_PARALLEL_JOIN_FAILED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T9-03：并行节点单测。覆盖任务书三条验收：
 * 并行分支互不阻塞 / 某分支失败按 join 语义处理 / 超时。
 */
class ParallelNodeTest {

    @BeforeEach
    void setUp() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.install();
    }

    @AfterEach
    void tearDown() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.uninstall();
    }

    private static ParallelNode readyParallel(String configJson) {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(1L);
        component.setName("Parallel");
        WorkflowNode node = new WorkflowNode();
        node.setUuid("parallel-node");
        node.setTitle("并行测试");
        node.setWorkflowComponentId(1L);
        node.setNodeConfig(com.moyz.adi.common.workflow.WfNodeTestSupport.config(configJson));
        WfState wfState = com.moyz.adi.common.workflow.WfNodeTestSupport.wfState(
                com.moyz.adi.common.workflow.WfNodeTestSupport.user(1L));
        WfNodeState nodeState = new WfNodeState();
        nodeState.getInputs().add(NodeIOData.createByText("input", "默认输入", "共享输入"));
        return new ParallelNode(component, node, wfState, nodeState);
    }

    private static Optional<NodeIOData> output(NodeProcessResult result, String name) {
        return result.getContent().stream().filter(item -> name.equals(item.getName())).findFirst();
    }

    @Test
    @DisplayName("并行分支互不阻塞：慢分支(delay 5s/timeout 1s)不拖垮快分支，join=any 下整体成功")
    void slowBranchDoesNotBlockFastBranch() {
        long start = System.currentTimeMillis();
        ParallelNode node = readyParallel("""
                {
                  "branches": [
                    {"name": "fast", "template": "fast:{input}"},
                    {"name": "slow", "template": "slow:{input}", "delay_ms": 5000}
                  ],
                  "join": "any",
                  "timeout_seconds": 1
                }
                """);

        NodeProcessResult result = node.onProcess();
        long elapsed = System.currentTimeMillis() - start;

        //快分支结果完整，慢分支被超时标记失败但未阻塞整体
        assertEquals("fast:共享输入", output(result, "fast").orElseThrow().valueToString());
        assertEquals(1, ((ParallelMetrics) node.getState().getMetrics()).getSuccessCount());
        assertEquals(1, ((ParallelMetrics) node.getState().getMetrics()).getFailCount());
        assertTrue(elapsed < 5000, "并行下慢分支不得阻塞整体完成，实际耗时 " + elapsed + "ms");
    }

    @Test
    @DisplayName("join=all：任一分支失败 → 整个节点失败，错误明细指明失败分支")
    void joinAllFailsWhenAnyBranchFails() {
        ParallelNode node = readyParallel("""
                {
                  "branches": [
                    {"name": "good", "template": "ok:{input}"},
                    {"name": "bad", "template": "{{missing_root_var}}"}
                  ],
                  "join": "all",
                  "timeout_seconds": 5
                }
                """);

        BaseException ex = assertThrows(BaseException.class, node::onProcess);
        assertEquals(A_WF_PARALLEL_JOIN_FAILED.getCode(), ex.getCode());
        assertTrue(ex.getInfo().contains("bad"), "错误明细必须指明失败分支名，实际：" + ex.getInfo());
        assertEquals(1, ((ParallelMetrics) node.getState().getMetrics()).getFailCount());
    }

    @Test
    @DisplayName("join=any：某分支失败不影响成功分支，成功分支结果正常聚合输出")
    void joinAnySucceedsWithPartialResults() {
        ParallelNode node = readyParallel("""
                {
                  "branches": [
                    {"name": "a", "template": "A:{input}"},
                    {"name": "b", "template": "{{no_such_var}}"},
                    {"name": "c", "template": "C:{input}"}
                  ],
                  "join": "any",
                  "timeout_seconds": 5
                }
                """);

        NodeProcessResult result = node.onProcess();

        assertEquals("A:共享输入", output(result, "a").orElseThrow().valueToString());
        assertEquals("C:共享输入", output(result, "c").orElseThrow().valueToString());
        assertTrue(output(result, "b").isEmpty());
        assertEquals(2, ((ParallelMetrics) node.getState().getMetrics()).getSuccessCount());
        assertEquals(1, ((ParallelMetrics) node.getState().getMetrics()).getFailCount());
    }

    @Test
    @DisplayName("超时：慢分支超过 timeout 即按失败处理（join=all 下节点失败，明细含 timeout 字样）")
    void branchTimeoutCountsAsFailure() {
        ParallelNode node = readyParallel("""
                {
                  "branches": [
                    {"name": "quick", "template": "Q:{input}"},
                    {"name": "stuck", "template": "S:{input}", "delay_ms": 3000}
                  ],
                  "join": "all",
                  "timeout_seconds": 1
                }
                """);

        BaseException ex = assertThrows(BaseException.class, node::onProcess);
        assertEquals(A_WF_PARALLEL_JOIN_FAILED.getCode(), ex.getCode());
        assertTrue(ex.getInfo().contains("timeout"), "超时分支必须以 timeout 失败呈现，实际：" + ex.getInfo());
    }

    @Test
    @DisplayName("join=any 全部分支失败 → 节点失败（no branch succeeded）")
    void joinAnyFailsWhenAllBranchesFail() {
        ParallelNode node = readyParallel("""
                {
                  "branches": [
                    {"name": "x", "template": "{{var_x}}"},
                    {"name": "y", "template": "{{var_y}}"}
                  ],
                  "join": "any",
                  "timeout_seconds": 5
                }
                """);

        BaseException ex = assertThrows(BaseException.class, node::onProcess);
        assertEquals(A_WF_PARALLEL_JOIN_FAILED.getCode(), ex.getCode());
        assertTrue(ex.getInfo().contains("join=any"), ex.getInfo());
    }

    @Test
    @DisplayName("分支名去重：重复分支名自动加序号，输出不互相覆盖")
    void duplicateBranchNamesDeduped() {
        ParallelNode node = readyParallel("""
                {
                  "branches": [
                    {"name": "same", "template": "first"},
                    {"name": "same", "template": "second"}
                  ],
                  "join": "all",
                  "timeout_seconds": 5
                }
                """);

        NodeProcessResult result = node.onProcess();

        assertEquals("first", output(result, "same").orElseThrow().valueToString());
        assertEquals("second", output(result, "same_1").orElseThrow().valueToString());
    }
}
