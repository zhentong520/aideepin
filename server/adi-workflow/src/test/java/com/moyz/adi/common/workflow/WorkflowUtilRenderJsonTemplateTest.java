package com.moyz.adi.common.workflow;

import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.workflow.data.NodeIOData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WorkflowUtil#renderJsonTemplate} 的单元测试（纯逻辑，不启 Spring）。
 * <p>
 * 背景：MCP 节点把上游文本塞进 tool arguments 的 JSON 模板里。上游文本是自由文本，
 * 常带引号/换行；如果只做字符串拼接，JSON 会被撑破，MCP 服务收到非法入参后
 * 报出的错误与真正原因相距很远。这里锁死「替换必须转义、结果必须是合法 JSON」。
 * </p>
 * 断言约定：按本项目「假 OK」红线，每条断言只问一句——被测功能彻底坏掉，它会红吗。
 * 因此一律断言具体值，不用「非空」这类在失败路径上也成立的表述。
 */
class WorkflowUtilRenderJsonTemplateTest {

    private static NodeIOData text(String name, String value) {
        return NodeIOData.createByText(name, name, value);
    }

    /**
     * 读渲染结果里的字段。渲染结果不可解析时抛 AssertionError（而不是让编译期检查的受检异常
     * 泄漏到每个测试方法上）——这样失败信息直接指出渲染结果本身有问题。
     */
    private static String readText(String renderedJson, String field) {
        try {
            return JsonUtil.getObjectMapper().readTree(renderedJson).path(field).asText();
        } catch (Exception e) {
            throw new AssertionError("渲染结果不是合法 JSON：" + renderedJson, e);
        }
    }

    private static int readInt(String renderedJson, String field) {
        try {
            return JsonUtil.getObjectMapper().readTree(renderedJson).path(field).asInt();
        } catch (Exception e) {
            throw new AssertionError("渲染结果不是合法 JSON：" + renderedJson, e);
        }
    }

    @Test
    @DisplayName("文本带引号/换行/反斜杠/制表符时：转义后仍是合法 JSON，且解析回来与原值完全一致")
    void escapesFreeTextAndRoundTripsExactly() {
        String raw = "He said \"hi\"\nline2\ttab\\slash";
        String rendered = WorkflowUtil.renderJsonTemplate("{\"q\": \"{input}\"}", List.of(text("input", raw)));

        // 必须能解析——拼接写法在这里会因为裸引号直接失败
        assertEquals(raw, readText(rendered, "q"), "解析回来的值必须与原始文本逐字符相等");
    }

    @Test
    @DisplayName("占位符不加引号时按 JSON 字面量处理（数字场景）")
    void supportsBareNumericPlaceholder() {
        String rendered = WorkflowUtil.renderJsonTemplate("{\"top_n\": {top_n}}", List.of(text("top_n", "5")));

        assertEquals(5, readInt(rendered, "top_n"));
    }

    @Test
    @DisplayName("渲染结果非法 JSON 时必须显式报错，不能把坏串静默传给下游")
    void throwsWhenRenderedResultIsNotValidJson() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> WorkflowUtil.renderJsonTemplate("{\"q\": {input}}", List.of(text("input", "plain text"))));
        assertTrue(ex.getMessage().contains("not a valid JSON"), "报错信息要指出是 JSON 非法，实际：" + ex.getMessage());
    }

    @Test
    @DisplayName("空模板退化为空对象")
    void blankTemplateBecomesEmptyObject() {
        assertEquals("{}", WorkflowUtil.renderJsonTemplate(null, List.of(text("input", "x"))));
        assertEquals("{}", WorkflowUtil.renderJsonTemplate("   ", List.of(text("input", "x"))));
    }

    @Test
    @DisplayName("多参数各自替换，短名不会吃掉长名（input 不得破坏 input2）")
    void multipleParamsDoNotInterfere() {
        String rendered = WorkflowUtil.renderJsonTemplate(
                "{\"a\": \"{input}\", \"b\": \"{input2}\"}",
                List.of(text("input", "AAA"), text("input2", "BBB")));

        assertEquals("AAA", readText(rendered, "a"));
        assertEquals("BBB", readText(rendered, "b"));
    }

    @Test
    @DisplayName("参数值为 null 时替换为空串，不能拼出字符串 \"null\"")
    void nullValueBecomesEmptyString() {
        NodeIOData nullValue = NodeIOData.createByText("v", "v", null);
        String rendered = WorkflowUtil.renderJsonTemplate("{\"v\": \"{v}\"}", List.of(nullValue));

        assertEquals("", readText(rendered, "v"));
    }

    @Test
    @DisplayName("模板中没有占位符时原样透传（只要本身是合法 JSON）")
    void templateWithoutPlaceholderPassesThrough() {
        String rendered = WorkflowUtil.renderJsonTemplate("{\"city\": \"北京\"}", List.of(text("input", "ignored")));

        assertEquals("北京", readText(rendered, "city"));
    }
}
