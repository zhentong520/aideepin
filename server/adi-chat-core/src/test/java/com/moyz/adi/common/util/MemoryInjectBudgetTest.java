package com.moyz.adi.common.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * P0-3（2026-11-03）：记忆注入预算的按行截断单测。
 * <p>
 * 契约：预算内原样返回；超预算按最后一个换行截断（绝不半句话）；
 * 一个完整行都放不下返回空串；maxChars<=0 返回空串。
 */
class MemoryInjectBudgetTest {

    @Test
    void withinBudgetUnchanged() {
        String text = "- fact one\n- fact two\n";
        assertEquals(text, CharacterChatHelper.truncateAtLine(text, 100));
    }

    @Test
    void overBudgetTruncatesAtLastCompleteLine() {
        String text = "- aaaa\n- bbbb\n- cccc\n";
        // 12 字符预算："- aaaa\n- bbbb" 恰好 13 > 12 → 截到第一个 \n 后
        String truncated = CharacterChatHelper.truncateAtLine(text, 8);
        assertEquals("- aaaa\n", truncated, "必须按行边界截断，绝不留半行");
    }

    @Test
    void noRoomForCompleteLineReturnsEmpty() {
        assertEquals("", CharacterChatHelper.truncateAtLine("- long fact line\n", 3),
                "一个完整行都放不下时应返回空串");
    }

    @Test
    void nonPositiveBudgetReturnsEmpty() {
        assertEquals("", CharacterChatHelper.truncateAtLine("- x\n", 0));
        assertEquals("", CharacterChatHelper.truncateAtLine("- x\n", -1));
    }
}
