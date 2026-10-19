package com.moyz.adi.common.dto.extapi;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExtApiWfRunReq {
    private List<ObjectNode> inputs;
    // 兼容驼峰/蛇形，否则 responseMode 被静默忽略、blocking 永远不可达
    // Accept both camelCase and snake_case, otherwise blocking mode is silently unreachable
    @JsonProperty("response_mode")
    @JsonAlias("responseMode")
    @Builder.Default
    private String responseMode = "streaming";
}
