package com.moyz.adi.common.workflow.orchestration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M2-T5 对话控制工具的参数解析边界测试（不触 DB 的前置路径）。
 * 覆盖：无用户上下文 / 非法 JSON / 缺 runtime_uuid / 空白 runtime_uuid；
 * 以及工具名常量与规格构建（名字一致是 execute 路由的前提）。
 * 归属校验/状态校验路径（依赖 WorkflowRuntimeService）由 T7 E2E 覆盖。
 */
class WorkflowControlToolTest {

    private final WorkflowControlTool tool = new WorkflowControlTool();

    @Test
    void toolNames_areFixedConstants() {
        assertEquals("cancel_workflow", WorkflowControlTool.CANCEL_TOOL_NAME);
        assertEquals("pause_workflow", WorkflowControlTool.PAUSE_TOOL_NAME);
        //规格名必须与常量一致，否则 execute 固定名路由永远打不中
        assertEquals(WorkflowControlTool.CANCEL_TOOL_NAME, tool.buildCancelSpecification().name());
        assertEquals(WorkflowControlTool.PAUSE_TOOL_NAME, tool.buildPauseSpecification().name());
    }

    @Test
    void noUserContext_returnsError() {
        assertTrue(tool.invokeCancel("{\"runtime_uuid\":\"abc\"}", null).startsWith("ERROR:"));
        assertTrue(tool.invokePause("{\"runtime_uuid\":\"abc\"}", null).startsWith("ERROR:"));
    }

    @Test
    void invalidJson_returnsError() {
        assertTrue(tool.invokeCancel("not a json", 1L).startsWith("ERROR:"));
        assertTrue(tool.invokeCancel("[1,2]", 1L).startsWith("ERROR:"));
        assertTrue(tool.invokePause("", 1L).startsWith("ERROR:"));
    }

    @Test
    void missingRuntimeUuid_returnsError() {
        assertTrue(tool.invokeCancel("{}", 1L).startsWith("ERROR:"));
        assertTrue(tool.invokeCancel("{\"other\":\"x\"}", 1L).startsWith("ERROR:"));
        assertTrue(tool.invokePause("{\"runtime_uuid\":\"   \"}", 1L).startsWith("ERROR:"));
        //非文本类型的 runtime_uuid 也必须拒绝（防 NPE / 防类型混淆）
        assertTrue(tool.invokeCancel("{\"runtime_uuid\":123}", 1L).startsWith("ERROR:"));
    }
}
