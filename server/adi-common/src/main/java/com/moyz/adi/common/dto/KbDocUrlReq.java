package com.moyz.adi.common.dto;

import com.moyz.adi.common.enums.SegmentModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 从网页 URL 创建知识库文档的请求。
 * <p>
 * Request for creating a knowledge-base document from a web page: the address is fetched once
 * now and, when {@code fetchIntervalMinutes} is greater than zero, re-fetched on that schedule.
 */
@Data
@Schema(title = "URL 数据源创建请求 | Create document from URL")
public class KbDocUrlReq {

    @NotBlank
    @Schema(title = "知识库uuid | Knowledge base uuid", requiredMode = Schema.RequiredMode.REQUIRED)
    private String kbUuid;

    @NotBlank
    @Schema(title = "网页地址（http/https）| Page URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String url;

    @Schema(title = "文档标题，留空取网页 title | Document title; falls back to the page <title>")
    private String title;

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
