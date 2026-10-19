package com.moyz.adi.common.dto;

import com.moyz.adi.common.enums.SegmentModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 HTTP API 创建知识库文档的请求。
 * <p>
 * Request for creating a knowledge-base document from an HTTP API: the endpoint is called with
 * the configured method, headers and body; when the response is JSON, {@code contentPath} picks
 * the field holding the text (dot path such as {@code data.list.content}).
 */
@Data
@Schema(title = "API 数据源创建请求 | Create document from API")
public class KbDocApiReq {

    @NotBlank
    @Schema(title = "知识库uuid | Knowledge base uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private String kbUuid;

    @NotBlank
    @Schema(title = "文档标题 | Document title", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @NotBlank
    @Schema(title = "接口地址（http/https）| API endpoint", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(title = "请求方法 GET/POST，默认 GET | HTTP method, defaults to GET")
    private String method;

    @Schema(title = "自定义请求头，如 Authorization | Custom request headers")
    private Map<String, String> headers = new LinkedHashMap<>();

    @Schema(title = "请求体（POST）| Request body")
    private String body;

    @Schema(title = "JSON响应取值路径，如 data.list.content；留空取整段响应 | Dot path to the text in a JSON response")
    private String contentPath;

    @Schema(title = "分段模式，默认 text | Segment mode, defaults to text")
    private SegmentModeEnum segmentMode;

    @Schema(title = "父子模式子块最大token数(50-4000) | Parent-child child chunk size")
    private Integer childMaxChunkSize;

    @Schema(title = "自动同步周期（分钟），0=仅手动 | Auto re-fetch period in minutes, 0 = manual only")
    private Integer fetchIntervalMinutes;

    @Schema(title = "抓取后是否立即索引，默认 true | Index right after fetching")
    private Boolean indexAfterFetch;

    @Schema(title = "索引类型，留空默认 embedding | Index types; defaults to embedding")
    private List<String> indexTypes;
}
