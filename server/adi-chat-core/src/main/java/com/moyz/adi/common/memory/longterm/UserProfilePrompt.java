package com.moyz.adi.common.memory.longterm;

/**
 * 二开新增（P0-1，2026-11-03）：结构化用户画像合并 prompt（Memobase 风格）。
 * <p>
 * 输入 = 现有画像 JSON + 本轮抽取出的新事实；输出 = 合并后的全量画像 JSON
 * + 新事件列表 + 被取代的旧事件原文（供 bi-temporal 失效）。要求输出 JSON 对象。
 */
final class UserProfilePrompt {

    private UserProfilePrompt() {
    }

    static final String PROFILE_MERGE_PROMPT = """
            You maintain a structured profile of the user, used to answer questions like
            "what does the user do for a living" instantly and consistently.

            You will receive:
            1. The existing profile as a JSON object (dimension name -> value). It may be empty.
            2. A list of new facts extracted from the latest conversation round.

            Your job:
            - Merge the new facts into the profile. Keep existing dimensions unchanged unless
              a new fact updates or contradicts them (then the new fact wins).
            - Use short snake_case English dimension names (e.g. occupation, language,
              skills, preferences, location, family) and keep values concise (one line).
            - Drop dimensions only when a new fact explicitly negates them.
            - From the new facts, list those that describe EVENTS (changes, transitions,
              decisions, purchases, moves...) — not stable attributes — in the "events" array,
              written as short self-contained sentences. Keep the user's own wording/period
              when it carries timing (e.g. "switched from sales to development in 2025").
            - If a new event supersedes an OLD event (the old one is no longer current),
              copy the OLD event text EXACTLY as given into "outdated_events". Never
              paraphrase it — matching is exact.

            Respond with a JSON object only:
            {"profile": {...}, "events": ["..."], "outdated_events": ["..."]}
            """;
}
