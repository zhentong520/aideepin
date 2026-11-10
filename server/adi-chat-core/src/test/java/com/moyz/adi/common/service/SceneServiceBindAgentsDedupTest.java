package com.moyz.adi.common.service;

import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.Scene;
import com.moyz.adi.common.entity.SceneAgent;
import com.moyz.adi.common.mapper.AgentMapper;
import com.moyz.adi.common.mapper.SceneAgentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * bindAgents 去重保序回归（2026-11-10 P3 收口）：
 * 同一请求携带重复 agentUuid 时必须规约为一次绑定——修复前第二个重复项走 insert 分支
 * 撞场景-Agent 绑定唯一约束，整个事务回滚且用户只看到「全局异常」。
 */
class SceneServiceBindAgentsDedupTest {

    private SceneService sceneService;
    private SceneAgentMapper sceneAgentMapper;
    private AgentMapper agentMapper;

    @BeforeEach
    void setUp() {
        sceneAgentMapper = mock(SceneAgentMapper.class);
        agentMapper = mock(AgentMapper.class);
        sceneService = org.mockito.Mockito.spy(new SceneService());
        ReflectionTestUtils.setField(sceneService, "sceneAgentMapper", sceneAgentMapper);
        ReflectionTestUtils.setField(sceneService, "agentMapper", agentMapper);

        Scene scene = new Scene();
        scene.setUuid("scene-1");
        doReturn(scene).when(sceneService).getByUuid("scene-1");
        when(agentMapper.selectOne(any())).thenReturn(new Agent());
        when(sceneAgentMapper.selectList(any())).thenReturn(Collections.emptyList());
    }

    @Test
    void duplicateAgentUuids_dedupedInOrder_oneInsertPerUniqueUuid() {
        sceneService.bindAgents("scene-1", Arrays.asList("a1", "a1", "a2", "a1"));

        ArgumentCaptor<SceneAgent> captor = ArgumentCaptor.forClass(SceneAgent.class);
        verify(sceneAgentMapper, times(2)).insert(captor.capture());
        List<String> bound = captor.getAllValues().stream().map(SceneAgent::getAgentUuid).toList();
        assertEquals(List.of("a1", "a2"), bound, "重复 uuid 必须去重且保持首次出现顺序");
    }

    @Test
    void blankEntries_skipped_notCounted() {
        sceneService.bindAgents("scene-1", Arrays.asList("a1", " ", ""));

        ArgumentCaptor<SceneAgent> captor = ArgumentCaptor.forClass(SceneAgent.class);
        verify(sceneAgentMapper, times(1)).insert(captor.capture());
        assertEquals("a1", captor.getValue().getAgentUuid());
    }
}
