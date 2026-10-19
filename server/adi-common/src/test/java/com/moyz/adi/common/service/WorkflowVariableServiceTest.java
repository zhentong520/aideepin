package com.moyz.adi.common.service;

import com.moyz.adi.common.dto.workflow.WfAvailableVarDto;
import com.moyz.adi.common.dto.workflow.WfTemplateValidateResp;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.workflow.WfNodeInputConfig;
import com.moyz.adi.common.workflow.data.NodeIOData;
import com.moyz.adi.common.workflow.def.WfNodeIOText;
import com.moyz.adi.common.workflow.def.WfNodeParamRef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VAR_PATH_NOT_FOUND;
import static com.moyz.adi.common.enums.ErrorEnum.A_WF_VAR_ROOT_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3-13：available-vars 结构与模板校验单元测试（测纯逻辑核心，不启 Spring）。
 */
class WorkflowVariableServiceTest {

    private final WorkflowVariableService service = new WorkflowVariableService();

    private static WorkflowNode node(String uuid, String title, WfNodeInputConfig inputConfig) {
        WorkflowNode node = new WorkflowNode();
        node.setUuid(uuid);
        node.setTitle(title);
        node.setInputConfig(inputConfig);
        return node;
    }

    private static WfNodeInputConfig inputConfig(List<WfNodeParamRef> refInputs) {
        WfNodeInputConfig config = new WfNodeInputConfig();
        config.setUserInputs(List.of());
        config.setRefInputs(refInputs);
        return config;
    }

    @Test
    @DisplayName("available-vars：refInput 名为根变量，历史输出提供类型与示例值")
    void availableVarsCarryTypeAndExample() {
        WorkflowNode httpNode = node("http-uuid", "HTTP 请求", null);
        WorkflowNode llmNode = node("llm-uuid", "LLM 生成", inputConfig(List.of(
                WfNodeParamRef.builder().name("resp").nodeUuid("http-uuid").nodeParamName("output").build(),
                WfNodeParamRef.builder().name("raw").nodeUuid("http-uuid").nodeParamName("status_code").build()
        )));
        //最近一次成功执行的输出：output 为 JSON 文本（HTTP 响应体），status_code 为文本
        Map<String, List<NodeIOData>> history = Map.of(
                "http-uuid", List.of(
                        NodeIOData.createByText("output", "响应内容", "{\"data\":{\"id\":42}}"),
                        NodeIOData.createByText("status_code", "状态码", "200")
                )
        );

        List<WfAvailableVarDto> vars = service.buildAvailableVars(List.of(httpNode, llmNode), llmNode, history);

        assertEquals(2, vars.size(), "refInputs 两条引用都应出现");
        WfAvailableVarDto resp = vars.get(0);
        assertEquals("resp", resp.getName());
        assertEquals("http-uuid", resp.getSourceNodeUuid());
        assertEquals("HTTP 请求", resp.getSourceNodeTitle());
        //文本值本身是 JSON → 识别为 object 并展开子字段（字段级穿梭框依赖）
        assertEquals("object", resp.getType());
        assertTrue(resp.getChildren().stream().anyMatch(c -> "data".equals(c.getName())),
                "顶层对象应展开出 data 子字段");
        assertTrue(resp.getChildren().stream()
                        .filter(c -> "data".equals(c.getName()))
                        .findFirst().orElseThrow()
                        .getChildren().stream().anyMatch(c -> "id".equals(c.getName())),
                "第 2 层应展开出 data.id（递归 2 层深）");
        WfAvailableVarDto raw = vars.get(1);
        assertEquals("raw", raw.getName());
        assertEquals("text", raw.getType());
        assertEquals("200", raw.getExample());
    }

    @Test
    @DisplayName("available-vars：无历史时变量仍列出（类型 unknown、示例为空），穿梭框不空白")
    void availableVarsWithoutHistoryStillListed() {
        WorkflowNode httpNode = node("http-uuid", "HTTP 请求", null);
        WorkflowNode llmNode = node("llm-uuid", "LLM 生成", inputConfig(List.of(
                WfNodeParamRef.builder().name("resp").nodeUuid("http-uuid").nodeParamName("output").build()
        )));

        List<WfAvailableVarDto> vars = service.buildAvailableVars(List.of(httpNode, llmNode), llmNode, Map.of());

        assertEquals(1, vars.size());
        assertEquals("resp", vars.get(0).getName());
        assertEquals("unknown", vars.get(0).getType());
        assertFalse(vars.get(0).getName().isBlank());
    }

    @Test
    @DisplayName("模板校验：引用全部合法 → valid=true 且无错误")
    void validTemplatePasses() {
        WorkflowNode target = node("n1", "T", inputConfig(List.of(
                WfNodeParamRef.builder().name("resp").nodeUuid("up").nodeParamName("output").build()
        )));
        Map<String, List<NodeIOData>> history = Map.of(
                "up", List.of(NodeIOData.createByText("output", "o", "{\"data\":{\"id\":1}}"))
        );

        WfTemplateValidateResp resp = service.doValidateTemplate(target, history, "取 {{resp.data.id}} 与 {input}");

        assertTrue(resp.isValid());
        assertTrue(resp.getErrors().isEmpty());
    }

    @Test
    @DisplayName("模板校验：缺根变量 → 报错且错误码为 A_WF_VAR_ROOT_NOT_FOUND")
    void validateReportsMissingRoot() {
        WorkflowNode target = node("n1", "T", inputConfig(List.of(
                WfNodeParamRef.builder().name("resp").nodeUuid("up").nodeParamName("output").build()
        )));

        WfTemplateValidateResp resp = service.doValidateTemplate(target, Map.of(), "x={{wrong.root}}");

        assertFalse(resp.isValid());
        assertEquals(1, resp.getErrors().size());
        assertEquals("wrong.root", resp.getErrors().get(0).getRef());
        assertEquals(A_WF_VAR_ROOT_NOT_FOUND.getCode(), resp.getErrors().get(0).getCode());
    }

    @Test
    @DisplayName("模板校验：路径断点 → 报错且错误码为 A_WF_VAR_PATH_NOT_FOUND")
    void validateReportsBrokenPath() {
        WorkflowNode target = node("n1", "T", inputConfig(List.of(
                WfNodeParamRef.builder().name("resp").nodeUuid("up").nodeParamName("output").build()
        )));
        Map<String, List<NodeIOData>> history = Map.of(
                "up", List.of(NodeIOData.createByText("output", "o", "{\"data\":{\"id\":1}}"))
        );

        WfTemplateValidateResp resp = service.doValidateTemplate(target, history, "x={{resp.data.nope}}");

        assertFalse(resp.isValid());
        assertEquals(1, resp.getErrors().size());
        assertEquals("resp.data.nope", resp.getErrors().get(0).getRef());
        assertEquals(A_WF_VAR_PATH_NOT_FOUND.getCode(), resp.getErrors().get(0).getCode());
    }
}
