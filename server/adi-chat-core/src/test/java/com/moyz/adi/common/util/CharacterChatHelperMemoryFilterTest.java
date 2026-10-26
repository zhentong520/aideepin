package com.moyz.adi.common.util;

import com.moyz.adi.common.enums.MemoryType;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotEqualTo;
import dev.langchain4j.store.embedding.filter.logical.And;
import org.junit.jupiter.api.Test;

import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.CHARACTER_ID;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.MEMORY_TYPE;
import static com.moyz.adi.common.constant.MetadataConstant.MetadataKey.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * T7-07：聊天召回路径的用户维度过滤单测（CharacterChatHelper.retrieve 的组装逻辑）。
 * 隔离断言：不同用户的召回 filter 值不同；开关关闭回退历史行为。
 */
class CharacterChatHelperMemoryFilterTest {

    private static final Long CHARACTER_A = 101L;
    private static final Long USER_A = 1L;
    private static final Long USER_B = 2L;

    @Test
    void semanticFilterAppendsUserLeg() {
        IsNotEqualTo semanticLeg = new IsNotEqualTo(MEMORY_TYPE, MemoryType.EPISODIC.getDesc());
        And outer = assertInstanceOf(And.class,
                CharacterChatHelper.memorySemanticFilter(CHARACTER_A, USER_A, semanticLeg));
        And base = assertInstanceOf(And.class, outer.left(), "外层 And 左支 = 历史 character+memory_type 组合");
        IsEqualTo characterLeg = assertInstanceOf(IsEqualTo.class, base.left());
        assertEquals(CHARACTER_ID, characterLeg.key());
        IsEqualTo userLeg = assertInstanceOf(IsEqualTo.class, outer.right(), "外层 And 右支 = user_id 隔离腿");
        assertEquals(USER_ID, userLeg.key());
        assertEquals(String.valueOf(USER_A), userLeg.comparisonValue());
    }

    @Test
    void semanticFilterKeepsLegacyShapeWithoutUser() {
        IsNotEqualTo semanticLeg = new IsNotEqualTo(MEMORY_TYPE, MemoryType.EPISODIC.getDesc());
        And base = assertInstanceOf(And.class,
                CharacterChatHelper.memorySemanticFilter(CHARACTER_A, null, semanticLeg),
                "无用户（匿名/开关关）保持历史两腿结构，不多不少");
        assertInstanceOf(IsEqualTo.class, base.left());
        assertInstanceOf(IsNotEqualTo.class, base.right());
    }

    @Test
    void episodicFilterAppendsUserLeg() {
        And and = assertInstanceOf(And.class, CharacterChatHelper.memoryCharacterFilter(CHARACTER_A, USER_B));
        IsEqualTo characterLeg = assertInstanceOf(IsEqualTo.class, and.left());
        assertEquals(CHARACTER_ID, characterLeg.key());
        IsEqualTo userLeg = assertInstanceOf(IsEqualTo.class, and.right());
        assertEquals(String.valueOf(USER_B), userLeg.comparisonValue());
    }

    @Test
    void episodicFilterKeepsLegacyShapeWithoutUser() {
        Filter filter = CharacterChatHelper.memoryCharacterFilter(CHARACTER_A, null);
        IsEqualTo eq = assertInstanceOf(IsEqualTo.class, filter);
        assertEquals(CHARACTER_ID, eq.key());
        assertEquals(CHARACTER_A, eq.comparisonValue());
    }

    @Test
    void usersAreIsolatedInRecallFilters() {
        assertNotEquals(
                CharacterChatHelper.memoryCharacterFilter(CHARACTER_A, USER_A),
                CharacterChatHelper.memoryCharacterFilter(CHARACTER_A, USER_B),
                "同一角色下 A/B 用户的召回 filter 必须不同——userId 隔离在检索入口生效");
    }
}
