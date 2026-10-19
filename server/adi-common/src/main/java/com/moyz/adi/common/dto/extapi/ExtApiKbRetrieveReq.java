package com.moyz.adi.common.dto.extapi;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * POST /ext/v1/knowledge/retrieve 请求体（T1-08，2026-10-19）。
 * 纯检索不调 LLM：kbUuids 支持多库，无权/不存在的库静默跳过进 skippedKbUuids。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExtApiKbRetrieveReq {

    @NotEmpty(message = "kb_uuids is required")
    @Size(max = 10, message = "kb_uuids allows at most 10 knowledge bases per request")
    @JsonAlias("kb_uuids")
    private List<String> kbUuids;

    @NotBlank(message = "query is required")
    private String query;

    /**
     * 可选，单库召回条数上限；不传时用各库自己的 retrieve_max_results 配置。
     * 服务端还会用 adi.ext.max-top-k 做二次封顶。
     */
    @JsonAlias("top_k")
    private Integer topK;
}
