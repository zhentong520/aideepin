package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.mapper.RagQueryLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T1-01 验收：检索日志实体/服务 CRUD（mock mapper）。
 * 断言：异步写入透传全部关键字段；管理端筛选参数正确下发。
 */
class RagQueryLogServiceTest {

    private RagQueryLogService service;
    private RagQueryLogMapper mapper;

    @BeforeEach
    void setUp() {
        // LambdaQueryWrapper 依赖实体的 TableInfo 缓存，纯单测环境需手动初始化
        com.baomidou.mybatisplus.core.MybatisConfiguration configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        com.baomidou.mybatisplus.core.metadata.TableInfoHelper.initTableInfo(
                new org.apache.ibatis.builder.MapperBuilderAssistant(configuration, ""), RagQueryLog.class);
        service = new RagQueryLogService();
        mapper = mock(RagQueryLogMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        // 同步执行器：saveAsync 里的任务立即在当前线程跑，便于断言
        AsyncTaskExecutor syncExecutor = Runnable::run;
        ReflectionTestUtils.setField(service, "mainExecutor", syncExecutor);
    }

    @AfterEach
    void tearDown() {
        org.mockito.Mockito.reset(mapper);
    }

    @Test
    void buildFillsAllObservableFields() {
        RagQueryLog row = RagQueryLogService.build("q-1", "u-1", List.of("kb-a", "kb-b"),
                "IsIn(kb_uuid)", "[{\"kb_uuid\":\"kb-a\",\"score\":0.9}]",
                2, 1, 123, "local:bge-small-zh-v1.5", "如何配置知识库？");

        assertEquals("q-1", row.getQueryId());
        assertEquals("u-1", row.getUserUuid());
        assertNotNull(row.getKbUuids());
        assertTrue(row.getKbUuids().toString().contains("kb-a") && row.getKbUuids().toString().contains("kb-b"));
        assertEquals("IsIn(kb_uuid)", row.getFilterExpression());
        assertNotNull(row.getHitSegments());
        assertEquals(2, row.getHitCount());
        // 双保险剔除计数（T1-05 联动字段）
        assertEquals(1, row.getAclFilteredCount());
        assertEquals(123, row.getElapsedMs());
        assertEquals("local:bge-small-zh-v1.5", row.getEmbeddingModel());
        assertEquals("", row.getFeedback());
        assertEquals(0, row.getPromptTokens());
        // T7-01：问句原文落库（转评测题题面来源）
        assertEquals("如何配置知识库？", row.getQueryText());
    }

    @Test
    void buildTruncatesQueryTextTo500() {
        String longQuery = "长".repeat(2000);
        RagQueryLog row = RagQueryLogService.build("q-4", "u-4", List.of(), "f", null, 0, 0, 5, "m", longQuery);

        assertTrue(row.getQueryText().length() <= RagQueryLogService.QUERY_TEXT_MAX_LEN,
                "query_text 必须截断到 500 以内, got " + row.getQueryText().length());
        // abbreviate 语义：末尾带 … 省略号
        assertTrue(row.getQueryText().startsWith("长"));
    }

    @Test
    void buildNullQueryTextBecomesEmpty() {
        RagQueryLog row = RagQueryLogService.build("q-5", "u-5", List.of(), "f", null, 0, 0, 5, "m", null);
        assertEquals("", row.getQueryText());
    }

    @Test
    void saveAsyncInsertsRowWithoutThrowing() {
        RagQueryLog row = RagQueryLogService.build("q-2", "u-2", List.of("kb-a"), "f", null, 0, 0, 5, "m", "问句");
        when(mapper.insert(any(RagQueryLog.class))).thenReturn(1);

        service.saveAsync(row);

        ArgumentCaptor<RagQueryLog> captor = ArgumentCaptor.forClass(RagQueryLog.class);
        verify(mapper).insert(captor.capture());
        assertEquals("q-2", captor.getValue().getQueryId());
        // null hitSegments 兜底为空数组，避免 jsonb 落库为 null
        assertNotNull(captor.getValue().getHitSegments());
    }

    @Test
    void saveAsyncSwallowsMapperFailure() {
        RagQueryLog row = RagQueryLogService.build("q-3", "u-3", List.of(), "f", null, 0, 0, 5, "m", "问句");
        when(mapper.insert(any(RagQueryLog.class))).thenThrow(new RuntimeException("db down"));

        // 不抛异常：日志失败只 warn，绝不阻断检索链路
        service.saveAsync(row);
    }

    @SuppressWarnings("unchecked")
    @Test
    void pageQueryAppliesFilters() {
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(new Page<RagQueryLog>());
        service.pageQuery("u-9", "kb-x", "2026-10-01 00:00:00", "2026-10-16 23:59:59", 1, 20);
        ArgumentCaptor<Wrapper<RagQueryLog>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getTargetSql();
        assertTrue(sql.contains("user_uuid"), "应按用户筛选: " + sql);
        assertTrue(sql.contains("kb_uuids"), "应按库（jsonb 包含）筛选: " + sql);
        assertTrue(sql.contains("create_time"), "应按时间区间筛选: " + sql);
    }

    @SuppressWarnings("unchecked")
    @Test
    void pageQueryRejectsIllegalKbUuid() {
        AtomicReference<Boolean> called = new AtomicReference<>(false);
        doAnswer(inv -> {
            called.set(true);
            return new Page<RagQueryLog>();
        }).when(mapper).selectPage(any(Page.class), any(Wrapper.class));

        // 非法 kbUuid 直接返回空页，不触库
        Page<RagQueryLog> page = service.pageQuery(null, "kb'; drop table x", null, null, 1, 10);
        assertEquals(0, page.getTotal());
        assertEquals(false, called.get());
    }
}
