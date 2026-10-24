package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.AgentService;
import com.moyz.adi.common.vo.AgentResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-02 {@link DelegateAgentTool}（delegate_agent 工具）单测：
 * 工具参数解析 / 契约缺要素可读报错 / 嵌套深度与防环拒绝以 ERROR 文本回给 LLM /
 * 无链时以当前 Agent 为链根播种 / 工具规格。
 * <p>
 * 深度与防环走真实 {@link DelegationService}（mock 其下游 resolver/agentService/日志），
 * 保证「工具 → 门面 → 链」整条拒绝链路被测到，而不是只测门面。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DelegateAgentToolTest {

    @Mock
    private DelegationAgentResolver agentResolver;
    @Mock
    private AgentService agentService;
    @Mock
    private DelegationLogService delegationLogService;
    @Mock
    private AdiProperties adiProperties;

    private DelegateAgentTool tool;
    private DelegationService delegationService;

    @BeforeAll
    static void installMessageSourceStub() throws Exception {
        MultiAgentTestSupport.installMessageSourceStub();
    }

    private void setUp(boolean switchOn) throws Exception {
        AdiProperties.Agent agentProps = mock(AdiProperties.Agent.class);
        when(agentProps.isDelegationEnabled()).thenReturn(switchOn);
        when(adiProperties.getAgent()).thenReturn(agentProps);

        delegationService = new DelegationService();
        inject(delegationService, "agentResolver", agentResolver);
        inject(delegationService, "agentService", agentService);
        inject(delegationService, "delegationLogService", delegationLogService);
        inject(delegationService, "adiProperties", adiProperties);

        tool = new DelegateAgentTool() {
            @Override
            protected Character loadSource(String uuid) {
                Character source = new Character();
                source.setUuid(uuid);
                source.setTitle("主Agent");
                return source;
            }

            @Override
            protected User loadUser(Long userId) {
                User user = new User();
                user.setId(userId);
                return user;
            }
        };
        inject(tool, "delegationService", delegationService);
    }

    private static void inject(Object target, String field, Object value) throws ReflectiveOperationException {
        Class<?> clazz = target.getClass();
        while (null != clazz) {
            try {
                Field f = clazz.getDeclaredField(field);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(field);
    }

    private static ToolExecutionRequest request(String json) {
        return ToolExecutionRequest.builder().id("t1").name(DelegateAgentTool.TOOL_NAME).arguments(json).build();
    }

    @Test
    @DisplayName("工具规格：名为 delegate_agent，target_agent/objective 必填")
    void specificationShape() throws Exception {
        setUp(true);
        ToolSpecification spec = tool.buildSpecification();
        assertEquals(DelegateAgentTool.TOOL_NAME, spec.name());
        assertEquals(List.of("target_agent", "objective"), spec.parameters().required());
    }

    @Test
    @DisplayName("深度1正常：无链时以当前 Agent 为链根播种，invoke 时链上含双方 Agent，返回目标回答")
    void delegatesWithSeededChain() throws Exception {
        setUp(true);
        when(agentResolver.resolve(eq("数据分析师"), any(User.class))).thenReturn(agent("agent-analyst", "数据分析师"));
        // invoke 是同步调用：在 answer 里抓取当时链快照（invoke 后目标出链，事后断言会看到已清理的链）
        List<java.util.List<String>> chainAtInvoke = new java.util.ArrayList<>();
        when(agentService.invoke(any(), any(User.class), any())).thenAnswer(inv -> {
            com.moyz.adi.common.vo.AgentRequest req = inv.getArgument(0);
            chainAtInvoke.add(req.getCallChain().snapshot());
            return AgentResult.builder().answer("结论：增长 12%").build();
        });

        ToolExecutionRequest req = request(
                "{\"target_agent\":\"数据分析师\",\"objective\":\"分析上周销售数据\",\"output_format\":\"200字内\"}");
        String out = tool.execute(req, 1L, "agent-main", null);

        assertEquals("结论：增长 12%", out);
        assertEquals(1, chainAtInvoke.size());
        // 深度 1：invoke 时链 = 发起方 + 目标，共 2 个 Agent
        assertEquals(java.util.List.of("agent-main", "agent-analyst"), chainAtInvoke.get(0));
    }

    @Test
    @DisplayName("契约缺 objective：返回可读 ERROR，不触发委派")
    void blankObjectiveReadableError() throws Exception {
        setUp(true);
        String out = tool.execute(request("{\"target_agent\":\"分析师\",\"objective\":\"  \"}"), 1L, "agent-main", null);
        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.contains("objective"), out);
        verify(agentService, org.mockito.Mockito.never()).invoke(any(), any(), any());
    }

    @Test
    @DisplayName("缺 target_agent：返回可读 ERROR")
    void missingTargetReadableError() throws Exception {
        setUp(true);
        String out = tool.execute(request("{\"objective\":\"做事\"}"), 1L, "agent-main", null);
        assertEquals("ERROR: target_agent is required (name or uuid of the agent to delegate to)", out);
    }

    @Test
    @DisplayName("无用户上下文：返回可读 ERROR，不触发委派")
    void noUserContextRejected() throws Exception {
        setUp(true);
        String out = tool.execute(request("{\"target_agent\":\"分析师\",\"objective\":\"做事\"}"), null, "agent-main", null);
        assertTrue(out.startsWith("ERROR:"), out);
        verify(agentService, org.mockito.Mockito.never()).invoke(any(), any(), any());
    }

    @Test
    @DisplayName("嵌套深度=2被拒：B（链上 agent-main -> agent-b）再委派返回含链信息的 ERROR")
    void depthTwoSurfacesReadableError() throws Exception {
        setUp(true);
        when(agentResolver.resolve(eq("C"), any(User.class))).thenReturn(agent("agent-c", "C"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-main");
        chain.enter("agent-b");

        String out = tool.execute(request("{\"target_agent\":\"C\",\"objective\":\"再嵌套一层\"}"),
                1L, "agent-b", chain);

        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.contains("A0080"), out);
        assertTrue(out.contains("agent-main -> agent-b"), out);
        verify(agentService, org.mockito.Mockito.never()).invoke(any(), any(), any());
        // 被拒也落库（REJECTED），审计可追溯
        ArgumentCaptor<com.moyz.adi.common.entity.AgentDelegationLog> logCaptor =
                ArgumentCaptor.forClass(com.moyz.adi.common.entity.AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("REJECTED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("A2A 成环被拒：A→B→A 返回含链信息的 ERROR（A0081）")
    void cycleSurfacesReadableError() throws Exception {
        setUp(true);
        when(agentResolver.resolve(eq("agent-main"), any(User.class))).thenReturn(agent("agent-main", "主Agent"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-main");
        chain.enter("agent-b");

        String out = tool.execute(request("{\"target_agent\":\"agent-main\",\"objective\":\"回调发起方\"}"),
                1L, "agent-b", chain);

        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.contains("A0081"), out);
        verify(agentService, org.mockito.Mockito.never()).invoke(any(), any(), any());
    }

    @Test
    @DisplayName("自递归被拒：agent-b 委派给自己返回 ERROR（链去重）")
    void selfRecursionSurfacesReadableError() throws Exception {
        setUp(true);
        when(agentResolver.resolve(eq("agent-b"), any(User.class))).thenReturn(agent("agent-b", "B"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-main");
        chain.enter("agent-b");

        String out = tool.execute(request("{\"target_agent\":\"agent-b\",\"objective\":\"自我委派\"}"),
                1L, "agent-b", chain);

        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.contains("A0081"), out);
    }

    @Test
    @DisplayName("开关关闭：委派被门面拒绝，工具返回可读 ERROR")
    void disabledSwitchSurfacesReadableError() throws Exception {
        setUp(false);
        String out = tool.execute(request("{\"target_agent\":\"分析师\",\"objective\":\"做事\"}"),
                1L, "agent-main", null);
        assertTrue(out.startsWith("ERROR:"), out);
        verify(agentService, org.mockito.Mockito.never()).invoke(any(), any(), any());
    }

    @Test
    @DisplayName("目标执行失败：工具把失败原因以 ERROR 文本回给 LLM（不中断工具循环）")
    void targetFailureSurfacesReadableError() throws Exception {
        setUp(true);
        when(agentResolver.resolve(eq("分析师"), any(User.class))).thenReturn(agent("agent-analyst", "分析师"));
        when(agentService.invoke(any(), any(User.class), any())).thenThrow(new RuntimeException("llm timeout"));

        String out = tool.execute(request("{\"target_agent\":\"分析师\",\"objective\":\"分析数据\"}"),
                1L, "agent-main", null);

        assertTrue(out.startsWith("ERROR:"), out);
        assertTrue(out.contains("llm timeout"), out);
    }

    private static Character agent(String uuid, String title) {
        Character character = new Character();
        character.setUuid(uuid);
        character.setTitle(title);
        return character;
    }
}
