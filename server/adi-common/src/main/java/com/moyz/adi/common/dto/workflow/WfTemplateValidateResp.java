package com.moyz.adi.common.dto.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * T3-13：模板表达式校验结果。errors 非空即校验失败，每条错误指明引用与原因。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WfTemplateValidateResp {
    private boolean valid;
    private List<WfVarError> errors;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class WfVarError {
        /** 出错的引用表达式，如 resp.data.id */
        private String ref;
        /** 错误码（A_WF_VAR_ROOT_NOT_FOUND / A_WF_VAR_PATH_NOT_FOUND） */
        private String code;
        /** 人类可读原因（已 i18n） */
        private String message;
    }
}
