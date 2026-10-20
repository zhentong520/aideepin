package com.moyz.adi.common.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * T9-06：从内置模板新建工作流请求。
 */
@Data
public class WfTemplateCreateReq {
    @NotBlank
    private String templateId;
    @NotBlank
    private String title;
    private String remark;
}
