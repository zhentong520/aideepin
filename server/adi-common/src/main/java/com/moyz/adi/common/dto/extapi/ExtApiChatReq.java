package com.moyz.adi.common.dto.extapi;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExtApiChatReq {
    @NotBlank(message = "query is required")
    private String query;
    private String model;
    // 兼容驼峰/蛇形，否则 responseMode 被静默忽略、blocking 永远不可达
    // Accept both camelCase and snake_case, otherwise blocking mode is silently unreachable
    @JsonProperty("response_mode")
    @JsonAlias("responseMode")
    @Builder.Default
    private String responseMode = "streaming";
}
