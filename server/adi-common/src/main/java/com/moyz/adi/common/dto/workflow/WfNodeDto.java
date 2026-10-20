package com.moyz.adi.common.dto.workflow;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.validation.annotation.Validated;

@Validated
@Data
public class WfNodeDto {
    private Long id;
    @NotBlank
    @Size(min = 32, max = 32)
    private String uuid;
    private Long workflowId;
    @Min(1)
    private Long workflowComponentId;
    /**
     * 二开新增（T3-14）：组件名，仅导出时填充；导入时当 workflowComponentId 在目标环境
     * 匹配不到（跨环境组件自增 id 不一致）按名称重映射。
     */
    private String componentName;
    @NotBlank
    private String title;
    private String remark;
    @NotNull
    private ObjectNode inputConfig;
    @NotNull
    private ObjectNode nodeConfig;
    /**
     * 二开新增（T2-07）：凭据库引用，必须能回读（铁律 3），否则保存一次即丢失凭据关联。
     */
    private String credentialUuid;
    @NotNull
    private Double positionX;
    @NotNull
    private Double positionY;

    /**
     * 二开新增（T9-08）：画布布局元数据（Container 分组/折叠标记等纯布局信息），
     * 必须能读回（铁律 3），否则分组保存一次即丢失。引擎侧不解析、不影响执行语义。
     */
    private ObjectNode layout;
}
