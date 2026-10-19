package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.mapper.DocumentSegmentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邻段批量取数的守卫与范围算术。
 * <p>
 * 这里刻意断言「不查库」的分支：窗口半径非法、入参为空、命中缺 doc_uuid / position 时，
 * 都必须在发 SQL 之前就返回——否则检索路径会为一堆注定匹配不到行的查询付出数据库往返。
 */
class DocumentSegmentServiceNeighborWindowTest {

    private DocumentSegmentService service;
    private DocumentSegmentMapper mapper;

    @BeforeAll
    static void initTableInfo() {
        // 脱离 MyBatis 容器时 lambda 列名解析依赖 TableInfo，先手工初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                DocumentSegment.class);
    }

    @BeforeEach
    void setUp() {
        service = new DocumentSegmentService();
        mapper = mock(DocumentSegmentMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void returnsEmptyWithoutQueryingWhenWindowIsBelowOne() {
        Map<Long, DocumentSegment> result = service.mapNeighborWindow(List.of(hit("doc-1", 2)), 0);

        assertTrue(result.isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void returnsEmptyWithoutQueryingWhenInputIsEmpty() {
        assertTrue(service.mapNeighborWindow(List.of(), 1).isEmpty());
        assertTrue(service.mapNeighborWindow(null, 1).isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void skipsHitsWithoutPositionOrDocument() {
        DocumentSegment noPosition = new DocumentSegment();
        noPosition.setId(1L);
        noPosition.setDocUuid("doc-1");

        DocumentSegment noDoc = new DocumentSegment();
        noDoc.setId(2L);
        noDoc.setPosition(3);

        assertTrue(service.mapNeighborWindow(List.of(noPosition, noDoc), 1).isEmpty());
        verify(mapper, never()).selectList(any());
    }

    @Test
    void queriesOnceForAllHitsAndUsesPlusMinusWindow() {
        when(mapper.selectList(any())).thenReturn(List.of(row(11L, "doc-1", 1), row(12L, "doc-1", 2)));

        List<DocumentSegment> hits = List.of(hit("doc-1", 2), hit("doc-2", 10));
        Map<Long, DocumentSegment> result = service.mapNeighborWindow(hits, 1);

        assertEquals(Set.of(11L, 12L), result.keySet());
        // 两个命中合并成一条 SQL，不退化成"每个命中查一次"
        verify(mapper, times(1)).selectList(any());

        // 参数值由 wrapper 在渲染 SQL 片段时写入，必须先取一次 sqlSegment
        AbstractWrapper<?, ?, ?> wrapper = capturedWrapper();
        String sql = wrapper.getSqlSegment();
        assertEquals(2, occurrences(sql, "position BETWEEN"), "每个命中一组范围，实际：" + sql);
        assertTrue(wrapper.getParamNameValuePairs().values().containsAll(List.of(1, 3, 9, 11)),
                "窗口范围应为 position±window，实际参数：" + wrapper.getParamNameValuePairs().values());
    }

    @Test
    void deduplicatesIdenticalDocAndPositionPairs() {
        when(mapper.selectList(any())).thenReturn(List.of());

        // 同一段出现在两个命中的窗口里：不应生成重复的 OR 分组
        service.mapNeighborWindow(List.of(hit("doc-1", 2), hit("doc-1", 2)), 1);

        String sql = capturedWrapper().getSqlSegment();
        assertEquals(1, occurrences(sql, "position BETWEEN"),
                "重复命中只能产生一组范围条件，实际：" + sql);
    }

    private static int occurrences(String haystack, String needle) {
        int count = 0, index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + 1);
        }
        return count;
    }

    private AbstractWrapper<?, ?, ?> capturedWrapper() {
        org.mockito.ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.Wrapper<DocumentSegment>> captor =
                org.mockito.ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(captor.capture());
        return (AbstractWrapper<?, ?, ?>) captor.getValue();
    }

    private static DocumentSegment hit(String docUuid, int position) {
        DocumentSegment segment = new DocumentSegment();
        segment.setId((long) (1000 + position));
        segment.setDocUuid(docUuid);
        segment.setPosition(position);
        return segment;
    }

    private static DocumentSegment row(Long id, String docUuid, Integer position) {
        DocumentSegment segment = new DocumentSegment();
        segment.setId(id);
        segment.setDocUuid(docUuid);
        segment.setPosition(position);
        return segment;
    }
}
