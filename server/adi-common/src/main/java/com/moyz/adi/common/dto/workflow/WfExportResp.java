package com.moyz.adi.common.dto.workflow;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * T3-14：工作流导出/导入 JSON。导出即本结构，导入直接以本结构为请求体（round-trip 友好）。
 * 知识检索节点引用的 kbUuid 位于 nodeConfig.knowledge_base_uuid，导入时按 uuid 校验（T3-14）。
 */
@Data
public class WfExportResp {
    /** 导出格式版本 */
    private Integer version = 1;
    private String title;
    private String remark;
    @NotEmpty
    private List<WfNodeDto> nodes;
    private List<WfEdgeReq> edges;
}
