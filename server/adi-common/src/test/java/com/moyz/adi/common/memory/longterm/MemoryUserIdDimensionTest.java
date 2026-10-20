package com.moyz.adi.common.memory.longterm;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.cosntant.AdiConstant;
import com.moyz.adi.common.enums.MemoryType;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.CHARACTER_ID;
import static com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.MEMORY_TYPE;
import static com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.USER_ID;
import static com.moyz.adi.common.cosntant.AdiConstant.MetadataKey.USER_ID_UNKNOWN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-07：记忆用户维度单测——metadata 契约、召回隔离条件、迁移兼容。
 * <p>
 * 隔离语义：user_id 决定「哪个人」，userDimensionEnabled 时写入带 user_id、
 * 检索按 character_id + user_id 双条件；关闭开关则完全回退历史行为（单条件）。
 */
class MemoryUserIdDimensionTest {

    private static final Long CHARACTER_A = 101L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    // ==================== 写入侧：metadata 契约 ====================

    @Test
    void addMetadataContainsUserIdWhenEnabled() {
        Metadata metadata = SemanticMemoryService.memoryMetadata(CHARACTER_A, USER_A);
        Map<String, Object> map = metadata.toMap();
        assertEquals(String.valueOf(USER_A), map.get(USER_ID), "user_id 必须以字符串形式写入 metadata（与 V14 回填口径一致）");
        assertEquals(CHARACTER_A, map.get(CHARACTER_ID));
        assertEquals(MemoryType.SEMANTIC.getDesc(), map.get(MEMORY_TYPE));
    }

    @Test
    void addMetadataOmitsUserIdForNullUser() {
        Metadata metadata = SemanticMemoryService.memoryMetadata(CHARACTER_A, null);
        Map<String, Object> map = metadata.toMap();
        assertFalse(map.containsKey(USER_ID), "userId 未知时宁缺毋滥：绝不写 user_id 键（历史数据一致）");
    }

    @Test
    void writePathNeverWritesUnknownSentinel() {
        // 迁移兼容：哨兵值 '__unknown__' 只属于 V14 对无主存量的回填，
        // 正常写入路径（userId 非空）永远不产出哨兵
        assertNotEquals(USER_ID_UNKNOWN, String.valueOf(USER_A));
        Map<String, Object> map = SemanticMemoryService.memoryMetadata(CHARACTER_A, USER_A).toMap();
        assertNotEquals(USER_ID_UNKNOWN, map.get(USER_ID));
    }

    // ==================== 检索侧：隔离条件 ====================

    @Test
    void mergeFilterAddsUserLegWhenEnabled() {
        Filter filter = SemanticMemoryService.memoryFilter(CHARACTER_A, USER_A, true);
        And and = assertInstanceOf(And.class, filter, "开启用户维度应为 And(character_id, user_id)");
        IsEqualTo characterLeg = assertInstanceOf(IsEqualTo.class, and.left());
        assertEquals(CHARACTER_ID, characterLeg.key());
        IsEqualTo userLeg = assertInstanceOf(IsEqualTo.class, and.right());
        assertEquals(USER_ID, userLeg.key());
        assertEquals(String.valueOf(USER_A), userLeg.comparisonValue());
    }

    @Test
    void mergeFilterFallsBackToCharacterOnlyWhenDisabled() {
        Filter filter = SemanticMemoryService.memoryFilter(CHARACTER_A, USER_A, false);
        IsEqualTo eq = assertInstanceOf(IsEqualTo.class, filter, "开关关闭回退历史行为：仅 character_id 单条件");
        assertEquals(CHARACTER_ID, eq.key());
        assertEquals(CHARACTER_A, eq.comparisonValue());
    }

    @Test
    void mergeFilterFallsBackWhenUserIdUnknown() {
        Filter filter = SemanticMemoryService.memoryFilter(CHARACTER_A, null, true);
        assertInstanceOf(IsEqualTo.class, filter, "匿名/无法取到用户时保持历史行为");
    }

    @Test
    void differentUsersProduceDifferentIsolationValues() {
        And forA = assertInstanceOf(And.class, SemanticMemoryService.memoryFilter(CHARACTER_A, USER_A, true));
        And forB = assertInstanceOf(And.class, SemanticMemoryService.memoryFilter(CHARACTER_A, USER_B, true));
        IsEqualTo legA = assertInstanceOf(IsEqualTo.class, forA.right());
        IsEqualTo legB = assertInstanceOf(IsEqualTo.class, forB.right());
        assertNotEquals(legA.comparisonValue(), legB.comparisonValue(), "A/B 用户的隔离值必须不同——userId 隔离的根");
    }

    // ==================== 迁移兼容：哨兵约定 ====================

    @Test
    void unknownSentinelNeverMatchesRealUserIds() {
        // V14 回填对无主记忆写 '__unknown__'；它必须永不等于任何真实用户 id 的字符串，
        // 否则按用户过滤时无主记忆会漏给某个具体用户
        assertNotEquals(USER_ID_UNKNOWN, String.valueOf(USER_A));
        assertNotEquals(USER_ID_UNKNOWN, String.valueOf(USER_B));
        assertFalse(USER_ID_UNKNOWN.matches("\\d+"), "哨兵不是纯数字，绝不与 Long.toString(user_id) 相等");
        assertEquals("__unknown__", USER_ID_UNKNOWN);
        assertTrue(AdiConstant.MetadataKey.USER_ID_UNKNOWN.length() > 0);
    }

    // ==================== 开关配置兜底 ====================

    @Test
    void switchesDefaultOn() {
        AdiProperties properties = new AdiProperties();
        assertTrue(properties.getMemory().isUserDimensionEnabled(), "用户维度默认开（新行为）");
        assertTrue(properties.getMemory().isPiiConfirmEnabled(), "敏感确认默认开（⚠️卡要求：写入前必须确认）");
    }
}
