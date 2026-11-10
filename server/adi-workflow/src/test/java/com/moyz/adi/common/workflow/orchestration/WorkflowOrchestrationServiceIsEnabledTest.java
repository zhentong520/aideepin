package com.moyz.adi.common.workflow.orchestration;

import com.moyz.adi.common.util.LocalCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 灰度总开关 wf_launch_enabled 解析边界测试（M1 全链路验证 · 边界层）。
 * isEnabled() 缺省关语义：无配置行/blank/false/非布尔 → 一律关闭；
 * "true"/"TRUE"/带空白 → 开启；"1" 是坑（parseBoolean 不认数字）。
 */
class WorkflowOrchestrationServiceIsEnabledTest {

    private final WorkflowOrchestrationService service = new WorkflowOrchestrationService();
    private static final String KEY = "wf_launch_enabled";

    @BeforeEach
    void setUp() {
        LocalCache.CONFIGS.remove(KEY);
    }

    @AfterEach
    void tearDown() {
        //恢复灰度真实状态，避免污染同 JVM 后续测试
        LocalCache.CONFIGS.remove(KEY);
    }

    private boolean isEnabledWith(String value) {
        if (null != value) {
            LocalCache.CONFIGS.put(KEY, value);
        }
        return service.isEnabled();
    }

    @Test
    void absentConfig_meansDisabled() {
        assertEquals(false, isEnabledWith(null), "无配置行必须缺省关（灰度安全语义）");
    }

    @Test
    void blankAndFalseVariants_meanDisabled() {
        assertEquals(false, isEnabledWith(""), "空串必须关");
        assertEquals(false, isEnabledWith("   "), "纯空白必须关");
        assertEquals(false, isEnabledWith("false"), "false 必须关");
        assertEquals(false, isEnabledWith("False"), "False 必须关");
        assertEquals(false, isEnabledWith("0"), "数字 0 必须关");
        assertEquals(false, isEnabledWith("1"), "数字 1 必须关（parseBoolean 不认 1，写 1 等于关——已知坑）");
        assertEquals(false, isEnabledWith("yes"), "yes 必须关");
        assertEquals(false, isEnabledWith("on"), "on 必须关");
        assertEquals(false, isEnabledWith("truetrue"), "非法词必须关");
    }

    @Test
    void trueVariants_meanEnabled() {
        assertEquals(true, isEnabledWith("true"), "true 必须开");
        assertEquals(true, isEnabledWith("TRUE"), "TRUE 必须开（大小写不敏感）");
        assertEquals(true, isEnabledWith("True"), "True 必须开");
        assertEquals(true, isEnabledWith(" true "), "带首尾空白的 true 必须开（trim 兜底）");
    }
}
