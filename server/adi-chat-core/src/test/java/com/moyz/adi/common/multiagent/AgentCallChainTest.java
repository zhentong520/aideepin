package com.moyz.adi.common.multiagent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-02 嵌套深度硬限制 + T7-04 A2A 双向互调防环（{@link AgentCallChain}）单测。
 * <p>
 * 覆盖任务书点名的用例：
 * <ul>
 *   <li>T7-02：深度 1 正常 / 深度 ≥2 被拒 / 错误信息清晰（带目标、链、最大深度）；</li>
 *   <li>T7-04：A→B→C 正常 / A→B→A 被拒并报错 / 自递归被拒 / 链状态可查询。</li>
 * </ul>
 * 另加一条文案守卫：ErrorEnum A0080/A0081/A0082 的真实 i18n 文案必须带够占位符，
 * 防止「改了文案漏占位符」导致用户界面出现字面 {@code {0}}。
 */
class AgentCallChainTest {

    @BeforeAll
    static void installMessageSourceStub() throws Exception {
        MultiAgentTestSupport.installMessageSourceStub();
    }

    // ---------------- T7-02: 嵌套深度 = 1 硬限制 ----------------

    @Test
    @DisplayName("T7-02 深度1正常：maxDepth=1 时 A 可委派给 B（发起方 + 1 层被委派方）")
    void depthOneAllowsOneDelegationLayer() {
        AgentCallChain chain = new AgentCallChain(1);
        chain.enter("agent-a"); // 发起方
        chain.enter("agent-b"); // 第 1 层委派：正常放行
        assertEquals(2, chain.size());
        assertEquals(List.of("agent-a", "agent-b"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-02 深度2被拒：B 再委派 C 抛 DelegationDepthExceededException")
    void depthTwoIsRejected() {
        AgentCallChain chain = new AgentCallChain(1);
        chain.enter("agent-a");
        chain.enter("agent-b");
        DelegationDepthExceededException ex =
                assertThrows(DelegationDepthExceededException.class, () -> chain.enter("agent-c"));
        // 拒绝后链状态不被破坏，仍可继续查询
        assertEquals(List.of("agent-a", "agent-b"), chain.snapshot());
        assertEquals("A0080", ex.getCode());
    }

    @Test
    @DisplayName("T7-02 错误信息清晰：带上目标Agent、调用链与最大深度")
    void depthExceededMessageIsReadable() {
        AgentCallChain chain = new AgentCallChain(1);
        chain.enter("agent-a");
        chain.enter("agent-b");
        DelegationDepthExceededException ex =
                assertThrows(DelegationDepthExceededException.class, () -> chain.enter("agent-c"));
        String message = ex.getMessage();
        assertTrue(message.contains("agent-c"), "信息必须带上被拒的目标 Agent，实际：" + message);
        assertTrue(message.contains("agent-a -> agent-b"), "信息必须带上当前调用链，实际：" + message);
        assertTrue(message.contains("A0080"), "信息必须带错误码，实际：" + message);
    }

    @Test
    @DisplayName("T7-02 出链恢复容量：被拒/结束后 exit 掉栈顶，链可继续使用")
    void exitRestoresChainCapacity() {
        AgentCallChain chain = new AgentCallChain(1);
        chain.enter("agent-a");
        chain.enter("agent-b");
        chain.exit("agent-b"); // B 调用结束出链，A 又可委派给下一个
        assertDoesNotThrow(() -> chain.enter("agent-c"));
        assertEquals(List.of("agent-a", "agent-c"), chain.snapshot());
    }

    // ---------------- T7-04: A2A 双向互调防环 ----------------

    @Test
    @DisplayName("T7-04 A→B→C 正常：链上无重复 Agent 时放行（两层委派，maxDepth=2 隔离深度因素）")
    void threeAgentChainIsAllowed() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("a");
        chain.enter("b");
        chain.enter("c");
        assertEquals(List.of("a", "b", "c"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 A→B→A 被拒并报错：链上出现重复 Agent 抛 AgentCallCycleException")
    void aToBToAIsRejected() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("a");
        chain.enter("b");
        AgentCallCycleException ex =
                assertThrows(AgentCallCycleException.class, () -> chain.enter("a"));
        assertEquals("A0081", ex.getCode());
        String message = ex.getMessage();
        assertTrue(message.contains("a"), "信息必须指出重复的 Agent，实际：" + message);
        assertTrue(message.contains("a -> b"), "信息必须带上完整调用链，实际：" + message);
        // 拒绝不动链
        assertEquals(List.of("a", "b"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 自递归被拒：A→A 同样按环处理")
    void selfRecursionIsRejected() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("a");
        AgentCallCycleException ex =
                assertThrows(AgentCallCycleException.class, () -> chain.enter("a"));
        assertEquals("A0081", ex.getCode());
        assertEquals(List.of("a"), chain.snapshot());
    }

    @Test
    @DisplayName("T7-04 拒绝不残留：委派被拒后 finally 出链，后续调用不受影响")
    void rejectionLeavesNoResidue() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("a");
        chain.enter("b");
        try {
            chain.enter("a"); // 被拒
        } catch (AgentCallCycleException ignored) {
            // 模拟 DelegationService 的 finally：无论成败都出链
        } finally {
            chain.exit("a"); // 未入链，非栈顶 no-op
        }
        chain.exit("b");
        assertDoesNotThrow(() -> chain.enter("c"));
        // 发起方 a 仍在链上（它的调用还没结束），b 已出链、c 是新的第 1 层委派
        assertEquals(List.of("a", "c"), chain.snapshot());
    }

    // ---------------- 链状态可查询 ----------------

    @Test
    @DisplayName("链状态可查询：contains / size / getMaxDepth / snapshot")
    void chainStateIsQueryable() {
        AgentCallChain chain = new AgentCallChain(2);
        assertEquals(2, chain.getMaxDepth());
        assertFalse(chain.contains("a"));
        chain.enter("a");
        assertTrue(chain.contains("a"));
        assertFalse(chain.contains("b"));
        assertEquals(1, chain.size());
    }

    @Test
    @DisplayName("snapshot 返回不可变副本，外部改动不影响链")
    void snapshotIsImmutableCopy() {
        AgentCallChain chain = new AgentCallChain(2);
        chain.enter("a");
        List<String> snapshot = chain.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("x"));
        assertEquals(List.of("a"), chain.snapshot());
    }

    @Test
    @DisplayName("exit 非栈顶元素时不误删中间节点")
    void exitNonTopKeepsMiddleNodes() {
        AgentCallChain chain = new AgentCallChain(3);
        chain.enter("a");
        chain.enter("b");
        chain.exit("a"); // a 不是栈顶，不能删
        assertEquals(List.of("a", "b"), chain.snapshot());
        chain.exit("b");
        assertEquals(List.of("a"), chain.snapshot());
    }

    @Test
    @DisplayName("空白 agentId 直接拒参，不进链")
    void blankAgentIdRejected() {
        AgentCallChain chain = new AgentCallChain(2);
        assertThrows(IllegalArgumentException.class, () -> chain.enter(null));
        assertThrows(IllegalArgumentException.class, () -> chain.enter("  "));
    }

    @Test
    @DisplayName("maxDepth 必须 >= 1，构造期即校验")
    void maxDepthMustBeAtLeastOne() {
        assertThrows(IllegalArgumentException.class, () -> new AgentCallChain(0));
        assertDoesNotThrow(() -> new AgentCallChain(1));
    }

    // ---------------- 文案守卫 ----------------

    @Test
    @DisplayName("i18n 文案占位符与异常参数个数匹配（防字面 {0} 泄露给用户）")
    void i18nMessagesCarryEnoughPlaceholders() throws Exception {
        for (String resource : List.of("i18n/messages.properties", "i18n/messages_zh_CN.properties")) {
            Properties properties = new Properties();
            try (InputStream in = AgentCallChainTest.class.getClassLoader().getResourceAsStream(resource)) {
                assertTrue(null != in, "找不到文案资源：" + resource);
                properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            }
            // DelegationDepthExceededException 传 3 个参数：目标 / 链 / 最大深度
            String depthMsg = properties.getProperty("A_AGENT_DELEGATION_DEPTH_EXCEEDED");
            assertTrue(null != depthMsg && depthMsg.contains("{0}") && depthMsg.contains("{1}") && depthMsg.contains("{2}"),
                    resource + " 的深度文案必须含 {0}{1}{2}，实际：" + depthMsg);
            // AgentCallCycleException 传 2 个参数：重复 Agent / 链
            String cycleMsg = properties.getProperty("A_AGENT_DELEGATION_CYCLE");
            assertTrue(null != cycleMsg && cycleMsg.contains("{0}") && cycleMsg.contains("{1}"),
                    resource + " 的防环文案必须含 {0}{1}，实际：" + cycleMsg);
            // AgentNotAllowedException 传 2 个参数：Agent / 原因
            String aclMsg = properties.getProperty("A_AGENT_DELEGATION_NOT_ALLOWED");
            assertTrue(null != aclMsg && aclMsg.contains("{0}") && aclMsg.contains("{1}"),
                    resource + " 的 ACL 文案必须含 {0}{1}，实际：" + aclMsg);
        }
    }
}
