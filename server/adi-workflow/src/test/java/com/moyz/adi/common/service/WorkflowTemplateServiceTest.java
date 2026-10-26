package com.moyz.adi.common.service;

import com.moyz.adi.common.dto.workflow.WfExportResp;
import com.moyz.adi.common.dto.workflow.WorkflowResp;
import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.moyz.adi.common.enums.ErrorEnum.A_WF_TEMPLATE_NOT_FOUND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T9-06：内置官方模板单元测试。
 * 覆盖：模板清单加载（资源文件）、未知模板 id 报错、从模板新建走导入链路并覆盖标题。
 */
class WorkflowTemplateServiceTest {

    private WorkflowTemplateService service;
    private WorkflowService workflowService;

    @BeforeEach
    void setUp() {
        service = new WorkflowTemplateService();
        workflowService = mock(WorkflowService.class);
        ReflectionTestUtils.setField(service, "workflowService", workflowService);
    }

    @Test
    @DisplayName("模板清单：内置「知识库问答」「邮件摘要」两个官方模板，结构完整（含节点与边）")
    void listReturnsBuiltinTemplates() {
        List<WorkflowTemplateService.WfTemplateInfo> templates = service.list();

        assertEquals(2, templates.size());
        assertTrue(templates.stream().anyMatch(t -> "kb-qa".equals(t.id()) && "知识库问答".equals(t.title())));
        assertTrue(templates.stream().anyMatch(t -> "mail-summary".equals(t.id()) && "邮件摘要".equals(t.title())));
        //模板 JSON 必须能解析为合法导出结构（round-trip 友好）
        WfExportResp kbQa = service.load("kb-qa");
        assertEquals(3, kbQa.getNodes().size(), "知识库问答模板应有 开始/知识检索/LLM回答 三个节点");
        assertEquals(2, kbQa.getEdges().size());
        WfExportResp mail = service.load("mail-summary");
        assertEquals(2, mail.getNodes().size());
        assertEquals(1, mail.getEdges().size());
    }

    @Test
    @DisplayName("未知模板 id → A_WF_TEMPLATE_NOT_FOUND")
    void loadUnknownTemplateFails() {
        BaseException e = assertThrows(BaseException.class, () -> service.load("no-such-template"));
        assertEquals(A_WF_TEMPLATE_NOT_FOUND.getCode(), e.getCode());
    }

    @Test
    @DisplayName("从模板新建：覆盖 title/remark 后走导入链路（名字冲突由导入链路加后缀）")
    void createFromTemplateOverridesTitleAndDelegatesToImport() {
        WorkflowResp fakeResp = new WorkflowResp();
        when(workflowService.importWorkflow(any())).thenReturn(fakeResp);

        WorkflowResp resp = service.createFromTemplate("kb-qa", "我的问答流", "备注");

        assertEquals(fakeResp, resp);
        org.mockito.ArgumentCaptor<WfExportResp> captor = org.mockito.ArgumentCaptor.forClass(WfExportResp.class);
        verify(workflowService).importWorkflow(captor.capture());
        assertEquals("我的问答流", captor.getValue().getTitle(), "模板标题必须被用户输入覆盖");
        assertEquals("备注", captor.getValue().getRemark());
        assertEquals(3, captor.getValue().getNodes().size(), "模板节点必须原样带入");
    }
}
