package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.Draw;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.helper.RateLimitHelper;
import com.moyz.adi.common.mapper.DrawMapper;
import com.moyz.adi.common.task.DrawTaskHandler;
import com.moyz.adi.common.task.TaskQueueService;
import com.moyz.adi.common.task.TaskResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import static com.moyz.adi.common.constant.GeneralConstant.GenerateImage.STATUS_DOING;
import static com.moyz.adi.common.constant.GeneralConstant.GenerateImage.STATUS_FAIL;
import static com.moyz.adi.common.constant.GeneralConstant.GenerateImage.STATUS_SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * S2-03：绘图任务迁 task_queue 的分支测试。
 * executeById 只执行 doing 行；终态（success/fail）→ ok（保持「失败手工 regenerate」语义，
 * 不自动重试、不重复扣配额）；生成内部失败落 draw 行而非任务失败。
 */
@ExtendWith(MockitoExtension.class)
class DrawServiceQueueTest {

    @Mock
    private DrawMapper drawMapper;
    @Mock
    private UserService userService;
    @Mock
    private TaskQueueService taskQueueService;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private RateLimitHelper rateLimitHelper;

    private DrawService service;
    private DrawService spyService;

    @BeforeEach
    void setUp() {
        service = new DrawService();
        ReflectionTestUtils.setField(service, "baseMapper", drawMapper);
        ReflectionTestUtils.setField(service, "userService", userService);
        ReflectionTestUtils.setField(service, "taskQueueService", taskQueueService);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        ReflectionTestUtils.setField(service, "rateLimitHelper", rateLimitHelper);
        spyService = spy(service);
    }

    private Draw draw(long id, int status) {
        Draw draw = new Draw();
        draw.setId(id);
        draw.setUserId(42L);
        draw.setProcessStatus(status);
        return draw;
    }

    @Test
    void executeById_rowMissing_returnsOk() {
        when(drawMapper.selectById(1L)).thenReturn(null);

        TaskResult result = service.executeById(1L);

        assertTrue(result.success());
        assertFalse(result.isDeferred());
        verifyNoInteractions(userService);
    }

    @Test
    void executeById_terminalDraw_skipsGeneration() {
        Draw done = draw(2L, STATUS_SUCCESS);
        when(drawMapper.selectById(2L)).thenReturn(done);
        assertTrue(service.executeById(2L).success());
        verifyNoInteractions(userService);

        Draw failed = draw(3L, STATUS_FAIL);
        when(drawMapper.selectById(3L)).thenReturn(failed);
        assertTrue(service.executeById(3L).success());
        verifyNoInteractions(userService);
        verify(drawMapper, never()).updateById(any(Draw.class));
    }

    @Test
    void executeById_ownerMissing_returnsOkWithoutGeneration() {
        Draw doing = draw(4L, STATUS_DOING);
        when(drawMapper.selectById(4L)).thenReturn(doing);
        when(userService.getByUserId(42L)).thenReturn(null);

        TaskResult result = service.executeById(4L);

        assertTrue(result.success());
        verify(drawMapper, never()).updateById(any(Draw.class));
    }

    @Test
    void executeById_doing_runsGeneration_failLandsOnDrawRowNotTask() {
        Draw doing = draw(5L, STATUS_DOING);
        doing.setAiModelName("no-such-model");
        when(drawMapper.selectById(5L)).thenReturn(doing);
        when(userService.getByUserId(42L)).thenReturn(new User());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);

        TaskResult result = service.executeById(5L);

        //模型不存在 → 生成失败落 draw.fail，任务本身仍 ok（不自动重试，等手工 regenerate）
        assertTrue(result.success());
        ArgumentCaptor<Draw> captor = ArgumentCaptor.forClass(Draw.class);
        verify(drawMapper).updateById(captor.capture());
        assertEquals(STATUS_FAIL, captor.getValue().getProcessStatus());
    }

    @Test
    void submitDrawQueue_putsDrawIdAndPriority4() {
        service.submitDrawQueue(7L, 42L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> payloadCaptor =
                ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(taskQueueService).submit(eq(DrawService.TASK_TYPE_DRAW), payloadCaptor.capture(),
                eq(DrawService.PRIORITY_DRAW), eq(42L));
        assertEquals(7L, payloadCaptor.getValue().get("drawId").asLong());
    }

    @Test
    void drawTaskHandler_delegatesToExecuteById() {
        DrawTaskHandler handler = new DrawTaskHandler();
        ReflectionTestUtils.setField(handler, "drawService", spyService);
        doReturn(TaskResult.defer(60, "busy")).when(spyService).executeById(9L);

        ObjectNode payload = new ObjectMapper().createObjectNode();
        payload.put("drawId", 9L);

        TaskResult result = handler.handle(payload);

        assertEquals("DRAW", handler.type());
        assertTrue(result.isDeferred());
    }
}
