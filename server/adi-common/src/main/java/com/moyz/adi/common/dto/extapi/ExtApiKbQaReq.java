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
public class ExtApiKbQaReq {
    @NotBlank(message = "query is required")
    private String query;
    private String model;
    // 只认蛇形 response_mode：调用方传驼峰 responseMode 会被静默忽略并恒走默认 streaming，
    // blocking 分支永远不可达 —— 曾导致"blocking 实际仍返回 SSE"。两种命名都接受。
    // Only snake_case response_mode was recognized: a camelCase responseMode was silently ignored and
    // the default "streaming" applied, making the blocking branch unreachable (blocking always returned SSE).
    @JsonProperty("response_mode")
    @JsonAlias("responseMode")
    @Builder.Default
    private String responseMode = "streaming";
}
