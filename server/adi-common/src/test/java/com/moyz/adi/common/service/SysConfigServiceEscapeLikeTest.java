package com.moyz.adi.common.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SysConfigService.escapeLike 回归（2026-11-10 P3 收口）：
 * 配置页关键字搜索须按字面量匹配，用户输入的 % _ 不得当作 LIKE 通配符生效。
 */
class SysConfigServiceEscapeLikeTest {

    @Test
    void escapesPercentUnderscoreAndBackslash() {
        assertEquals("quota\\_max", SysConfigService.escapeLike("quota_max"));
        assertEquals("100\\%", SysConfigService.escapeLike("100%"));
        assertEquals("a\\\\b", SysConfigService.escapeLike("a\\b"));
        assertEquals("wf\\_launch\\_enabled", SysConfigService.escapeLike("wf_launch_enabled"));
    }

    @Test
    void plainKeywordUntouched() {
        assertEquals("plain", SysConfigService.escapeLike("plain"));
        assertEquals("wf-launch.enabled", SysConfigService.escapeLike("wf-launch.enabled"));
    }
}
