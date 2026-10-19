package com.moyz.adi.common.dto.workflow;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * T3-13：模板表达式校验请求。
 */
@Data
public class WfTemplateValidateReq {
    @NotBlank
    private String wfUuid;
    @NotBlank
    private String nodeUuid;
    /** 待校验的模板串（可含 {{var.path}} 引用） */
    @NotNull
    private String template;
}
