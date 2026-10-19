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
}
