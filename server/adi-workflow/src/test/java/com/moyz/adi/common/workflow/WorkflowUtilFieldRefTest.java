package com.moyz.adi.common.workflow;

import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VAR_PATH_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VAR_ROOT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T3-13：字段级变量引用（{@code {{var.path.to.field}}}）单元测试（纯逻辑，不启 Spring）。
 * <p>
 * 断言约定：按本项目「假 OK」红线，每条断言只问一句——被测功能彻底坏掉，它会红吗。
 * 因此一律断言具体值 / 具体错误码，不用「非空」这类在失败路径上也成立的表述。
 */
class WorkflowUtilFieldRefTest {

    @BeforeEach
    void setUp() {
        //BaseException 带参文案经 MessageFormat 还原，mock MessageSource 返回带 {0}/{1} 的真实模板
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        MessageSource messageSource = mock(MessageSource.class);
        when(applicationContext.getBean(MessageSource.class)).thenReturn(messageSource);
        when(messageSource.getMessage(any(String.class), any(), any())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return switch (key) {
                case "A_WF_VAR_ROOT_NOT_FOUND" -> "变量「{0}」在当前节点可用输入中不存在";
                case "A_WF_VAR_PATH_NOT_FOUND" -> "变量「{0}」的字段路径在「{1}」处断开：引用的字段不存在";
                default -> key;
            };
        });
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", applicationContext);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(SpringUtil.class, "applicationContext", null);
    }

    private static NodeIOData text(String name, String value) {
        return NodeIOData.createByText(name, name, value);
    }

    private static NodeIOData options(String name, Map<String, Object> value) {
        return NodeIOData.createByOptions(name, name, value);
    }

    @Test
    @DisplayName("点路径取值：{{resp.data.id}} 取出嵌套字段值")
    void resolvesNestedFieldPath() {
        String rendered = WorkflowUtil.renderTemplateStrict(
                "id={{resp.data.id}}",
                List.of(options("resp", Map.of("data", Map.of("id", 42)))),
                true);

        assertEquals("id=42", rendered);
    }

    @Test
    @DisplayName("HTTP 响应场景：TEXT 值本身是 JSON 字符串时，点路径可直接取字段")
    void resolvesFieldInsideTextualJson() {
        String rendered = WorkflowUtil.renderTemplateStrict(
                "user={{resp.data.name}}",
                List.of(text("resp", "{\"data\":{\"name\":\"alice\"}}")),
                true);

        assertEquals("user=alice", rendered);
    }

    @Test
    @DisplayName("{{var}} 无点路径：整值替换（对象输出紧凑 JSON）")
    void resolvesRootVariableWholeValue() {
        String rendered = WorkflowUtil.renderTemplateStrict(
                "v={{resp}}",
                List.of(text("resp", "plain")),
                true);

        assertEquals("v=plain", rendered);
    }

    @Test
    @DisplayName("数组下标：{{resp.items.1}} 取第二个元素")
    void resolvesArrayIndex() {
        String rendered = WorkflowUtil.renderTemplateStrict(
                "second={{resp.items.1}}",
                List.of(options("resp", Map.of("items", List.of("a", "b")))),
                true);

        assertEquals("second=b", rendered);
    }

    @Test
    @DisplayName("缺根变量：立即报错（A_WF_VAR_ROOT_NOT_FOUND），不静默跑")
    void missingRootVariableThrows() {
        BaseException e = assertThrows(BaseException.class,
                () -> WorkflowUtil.renderTemplateStrict("x={{nope.a}}", List.of(text("resp", "v")), true));

        assertEquals(A_WF_VAR_ROOT_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("根变量存在但路径断开：报错并指明断点段（A_WF_VAR_PATH_NOT_FOUND）")
    void brokenPathThrowsWithBreakpoint() {
        BaseException e = assertThrows(BaseException.class,
                () -> WorkflowUtil.renderTemplateStrict(
                        "x={{resp.data.missing.deep}}",
                        List.of(options("resp", Map.of("data", Map.of("id", 1)))),
                        true));

        assertEquals(A_WF_VAR_PATH_NOT_FOUND.getCode(), e.getCode());
        //错误信息必须指明断在哪一段（missing），才能定位配置错误
        assertTrue(e.getInfo().contains("missing"), "错误信息应包含断点路径段，实际：" + e.getInfo());
    }

    @Test
    @DisplayName("根变量存在但路径落在对象不存在的字段上：报错并指明断点段")
    void missingFieldInObjectThrows() {
        BaseException e = assertThrows(BaseException.class,
                () -> WorkflowUtil.renderTemplateStrict(
                        "x={{resp.wrong}}",
                        List.of(options("resp", Map.of("data", Map.of("id", 1)))),
                        true));

        assertEquals(A_WF_VAR_PATH_NOT_FOUND.getCode(), e.getCode());
        assertTrue(e.getInfo().contains("wrong"));
    }

    @Test
    @DisplayName("开关关闭：回退旧版宽松行为（{{var.path}} 不解析、不抛错，{name} 照常替换）")
    void switchOffFallsBackToLenientBehavior() {
        String rendered = WorkflowUtil.renderTemplateStrict(
                "a={{nope.a}} b={resp}",
                List.of(text("resp", "legacy")),
                false);

        //旧版宽松行为：未匹配的 {{...}} 保持原样，单花括号照常替换
        assertEquals("a={{nope.a}} b=legacy", rendered);
    }

    @Test
    @DisplayName("双花括号解析后再走旧版 {name} 替换，两种语法可共存")
    void doubleBraceThenLegacySingleBraceCoexist() {
        String rendered = WorkflowUtil.renderTemplateStrict(
                "{input} / {{resp.data.id}}",
                List.of(text("input", "legacy"), options("resp", Map.of("data", Map.of("id", 7)))),
                true);

        assertEquals("legacy / 7", rendered);
    }
}
