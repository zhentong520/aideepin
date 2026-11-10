package com.moyz.adi.common.memory.longterm;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.CHARACTER_ID;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.MEMORY_INVALID_AT;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.MEMORY_TYPE;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.MEMORY_VALID;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-2（2026-11-03）：语义记忆 bi-temporal 软关闭的单测——metadata 契约与活跃行过滤。
 * <p>
 * 契约：新写入行显式带 {@code memory_valid="true"}；软关闭行打
 * {@code memory_valid="false"} + {@code invalid_at}；检索/合并候选过滤一律叠加
 * {@code memory_valid != "false"}——缺键的存量行照常通过（历史兼容，与 pgvector
 * IsNotEqualTo 键缺失返回 true 的语义同源）。
 */
class MemoryBitemporalTest {

    private static final Long CHARACTER_A = 101L;
    private static final Long USER_A = 1L;

    // ==================== 写入侧：metadata 契约 ====================

    @Test
    void addMetadataMarksRowActive() {
        Map<String, Object> map = SemanticMemoryService.memoryMetadata(CHARACTER_A, USER_A).toMap();
        assertEquals("true", map.get(MEMORY_VALID), "新写入行必须显式标记 memory_valid=true（显式化，不只依赖缺键语义）");
        assertFalse(map.containsKey(MEMORY_INVALID_AT), "生效行绝不携带 invalid_at");
    }

    @Test
    void activeMemoryFilterComposesValidLeg() {
        Filter base = new IsEqualTo(CHARACTER_ID, CHARACTER_A);
        Filter composed = SemanticMemoryService.activeMemoryFilter(base);
        And and = assertInstanceOf(And.class, composed);
        IsEqualTo characterLeg = assertInstanceOf(IsEqualTo.class, and.left());
        assertEquals(CHARACTER_ID, characterLeg.key());
        IsNotEqualTo validLeg = assertInstanceOf(IsNotEqualTo.class, and.right());
        assertEquals(MEMORY_VALID, validLeg.key());
        assertEquals("false", validLeg.comparisonValue());
    }

    // ==================== 过滤语义：三类行的存活矩阵 ====================

    @Test
    void filterMatrixActiveAndClosed() {
        IsNotEqualTo leg = new IsNotEqualTo(MEMORY_VALID, "false");
        // 活跃行 → 通过。注意：内存版 Filter.test(Object) 要求入参 instanceof Metadata，
        // 传裸 Map 恒返回 false —— 生产链路传的就是 Metadata，这里保持同型。
        assertTrue(leg.test(new Metadata(Map.of(MEMORY_VALID, "true"))),
                "memory_valid=true 的活跃行必须通过过滤");
        // 软关闭行 → 排除（既不进 prompt 也不再参与归并）
        assertFalse(leg.test(new Metadata(Map.of(MEMORY_VALID, "false"))),
                "memory_valid=false 的软关闭行必须被排除");
    }

    /**
     * 缺键（存量行）语义的权威依据：
     * ① langchain4j-pgvector 1.14.1-beta24 的 PgVectorFilterMapper 对 IsNotEqualTo
     *    生成 SQL {@code (key is null or key != 'false')}——缺键返回 true；
     * ② langchain4j-core 1.14.1 的 IsNotEqualTo.test(Metadata) 字节码：
     *    {@code containsKey(key) == false → return true}——缺键同样返回 true。
     * 两端语义一致，缺键的存量行照常通过。V27 迁移已把全部存量语义行回填
     * memory_valid='true'，缺键场景在语义库中实际不再出现，但语义必须保持兼容。
     */
    @Test
    void legacyRowsPassWhenKeyMissing() {
        // 存量行无 memory_valid 键（V27 前）；SQL 与内存版缺键均返回 true → 通过。
        IsNotEqualTo leg = new IsNotEqualTo(MEMORY_VALID, "false");
        Metadata legacyRow = new Metadata(Map.of(CHARACTER_ID, 101L));
        assertFalse(legacyRow.toMap().containsKey(MEMORY_VALID), "前置：构造的行确实缺 memory_valid 键");
        assertTrue(leg.test(legacyRow), "缺键的存量行必须通过过滤（内存版与 pgvector SQL 语义一致）");
    }
}
