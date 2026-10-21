package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowTrigger;
import com.moyz.adi.common.mapper.WorkflowTriggerMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.RedisTemplateUtil;
import com.moyz.adi.common.workflow.WorkflowStarter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9-01：webhook 触发鉴权与限流单元测试。
 * 覆盖：无效 token → null（controller 映射 403）/ 触发器停用 → null / 工作流停用 → null /
 * 匿名限流超限 → false（映射 429）/ 有效 token 派发（trigger_source=webhook）。
 */
class WorkflowWebhookAuthTest {

    private static final String TOKEN = "wh00000000000000000000000000001";
    private static final String WF_UUID = "w000000000000000000000000000001";

    private WorkflowTriggerService service;
    /** 执行侧（runTriggered）现在住在 Runner 里，测试用真实实例而非 mock */
    private WorkflowTriggerRunner realRunner;
    private WorkflowTriggerMapper triggerMapper;
    private WorkflowService workflowService;
    private WorkflowStarter workflowStarter;
    private UserService userService;
    private RedisTemplateUtil redisTemplateUtil;
    private AdiProperties adiProperties;

    @BeforeEach
    void setUp() {
        MpTableInfoTestSupport.initTableInfo(com.moyz.adi.common.entity.WorkflowTrigger.class);
        service = new WorkflowTriggerService();
        triggerMapper = mock(WorkflowTriggerMapper.class);
        workflowService = mock(WorkflowService.class);
        workflowStarter = mock(WorkflowStarter.class);
        userService = mock(UserService.class);
        redisTemplateUtil = mock(RedisTemplateUtil.class);
        adiProperties = new AdiProperties();

        ReflectionTestUtils.setField(service, "baseMapper", triggerMapper);
        ReflectionTestUtils.setField(service, "workflowService", workflowService);
        ReflectionTestUtils.setField(service, "redisTemplateUtil", redisTemplateUtil);
        ReflectionTestUtils.setField(service, "adiProperties", adiProperties);
        ReflectionTestUtils.setField(service, "workflowRuntimeService", mock(WorkflowRuntimeService.class));
        ReflectionTestUtils.setField(service, "workflowTriggerRunner", mock(WorkflowTriggerRunner.class));
        // 执行侧（runTriggered）现在住在 Runner 里，测试用真实实例而非 mock
        realRunner = new WorkflowTriggerRunner();
        ReflectionTestUtils.setField(realRunner, "workflowService", workflowService);
        ReflectionTestUtils.setField(realRunner, "userService", userService);
        ReflectionTestUtils.setField(realRunner, "workflowStarter", workflowStarter);
        ReflectionTestUtils.setField(realRunner, "workflowTriggerMapper", triggerMapper);
        ReflectionTestUtils.setField(realRunner, "adiProperties", adiProperties);
    }

    private WorkflowTrigger webhookTrigger(boolean enabled) {
        WorkflowTrigger trigger = new WorkflowTrigger();
        trigger.setId(21L);
        trigger.setUuid("t00000000000000000000000000002");
        trigger.setWorkflowId(100L);
        trigger.setTriggerType(WorkflowTriggerService.TYPE_WEBHOOK);
        trigger.setWebhookToken(TOKEN);
        trigger.setIsEnable(enabled);
        trigger.setInputParams(JsonUtil.createArrayNode());
        return trigger;
    }

    private Workflow enabledWorkflow(boolean enabled) {
        Workflow workflow = new Workflow();
        workflow.setId(100L);
        workflow.setUuid(WF_UUID);
        workflow.setIsEnable(enabled);
        workflow.setIsDeleted(false);
        workflow.setUserId(42L);
        return workflow;
    }

    @Test
    @DisplayName("无效 token（查不到触发器）→ null，controller 映射 HTTP 403")
    void invalidTokenReturnsNull() {
        when(triggerMapper.selectOne(any())).thenReturn(null);
        assertNull(service.findValidWebhook("bad-token"));
        assertNull(service.findValidWebhook(""));
    }

    @Test
    @DisplayName("触发器停用 → null（403）")
    void disabledTriggerReturnsNull() {
        //selectOne 的 wrapper 带 is_enable=true 条件，mock 层模拟「停用行查不到」
        when(triggerMapper.selectOne(any())).thenReturn(null);
        assertNull(service.findValidWebhook(TOKEN));
    }

    @Test
    @DisplayName("工作流停用/已删 → null（403），绝不让停用工作流被 webhook 拉起")
    void disabledWorkflowReturnsNull() {
        when(triggerMapper.selectOne(any())).thenReturn(webhookTrigger(true));
        when(workflowService.getById(100L)).thenReturn(enabledWorkflow(false));
        assertNull(service.findValidWebhook(TOKEN));
    }

    @Test
    @DisplayName("有效 token + 触发器/工作流均启用 → 返回触发器")
    void validTokenReturnsTrigger() {
        WorkflowTrigger trigger = webhookTrigger(true);
        when(triggerMapper.selectOne(any())).thenReturn(trigger);
        when(workflowService.getById(100L)).thenReturn(enabledWorkflow(true));
        assertEquals(trigger, service.findValidWebhook(TOKEN));
    }

    @Test
    @DisplayName("匿名限流：窗口内计数超限返回 false（映射真实 HTTP 429）")
    void rateLimitedWhenQuotaExceeded() {
        adiProperties.getWf().setWebhookPerMinuteLimit(2);
        when(redisTemplateUtil.increment(anyString(), anyLong())).thenReturn(1L, 2L, 3L);
        assertTrue(service.tryAcquireWebhookPermit(TOKEN));
        assertTrue(service.tryAcquireWebhookPermit(TOKEN));
        assertFalse(service.tryAcquireWebhookPermit(TOKEN), "第 3 次必须被限流");
    }

    @Test
    @DisplayName("Redis 异常（increment 返回 null）：fail-closed 拒绝而非放行")
    void redisFailureFailsClosed() {
        when(redisTemplateUtil.increment(anyString(), anyLong())).thenReturn(null);
        assertFalse(service.tryAcquireWebhookPermit(TOKEN));
    }

    @Test
    @DisplayName("限流维度是 token 摘要：明文 token 不进 Redis key")
    void rateLimitKeyUsesHashedToken() {
        adiProperties.getWf().setWebhookPerMinuteLimit(5);
        when(redisTemplateUtil.increment(anyString(), anyLong())).thenReturn(1L);
        service.tryAcquireWebhookPermit(TOKEN);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplateUtil).increment(keyCaptor.capture(), anyLong());
        assertFalse(keyCaptor.getValue().contains(TOKEN), "明文 token 不得出现在 Redis key 中");
        assertEquals(32, keyCaptor.getValue().split(":")[3].length(), "key 第四段应为 SHA-256 前 32 位摘要");
    }

    @Test
    @DisplayName("触发执行：以属主身份运行，trigger_source=webhook；结果进运行历史由引擎落库")
    void runTriggeredWebhookUsesOwnerAndSource() {
        User owner = new User();
        owner.setId(42L);
        when(workflowService.getByUuid(WF_UUID)).thenReturn(enabledWorkflow(true));
        when(userService.getById(42L)).thenReturn(owner);
        ArrayNode inputs = JsonUtil.createArrayNode();

        realRunner.runTriggered(null, WF_UUID, inputs, "webhook");

        verify(workflowStarter).blocking(eq(owner), eq(WF_UUID), any(), eq("webhook"));
        //webhook 触发器 last_fire_status 不参与（triggerId=null 时不更新）
        verify(triggerMapper, never()).update(any(), any());
    }
}
