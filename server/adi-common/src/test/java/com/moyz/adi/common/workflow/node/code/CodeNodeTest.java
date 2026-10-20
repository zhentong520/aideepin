package com.moyz.adi.common.workflow.node.code;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.skill.SkillScriptRunner;
import com.moyz.adi.common.workflow.NodeProcessResult;
import com.moyz.adi.common.workflow.WfNodeState;
import com.moyz.adi.common.workflow.WfState;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.metrics.CodeMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T9-04：代码节点单测。覆盖任务书三条验收：
 * 简单代码执行 / 输入输出映射 / 沙箱限制生效。
 * <p>
 * 沙箱复用 {@link SkillScriptRunner}（无 shell、环境变量透传、超时强杀、10KB 截断），
 * 本测试直接对沙箱层做真实 python 执行验证（环境无 python 时自动跳过，不算失败）。
 */
class CodeNodeTest {

    private static final boolean PYTHON_AVAILABLE = detectPython();

    private Path tempDir;

    @BeforeEach
    void setUp() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.install();
        tempDir = Path.of(System.getProperty("java.io.tmpdir"), "adi-code-node-test-" + System.nanoTime());
    }

    @AfterEach
    void tearDown() {
        com.moyz.adi.common.workflow.WfNodeTestSupport.uninstall();
    }

    private static boolean detectPython() {
        try {
            Process p = new ProcessBuilder("python", "--version").start();
            p.waitFor(10, TimeUnit.SECONDS);
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static CodeNode readyCode(String configJson) {
        WorkflowComponent component = new WorkflowComponent();
        component.setId(1L);
        component.setName("Code");
        WorkflowNode node = new WorkflowNode();
        node.setUuid("code-node");
        node.setTitle("代码测试");
        node.setWorkflowComponentId(1L);
        node.setNodeConfig(com.moyz.adi.common.workflow.WfNodeTestSupport.config(configJson));
        WfState wfState = com.moyz.adi.common.workflow.WfNodeTestSupport.wfState(
                com.moyz.adi.common.workflow.WfNodeTestSupport.user(1L));
        WfNodeState nodeState = new WfNodeState();
        nodeState.getInputs().add(NodeIOData.createByText("city", "城市", "杭州"));
        nodeState.getInputs().add(NodeIOData.createByText("day", "日期", "周二"));
        return new CodeNode(component, node, wfState, nodeState);
    }

    private static Optional<NodeIOData> output(NodeProcessResult result, String name) {
        return result.getContent().stream().filter(item -> name.equals(item.getName())).findFirst();
    }

    @Test
    @DisplayName("简单代码执行：print 输出成为节点 output，exit_code=0")
    void simpleCodeExecution() {
        Assumptions.assumeTrue(PYTHON_AVAILABLE, "python not available, sandbox execution test skipped");
        CodeNode node = readyCode("""
                {"language":"python","code":"print('hello from sandbox')","timeout_seconds":30}
                """);

        NodeProcessResult result = node.onProcess();

        assertEquals("hello from sandbox", output(result, "output").orElseThrow().valueToString().strip());
        assertEquals(0.0, (Double) output(result, "exit_code").orElseThrow().getContent().getValue());
        CodeMetrics metrics = (CodeMetrics) node.getState().getMetrics();
        assertEquals(0, metrics.getExitCode());
        assertEquals("python", metrics.getLanguage());
    }

    @Test
    @DisplayName("输入输出映射：上游输入序列化为 SKILL_SCRIPT_ARGS JSON，代码内可按名读取")
    void inputOutputMapping() {
        Assumptions.assumeTrue(PYTHON_AVAILABLE, "python not available, sandbox execution test skipped");
        CodeNode node = readyCode("""
                {"language":"python","code":"import os, json\\nargs = json.loads(os.environ['SKILL_SCRIPT_ARGS'])\\nprint(args['city'] + '-' + args['day'])","timeout_seconds":30}
                """);

        NodeProcessResult result = node.onProcess();

        assertEquals("杭州-周二", output(result, "output").orElseThrow().valueToString().strip());
        //映射函数本身：全部输入进 JSON，可反序列化还原
        String argsJson = CodeNode.buildArgsJson(node.getState().getInputs());
        assertTrue(argsJson.contains("\"city\":\"杭州\""), argsJson);
    }

    @Test
    @DisplayName("沙箱限制生效：sleep 超过 timeout 被 1 秒超时强杀（与 Skill 沙箱同款强杀语义）")
    void sandboxTimeoutEnforced() {
        Assumptions.assumeTrue(PYTHON_AVAILABLE, "python not available, sandbox execution test skipped");
        String result = CodeNode.runInSandbox(tempDir.resolve("timeout"),
                "import time\nprint('start')\ntime.sleep(30)\nprint('never')", "{}", 1);

        assertTrue(result.startsWith("ERROR: script timeout after 1s"), result);
        assertTrue(!result.contains("never"), "超时后不得再有输出");
    }

    @Test
    @DisplayName("沙箱限制生效：输出超过 10KB 被截断（与 Skill 一致）")
    void sandboxOutputTruncated() {
        Assumptions.assumeTrue(PYTHON_AVAILABLE, "python not available, sandbox execution test skipped");
        String result = CodeNode.runInSandbox(tempDir.resolve("truncate"),
                "print('x' * 50000)", "{}", 30);

        //SkillScriptRunner 截断上限 10000 字符 + "exit_code: 0\n" 头
        int contentLength = result.length() - "exit_code: 0\n".length();
        assertTrue(contentLength <= 10100, "输出必须被截断到 ~10KB，实际 " + contentLength);
    }

    @Test
    @DisplayName("非零退出码 → 节点显式失败（错误不顺着下游静默流走）")
    void nonZeroExitFailsNode() {
        Assumptions.assumeTrue(PYTHON_AVAILABLE, "python not available, sandbox execution test skipped");
        CodeNode node = readyCode("""
                {"language":"python","code":"import sys\\nprint('boom')\\nsys.exit(2)","timeout_seconds":30}
                """);

        BaseException ex = assertThrows(BaseException.class, node::onProcess);
        assertTrue(ex.getMessage().contains("non-zero status 2"), ex.getMessage());
    }

    @Test
    @DisplayName("语言白名单：仅 python（与 Skill 沙箱解释器白名单一致），其余配置直接拒绝")
    void languageWhitelist() {
        CodeNode node = readyCode("""
                {"language":"javascript","code":"console.log(1)"}
                """);
        BaseException ex = assertThrows(BaseException.class, node::onProcess);
        assertTrue(ex.getMessage().contains("python"), ex.getMessage());
    }

    @Test
    @DisplayName("结果解析：SkillScriptRunner 输出格式 exit_code 行 + stdout 正确拆分")
    void parseResultFormat() {
        assertEquals(0, CodeNode.parseExitCode("exit_code: 0\nhello"));
        assertEquals(2, CodeNode.parseExitCode("exit_code: 2\nboom"));
        assertEquals(-1, CodeNode.parseExitCode("ERROR: script timeout after 60s"));
        assertEquals("hello\nworld", CodeNode.parseStdout("exit_code: 0\nhello\nworld"));
        assertEquals("", CodeNode.parseStdout("exit_code: 1\n"));
    }

    @Test
    @DisplayName("buildArgsJson：空输入输出空对象 JSON（不依赖 Spring 上下文）")
    void emptyArgsJson() {
        assertEquals("{}", CodeNode.buildArgsJson(List.of()));
    }

}
