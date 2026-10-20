package com.moyz.adi.common.workflow.node.loop;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeTestSupport;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.LoopMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T9-02：循环节点单测。覆盖任务书四条验收：
 * 数组逐项 / 聚合正确 / 最大次数熔断 / 空数组。
 */
class LoopNodeTest {

    @BeforeEach
    void setUp() {
        WfNodeTestSupport.install();
    }

    @AfterEach
    void tearDown() {
        WfNodeTestSupport.uninstall();
        com.moyz.adi.common.workflow.node.subworkflow.SubWorkflowInvoker.clear();
    }

    private static WorkflowComponent component() {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(1L);
        component.setName("Loop");
        return component;
    }

    private static WorkflowNode node(String configJson) {
        WorkflowNode node = new WorkflowNode();
        node.setUuid("loop-node");
        node.setTitle("循环测试");
        node.setWorkflowComponentId(1L);
        node.setNodeConfig(WfNodeTestSupport.config(configJson));
        return node;
    }

    private static LoopNode readyLoop(String configJson, String inputValue) {
        WfState wfState = WfNodeTestSupport.wfState(WfNodeTestSupport.user(1L));
        WfNodeState nodeState = new WfNodeState();
        nodeState.getInputs().add(NodeIOData.createByText("input", "默认输入", inputValue));
        return new LoopNode(component(), node(configJson), wfState, nodeState);
    }

    private static Optional<NodeIOData> output(NodeProcessResult result, String name) {
        return result.getContent().stream().filter(item -> name.equals(item.getName())).findFirst();
    }

    @Test
    @DisplayName("数组逐项：JSON 数组每个元素各渲染一次 body，{item}/{index} 正确替换")
    void eachIteratesOverArrayItems() {
        LoopNode loop = readyLoop("""
                {"mode":"each","body_template":"第{index}项:{item}"}
                """, "[\"苹果\",\"香蕉\",\"樱桃\"]");

        NodeProcessResult result = loop.onProcess();

        LoopMetrics metrics = (LoopMetrics) loop.getState().getMetrics();
        assertEquals(3, metrics.getIterations());
        assertEquals(3, metrics.getItemCount());
        assertEquals(3, metrics.getResultCount());
        assertEquals("completed", metrics.getStopReason());
        //聚合正确：JSON 数组保序包含全部逐项结果
        String aggregate = output(result, "output").orElseThrow().valueToString();
        assertTrue(aggregate.contains("第0项:苹果"), aggregate);
        assertTrue(aggregate.contains("第1项:香蕉"), aggregate);
        assertTrue(aggregate.contains("第2项:樱桃"), aggregate);
        assertEquals("completed", output(result, "stop_reason").orElseThrow().valueToString());
    }

    @Test
    @DisplayName("聚合正确：results_text 换行拼接、iterations 数值输出与 metrics 一致")
    void aggregationOutputsAreConsistent() {
        LoopNode loop = readyLoop("""
                {"mode":"each","body_template":"r-{item}"}
                """, "[1,2,3,4,5]");

        NodeProcessResult result = loop.onProcess();

        assertEquals("r-1\nr-2\nr-3\nr-4\nr-5", output(result, "results_text").orElseThrow().valueToString());
        assertEquals(5.0, (Double) output(result, "iterations").orElseThrow().getContent().getValue());
        assertEquals(5, ((LoopMetrics) loop.getState().getMetrics()).getIterations());
    }

    @Test
    @DisplayName("最大次数熔断：10 项数组 max_iterations=3 只执行 3 轮，stop_reason=max_iterations，部分结果不丢")
    void maxIterationsFuseStopsLoop() {
        LoopNode loop = readyLoop("""
                {"mode":"each","body_template":"item:{item}","max_iterations":3}
                """, "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\"]");

        NodeProcessResult result = loop.onProcess();

        LoopMetrics metrics = (LoopMetrics) loop.getState().getMetrics();
        assertEquals(3, metrics.getIterations());
        assertEquals("max_iterations", metrics.getStopReason());
        String aggregate = output(result, "output").orElseThrow().valueToString();
        assertTrue(aggregate.contains("item:a") && aggregate.contains("item:c"), aggregate);
        assertTrue(!aggregate.contains("item:d"), "熔断后不得继续执行后续项");
    }

    @Test
    @DisplayName("空数组：直接成功结束，输出空聚合，stop_reason=empty_input")
    void emptyArrayCompletesWithoutFailure() {
        LoopNode loop = readyLoop("""
                {"mode":"each","body_template":"x{item}"}
                """, "[]");

        NodeProcessResult result = loop.onProcess();

        LoopMetrics metrics = (LoopMetrics) loop.getState().getMetrics();
        assertEquals(0, metrics.getIterations());
        assertEquals("empty_input", metrics.getStopReason());
        assertEquals("[]", output(result, "output").orElseThrow().valueToString());
        assertEquals("", output(result, "results_text").orElseThrow().valueToString());
    }

    @Test
    @DisplayName("while 模式：条件渲染为 false 即停止；未停止时受 max_iterations 熔断")
    void whileModeStopsOnConditionAndFuse() {
        //condition 恒真 → 触达熔断
        LoopNode alwaysTrue = readyLoop("""
                {"mode":"while","condition_template":"yes","body_template":"round{iteration}","max_iterations":5}
                """, "ignored");
        NodeProcessResult result = alwaysTrue.onProcess();
        assertEquals(5, ((LoopMetrics) alwaysTrue.getState().getMetrics()).getIterations());
        assertEquals("max_iterations", ((LoopMetrics) alwaysTrue.getState().getMetrics()).getStopReason());
        assertTrue(output(result, "results_text").orElseThrow().valueToString().contains("round4"));

        //条件立即 false → 零轮执行
        LoopNode immediateFalse = readyLoop("""
                {"mode":"while","condition_template":"false","body_template":"never","max_iterations":5}
                """, "ignored");
        immediateFalse.onProcess();
        assertEquals(0, ((LoopMetrics) immediateFalse.getState().getMetrics()).getIterations());
        assertEquals("completed", ((LoopMetrics) immediateFalse.getState().getMetrics()).getStopReason());
    }

    @Test
    @DisplayName("并行逐项：parallel=true 结果仍保序聚合（并行不改变聚合顺序）")
    void parallelEachKeepsOrder() {
        LoopNode loop = readyLoop("""
                {"mode":"each","body_template":"p{index}-{item}","parallel":true,"max_parallel":4}
                """, "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]");

        NodeProcessResult result = loop.onProcess();

        assertEquals("p0-a\np1-b\np2-c\np3-d\np4-e\np5-f",
                output(result, "results_text").orElseThrow().valueToString());
    }
}
