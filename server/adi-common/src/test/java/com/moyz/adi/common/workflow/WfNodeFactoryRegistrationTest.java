package com.moyz.adi.common.workflow;

import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.AgentService;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.metrics.McpMetrics;
import com.moyz.adi.common.workflow.metrics.OrchestratorMetrics;
import com.moyz.adi.common.workflow.metrics.SkillMetrics;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import com.moyz.adi.common.workflow.node.database.DatabaseNode;
import com.moyz.adi.common.workflow.node.mcp.McpNode;
import com.moyz.adi.common.workflow.node.orchestrator.OrchestratorNode;
import com.moyz.adi.common.workflow.node.skill.SkillNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WfNodeFactory} 与 {@link WfComponentNameEnum} 的一致性测试（纯逻辑，不启 Spring 容器）。
 * <p>
 * 背景：节点要生效必须同时存在三处登记——数据库 adi_workflow_component 的组件记录、枚举 name、
 * 工厂 switch 分支。历史上 Database 节点就是「组件记录了但前端没跟」的半成品；而工厂在此之前对
 * 未知组件是 <b>静默 return null</b>，报错的地方离真正原因很远。这里把「已登记组件必须造出正确
 * 节点」与「未知组件必须显式报错」两条都锁死。
 * </p>
 * 说明：
 * <ul>
 *   <li>{@link BaseException} 用 ErrorEnum 构造时会向 Spring 的 MessageSource 取文案，
 *       因此本测试在 {@code @BeforeAll} 里给 {@link SpringUtil} 装一个最小消息源桩，
 *       否则异常构造阶段就会 NPE，测不到工厂本身的行为。</li>
 *   <li>只覆盖构造函数不依赖 Spring 容器的节点；Agent 等节点构造期就要取 bean，不在范围内。
 *       Orchestrator 节点构造期取 {@link AgentService} bean，这里由代理桩返回 mock
 *       （它只影响构造注入，onProcess 的行为由 BlackboardTest / 各节点自身单测覆盖）。</li>
 * </ul>
 */
class WfNodeFactoryRegistrationTest {

    /**
     * 装一个消息源桩。返回的文案带 <code>{0}</code> 占位符，与真实文案包一致
     * （见 adi-common i18n messages.properties: {@code A_PARAMS_INVALID_BY_=Parameter validation error: {0}}），
     * 这样 {@link BaseException} 里的 MessageFormat 才会把参数值填进去——否则本测试就无法验证
     * 「报错能指向具体组件名」这条真正的诉求。
     */
    @BeforeAll
    static void installMessageSourceStub() throws Exception {
        MessageSource messageSource = new MessageSource() {
            @Override
            public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
                return code + ": {0}";
            }

            @Override
            public String getMessage(String code, Object[] args, Locale locale) {
                return code + ": {0}";
            }

            @Override
            public String getMessage(MessageSourceResolvable resolvable, Locale locale) {
                return String.join(",", resolvable.getCodes() == null ? new String[0] : resolvable.getCodes());
            }
        };
        // Orchestrator 节点构造期要 AgentService bean；代理桩对其它 bean 请求返回 null（保持原语义）
        AgentService agentServiceStub = Mockito.mock(AgentService.class);
        ApplicationContext context = (ApplicationContext) Proxy.newProxyInstance(
                WfNodeFactoryRegistrationTest.class.getClassLoader(),
                new Class<?>[]{ApplicationContext.class},
                (proxy, method, methodArgs) -> {
                    if ("getBean".equals(method.getName()) && methodArgs != null && methodArgs.length > 0) {
                        Object last = methodArgs[methodArgs.length - 1];
                        if (last instanceof Class<?> type && MessageSource.class.isAssignableFrom(type)) {
                            return messageSource;
                        }
                        if (last instanceof Class<?> type && AgentService.class.isAssignableFrom(type)) {
                            return agentServiceStub;
                        }
                    }
                    return null;
                });
        Field field = SpringUtil.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        field.set(null, context);
    }

    private static WorkflowComponent component(String name) {
        WorkflowComponent component = new WorkflowComponent();
        component.setName(name);
        return component;
    }

    private static AbstractWfNode create(String name) {
        WfState wfState = new WfState(null, List.of(), "test-wf-state");
        return WfNodeFactory.create(component(name), new WorkflowNode(), wfState, new WfNodeState());
    }

    @Test
    @DisplayName("Database / Mcp / Skill / Orchestrator 四个组件名必须在枚举中登记")
    void enumRegistersTheFourNodeTypes() {
        assertEquals(WfComponentNameEnum.DATABASE, WfComponentNameEnum.getByName("Database"));
        assertEquals(WfComponentNameEnum.MCP, WfComponentNameEnum.getByName("Mcp"));
        assertEquals(WfComponentNameEnum.SKILL, WfComponentNameEnum.getByName("Skill"));
        assertEquals(WfComponentNameEnum.ORCHESTRATOR, WfComponentNameEnum.getByName("Orchestrator"));
    }

    @Test
    @DisplayName("工厂能为 Database / Mcp / Skill / Orchestrator 造出对应节点实例")
    void factoryCreatesTheFourNodeTypes() {
        assertInstanceOf(DatabaseNode.class, create("Database"));
        assertInstanceOf(McpNode.class, create("Mcp"));
        assertInstanceOf(SkillNode.class, create("Skill"));
        assertInstanceOf(OrchestratorNode.class, create("Orchestrator"));
    }

    @Test
    @DisplayName("新节点必须带上自己的指标类型（防止漏写 setMetrics 导致可观测性静默缺失）")
    void newNodesCarryTheirOwnMetricsType() {
        assertInstanceOf(McpMetrics.class, create("Mcp").getState().getMetrics());
        assertInstanceOf(SkillMetrics.class, create("Skill").getState().getMetrics());
        assertInstanceOf(OrchestratorMetrics.class, create("Orchestrator").getState().getMetrics());
    }

    @Test
    @DisplayName("未知组件必须显式报错并带上组件名，不能静默返回 null")
    void unknownComponentFailsLoudly() {
        BaseException ex = assertThrows(BaseException.class, () -> create("NotARealNode"));
        assertTrue(ex.getMessage().contains("NotARealNode"),
                "报错必须能指向具体组件名，实际 message=" + ex.getMessage());
    }
}
