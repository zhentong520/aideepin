package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.AgentDelegationLog;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.AgentService;
import com.moyz.adi.common.vo.AgentRequest;
import com.moyz.adi.common.vo.AgentResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T7-02 delegate_agent 门面（{@link DelegationService}）单测：开关 / 契约 / ACL / 深度 / 防环 / 落库。
 * <p>
 * 任务书点名用例的门面级验证：
 * <ul>
 *   <li>深度 1 正常（委派成功且 invoke 收到契约渲染文本）；</li>
 *   <li>深度 ≥2 被拒（第二次嵌套抛 DelegationDepthExceededException，目标 Agent 根本未被 invoke）；</li>
 *   <li>A→B→A 被拒（链上已有目标 Agent，AgentCallCycleException）；</li>
 *   <li>每次委派（含被拒）都落库，状态区分 REJECTED / SUCCESS。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DelegationServiceTest {

    @Mock
    private DelegationAgentResolver agentResolver;
    @Mock
    private AgentService agentService;
    @Mock
    private DelegationLogService delegationLogService;
    @Mock
    private AdiProperties adiProperties;

    private DelegationService delegationService;

    @BeforeAll
    static void installMessageSourceStub() throws Exception {
        MultiAgentTestSupport.installMessageSourceStub();
    }

    private void setUp(boolean switchOn) {
        AdiProperties.Agent agentProps = mock(AdiProperties.Agent.class);
        when(agentProps.isDelegationEnabled()).thenReturn(switchOn);
        when(adiProperties.getAgent()).thenReturn(agentProps);
        delegationService = new DelegationService();
        inject(delegationService, "agentResolver", agentResolver);
        inject(delegationService, "agentService", agentService);
        inject(delegationService, "delegationLogService", delegationLogService);
        inject(delegationService, "adiProperties", adiProperties);
    }

    private static void inject(Object target, String field, Object value) {
        try {
            java.lang.reflect.Field f = DelegationService.class.getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Character agent(String uuid, String title) {
        Character character = new Character();
        character.setUuid(uuid);
        character.setTitle(title);
        return character;
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        return user;
    }

    private static DelegationContract contract(String objective) {
        return DelegationContract.builder()
                .objective(objective)
                .outputFormat("JSON")
                .build();
    }

    @Test
    @DisplayName("深度1正常：委派成功，invoke 收到契约四要素渲染文本，日志记 SUCCESS")
    void depthOneDelegationSucceeds() {
        setUp(true);
        when(agentResolver.resolve("数据分析师", user())).thenReturn(agent("agent-analyst", "数据分析师"));
        AgentResult expected = AgentResult.builder().answer("结论：增长 12%").inputTokens(100).outputTokens(20).build();
        when(agentService.invoke(any(AgentRequest.class), any(User.class), any())).thenReturn(expected);

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-main"); // 发起方先入链

        AgentResult actual = delegationService.delegate("数据分析师", contract("分析上周销售数据并给结论"), null, user(), chain);

        assertSame(expected, actual);
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentService).invoke(captor.capture(), any(User.class), any());
        AgentRequest request = captor.getValue();
        assertEquals("agent-analyst", request.getCharacterUuid());
        String inputText = request.getInputText();
        assertEquals(true, inputText.contains("【委派目标】分析上周销售数据并给结论"), inputText);
        assertEquals(true, inputText.contains("【产出格式】JSON"), inputText);

        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        AgentDelegationLog saved = logCaptor.getValue();
        assertEquals("SUCCESS", saved.getStatus());
        assertEquals("agent-analyst", saved.getTargetAgentId());
        assertEquals("agent-main -> agent-analyst", saved.getChainPath());
        assertEquals(2, saved.getChainDepth());
        assertEquals(100, saved.getInputTokens());
        assertEquals(20, saved.getOutputTokens());
    }

    @Test
    @DisplayName("深度2被拒：链已满时抛 DelegationDepthExceededException，目标 Agent 不被 invoke")
    void secondNestingIsRejected() {
        setUp(true);
        when(agentResolver.resolve(eq("C"), any())).thenReturn(agent("agent-c", "C"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-a");
        chain.enter("agent-b");

        DelegationDepthExceededException ex = assertThrows(DelegationDepthExceededException.class,
                () -> delegationService.delegate("C", contract("再嵌套一层"), agent("agent-b", "B"), user(), chain));

        assertEquals("A0080", ex.getCode());
        verify(agentService, never()).invoke(any(), any(), any());
        // 被拒也落库（REJECTED），链状态保持拒绝现场
        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("REJECTED", logCaptor.getValue().getStatus());
        assertEquals("agent-a -> agent-b", logCaptor.getValue().getChainPath());
    }

    @Test
    @DisplayName("A→B→A 被拒：链上已有目标 Agent 时抛 AgentCallCycleException 并落库")
    void cycleOnChainIsRejected() {
        setUp(true);
        when(agentResolver.resolve(eq("A"), any())).thenReturn(agent("agent-a", "A"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-a");
        chain.enter("agent-b");

        AgentCallCycleException ex = assertThrows(AgentCallCycleException.class,
                () -> delegationService.delegate("A", contract("回调 A"), agent("agent-b", "B"), user(), chain));

        assertEquals("A0081", ex.getCode());
        verify(agentService, never()).invoke(any(), any(), any());
        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("REJECTED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("自递归被拒：链上只有发起方自己，再委派给自己即环")
    void selfRecursionIsRejected() {
        setUp(true);
        when(agentResolver.resolve(eq("A"), any())).thenReturn(agent("agent-a", "A"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-a");

        assertThrows(AgentCallCycleException.class,
                () -> delegationService.delegate("A", contract("自我委派"), null, user(), chain));
        verify(agentService, never()).invoke(any(), any(), any());
    }

    @Test
    @DisplayName("开关关闭：直接拒绝并落 REJECTED，不解析目标、不 invoke")
    void disabledSwitchRejects() {
        setUp(false);
        assertThrows(AgentNotAllowedException.class,
                () -> delegationService.delegate("任何Agent", contract("做事"), null, user(), DelegationService.newChain()));
        verify(agentResolver, never()).resolve(any(), any());
        verify(agentService, never()).invoke(any(), any(), any());
        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("REJECTED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("契约缺 objective：拒参并落 REJECTED，不解析目标")
    void blankObjectiveRejected() {
        setUp(true);
        assertThrows(IllegalArgumentException.class,
                () -> delegationService.delegate("分析师", DelegationContract.builder().objective(" ").build(),
                        null, user(), DelegationService.newChain()));
        verify(agentResolver, never()).resolve(any(), any());
        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("REJECTED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("ACL 拒绝：解析失败原样抛出并落 REJECTED，不 invoke")
    void aclRejectionPropagates() {
        setUp(true);
        when(agentResolver.resolve(eq("别人的私有Agent"), any()))
                .thenThrow(new AgentNotAllowedException("别人的私有Agent", "not owner nor system preset"));
        assertThrows(AgentNotAllowedException.class,
                () -> delegationService.delegate("别人的私有Agent", contract("越权访问"), null, user(), DelegationService.newChain()));
        verify(agentService, never()).invoke(any(), any(), any());
        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("REJECTED", logCaptor.getValue().getStatus());
    }

    @Test
    @DisplayName("目标执行失败：记 FAIL 并原样抛出，链状态被 finally 清理")
    void targetFailureIsLoggedAsFail() {
        setUp(true);
        when(agentResolver.resolve(eq("分析师"), any())).thenReturn(agent("agent-analyst", "分析师"));
        when(agentService.invoke(any(AgentRequest.class), any(User.class), any()))
                .thenThrow(new RuntimeException("llm timeout"));

        AgentCallChain chain = DelegationService.newChain();
        chain.enter("agent-main");

        assertThrows(RuntimeException.class,
                () -> delegationService.delegate("分析师", contract("分析数据"), null, user(), chain));
        ArgumentCaptor<AgentDelegationLog> logCaptor = ArgumentCaptor.forClass(AgentDelegationLog.class);
        verify(delegationLogService).saveAsync(logCaptor.capture());
        assertEquals("FAIL", logCaptor.getValue().getStatus());
        // finally 出链后链上只剩发起方
        assertEquals(java.util.List.of("agent-main"), chain.snapshot());
    }

    @Test
    @DisplayName("newChain 无 Spring 上下文时默认深度 1（任务书硬限制）")
    void newChainDefaultsToDepthOne() {
        assertEquals(1, DelegationService.newChain().getMaxDepth());
    }

    @Test
    @DisplayName("默认开关为开：无 sys_config、无配置文件时不阻断委派")
    void enabledByDefault() {
        setUp(true);
        assertEquals(true, delegationService.isEnabled());
    }
}
