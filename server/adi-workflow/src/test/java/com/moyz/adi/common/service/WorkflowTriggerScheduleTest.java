package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.entity.WorkflowTrigger;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.WorkflowTriggerMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.RedisTemplateUtil;
import com.moyz.adi.common.workflow.WorkflowStarter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


import static com.moyz.adi.common.enums.ErrorEnum.A_WF_TRIGGER_DISABLED;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_TRIGGER_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9-01：定时触发单元测试（mock mapper/服务，不启 Spring）。
 * 覆盖：cron 解析/到期计算、防重入（上次未跑完不重复触发）、派发锁、总开关、
 * 触发执行以属主身份运行且 trigger_source 透传。
 */
class WorkflowTriggerScheduleTest {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String WF_UUID = "w000000000000000000000000000001";

    private WorkflowTriggerService service;
    private WorkflowTriggerMapper triggerMapper;
    private WorkflowService workflowService;
    private WorkflowRuntimeService workflowRuntimeService;
    private WorkflowTriggerRunner runner;
    /** 执行侧（runTriggered）现在住在 Runner 里，测试用真实实例而非 mock */
    private WorkflowTriggerRunner realRunner;
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
        workflowRuntimeService = mock(WorkflowRuntimeService.class);
        runner = mock(WorkflowTriggerRunner.class);
        workflowStarter = mock(WorkflowStarter.class);
        userService = mock(UserService.class);
        redisTemplateUtil = mock(RedisTemplateUtil.class);
        adiProperties = new AdiProperties();

        ReflectionTestUtils.setField(service, "baseMapper", triggerMapper);
        ReflectionTestUtils.setField(service, "workflowService", workflowService);
        ReflectionTestUtils.setField(service, "workflowRuntimeService", workflowRuntimeService);
        ReflectionTestUtils.setField(service, "workflowTriggerRunner", runner);
        ReflectionTestUtils.setField(service, "redisTemplateUtil", redisTemplateUtil);
        ReflectionTestUtils.setField(service, "adiProperties", adiProperties);

        realRunner = new WorkflowTriggerRunner();
        ReflectionTestUtils.setField(realRunner, "workflowService", workflowService);
        ReflectionTestUtils.setField(realRunner, "userService", userService);
        ReflectionTestUtils.setField(realRunner, "workflowStarter", workflowStarter);
        ReflectionTestUtils.setField(realRunner, "workflowTriggerMapper", triggerMapper);
        ReflectionTestUtils.setField(realRunner, "adiProperties", adiProperties);
    }

    private WorkflowTrigger scheduleTrigger(String cron, LocalDateTime lastFire) {
        WorkflowTrigger trigger = new WorkflowTrigger();
        trigger.setId(11L);
        trigger.setUuid("t00000000000000000000000000001");
        trigger.setWorkflowId(100L);
        trigger.setTriggerType(WorkflowTriggerService.TYPE_SCHEDULE);
        trigger.setCronExpr(cron);
        trigger.setIsEnable(true);
        trigger.setLastFireTime(lastFire);
        return trigger;
    }

    private Workflow enabledWorkflow() {
        Workflow workflow = new Workflow();
        workflow.setId(100L);
        workflow.setUuid(WF_UUID);
        workflow.setIsEnable(true);
        workflow.setIsDeleted(false);
        workflow.setUserId(42L);
        return workflow;
    }

    // ---------- cron 解析 ----------

    @Test
    @DisplayName("cron 到期计算：按 last_fire_time 推算下一时刻（每小时整点）")
    void nextFireTimeUsesLastFireAsBase() {
        WorkflowTrigger trigger = scheduleTrigger("0 0 * * * *", LocalDateTime.parse("2026-10-20 10:30:00", FMT));
        LocalDateTime next = WorkflowTriggerService.nextFireTime(trigger);
        assertEquals("2026-10-20 11:00:00", next.format(FMT));
    }

    @Test
    @DisplayName("cron 到期计算：无 last_fire_time 时以 create_time 为基准")
    void nextFireTimeFallsBackToCreateTime() {
        WorkflowTrigger trigger = scheduleTrigger("0 30 9 * * *", null);
        trigger.setCreateTime(LocalDateTime.parse("2026-10-20 08:00:00", FMT));
        LocalDateTime next = WorkflowTriggerService.nextFireTime(trigger);
        assertEquals("2026-10-20 09:30:00", next.format(FMT));
    }

    @Test
    @DisplayName("非法 cron：nextFireTime 返回 null（调度器跳过并告警），空表达式同理")
    void invalidCronReturnsNull() {
        assertNull(WorkflowTriggerService.nextFireTime(scheduleTrigger("not-a-cron", null)));
        assertNull(WorkflowTriggerService.nextFireTime(scheduleTrigger("", null)));
    }

    @Test
    @DisplayName("保存入口校验：非法 cron 抛 A_WF_TRIGGER_INVALID，不让坏表达式进库")
    void validateCronRejectsBadExpression() {
        BaseException e = assertThrows(BaseException.class,
                () -> WorkflowTriggerService.validateCron("61 * * * * *"));
        assertEquals(A_WF_TRIGGER_INVALID.getCode(), e.getCode());
        //合法表达式不抛
        WorkflowTriggerService.validateCron("0 */5 * * * *");
    }

    // ---------- 派发：防重入 / 锁 / 开关 ----------

    @Test
    @DisplayName("防重入：上次未跑完（存在 DOING 运行记录）不重复触发，且不推进 last_fire_time")
    void dispatchSkipsWhenPreviousRunStillDoing() {
        WorkflowTrigger trigger = scheduleTrigger("0 * * * * *", LocalDateTime.now().minusMinutes(1));
        when(redisTemplateUtil.lock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(workflowService.getById(100L)).thenReturn(enabledWorkflow());
        when(workflowRuntimeService.existsDoing(100L)).thenReturn(true);

        service.dispatchSchedule(trigger);

        verify(runner, never()).asyncFireSchedule(any(), anyString(), any());
        //防重入跳过时不允许写 last_fire_time/status（跑完后下一轮询正常补触发）
        verify(triggerMapper, never()).update(any(), any());
    }

    @Test
    @DisplayName("派发锁被其他实例持有：本轮不派发")
    void dispatchSkipsWhenLockHeld() {
        WorkflowTrigger trigger = scheduleTrigger("0 * * * * *", LocalDateTime.now().minusMinutes(1));
        when(redisTemplateUtil.lock(anyString(), anyString(), anyLong())).thenReturn(false);

        service.dispatchSchedule(trigger);

        verify(runner, never()).asyncFireSchedule(any(), anyString(), any());
    }

    @Test
    @DisplayName("正常派发：推进 last_fire_time 并派发（triggerId + workflowUuid + 默认入参）")
    void dispatchFiresWhenDue() {
        WorkflowTrigger trigger = scheduleTrigger("0 * * * * *", LocalDateTime.now().minusMinutes(1));
        ArrayNode defaultInputs = JsonUtil.createArrayNode();
        trigger.setInputParams(defaultInputs);
        when(redisTemplateUtil.lock(anyString(), anyString(), anyLong())).thenReturn(true);
        when(workflowService.getById(100L)).thenReturn(enabledWorkflow());
        when(workflowRuntimeService.existsDoing(100L)).thenReturn(false);

        service.dispatchSchedule(trigger);

        verify(runner).asyncFireSchedule(eq(11L), eq(WF_UUID), any());
        //markFired 落库：last_fire_time + status=1
        verify(triggerMapper).update(any(), any());
    }

    @Test
    @DisplayName("总开关关闭：pollAndDispatch 直接返回，不查询不派发（铁律 10 热回滚位）")
    void pollSkipsWhenMasterSwitchOff() {
        adiProperties.getWf().setTriggerEnabled(false);
        service.pollAndDispatch();
        verify(triggerMapper, never()).selectList(any());
        verify(runner, never()).asyncFireSchedule(any(), anyString(), any());
    }

    // ---------- 触发执行 ----------

    @Test
    @DisplayName("runTriggered：以属主身份阻塞运行，trigger_source 透传（webhook）")
    void runTriggeredUsesOwnerAndSource() {
        User owner = new User();
        owner.setId(42L);
        when(workflowService.getByUuid(WF_UUID)).thenReturn(enabledWorkflow());
        when(userService.getById(42L)).thenReturn(owner);
        ArrayNode inputs = JsonUtil.createArrayNode();

        realRunner.runTriggered(11L, WF_UUID, inputs, "webhook");

        verify(workflowStarter).blocking(eq(owner), eq(WF_UUID), any(), eq("webhook"));
    }

    @Test
    @DisplayName("runTriggered：属主缺失则放弃并标记派发失败（last_fire_status 更新）")
    void runTriggeredFailsWhenOwnerMissing() {
        when(workflowService.getByUuid(WF_UUID)).thenReturn(enabledWorkflow());
        when(userService.getById(42L)).thenReturn(null);

        realRunner.runTriggered(11L, WF_UUID, JsonUtil.createArrayNode(), "schedule");

        verify(triggerMapper).update(any(), any());
    }

    // ---------- API 触发 ----------

    @Test
    @DisplayName("API 触发：总开关关闭抛 A_WF_TRIGGER_DISABLED")
    void apiTriggerRefusedWhenSwitchOff() {
        adiProperties.getWf().setTriggerEnabled(false);
        BaseException e = assertThrows(BaseException.class,
                () -> service.dispatchApiTrigger(enabledWorkflow(), null));
        assertEquals(A_WF_TRIGGER_DISABLED.getCode(), e.getCode());
    }

    @Test
    @DisplayName("API 触发：开关开启时异步派发（trigger_source=api）")
    void apiTriggerDispatchesWhenSwitchOn() {
        adiProperties.getWf().setTriggerEnabled(true);
        boolean accepted = service.dispatchApiTrigger(enabledWorkflow(), null);
        org.junit.jupiter.api.Assertions.assertTrue(accepted);
        verify(runner).asyncFireApi(eq(WF_UUID), any());
    }
}
