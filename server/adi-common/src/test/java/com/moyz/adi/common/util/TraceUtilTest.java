package com.moyz.adi.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6-14 验收：traceId 贯穿语义（生成/复用/清理）。
 */
class TraceUtilTest {

    @AfterEach
    void tearDown() {
        TraceUtil.clear();
    }

    @Test
    void ensureCreatesAndStaysStableWithinThread() {
        String first = TraceUtil.ensureTraceId();
        assertTrue(first.matches("[0-9a-f]{32}"), "traceId 应为 32 位短 uuid");
        // 同线程内贯穿：再次获取是同一个 id（检索→LLM→回复 各阶段一致）
        assertEquals(first, TraceUtil.getTraceId());
        assertEquals(first, TraceUtil.ensureTraceId());
    }

    @Test
    void twoCallsGetDifferentTraceIds() {
        String first = TraceUtil.ensureTraceId();
        TraceUtil.clear();
        String second = TraceUtil.ensureTraceId();
        assertNotEquals(first, second);
    }

    @Test
    void clearIfCreatedOnlyClearsWhenCreated() {
        String existing = TraceUtil.ensureTraceId();
        // 复用方（非创建方）清理不应抹掉外层上下文
        TraceUtil.clearIfCreated(false);
        assertEquals(existing, TraceUtil.getTraceId());
        // 创建方清理生效
        TraceUtil.clearIfCreated(true);
        assertEquals("", TraceUtil.getTraceId());
    }

    @Test
    void getTraceIdWithoutContextIsEmpty() {
        assertEquals("", TraceUtil.getTraceId());
    }
}
