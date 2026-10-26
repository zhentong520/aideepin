package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 扫描件 OCR 配置项。
 * <p>
 * Configuration for the optional OCR service used when a document has no text layer
 * (scanned PDF / screenshot). Disabled by default: without an endpoint the upload path
 * keeps flagging such documents as failed with a readable reason instead of silently
 * indexing nothing.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Ocr} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Ocr {
    /**
     * 是否启用 OCR 兜底 | Whether OCR fallback is enabled.
     */
    private boolean enabled = false;

    /**
     * OCR 服务地址，multipart 上传图片后返回 JSON 文本 | OCR endpoint accepting a multipart image.
     */
    private String endpoint;

    /**
     * 鉴权 key；为空则不发送鉴权头 | Auth key; no auth header is sent when blank.
     */
    private String apiKey = "";

    /**
     * 自定义鉴权头名，留空时用 {@code Authorization: Bearer <apiKey>} | Custom auth header name.
     */
    private String apiKeyHeader = "";

    /**
     * multipart 中文件字段名 | Multipart field name of the image.
     */
    private String fileField = "file";

    /**
     * 语言参数（作为额外表单字段 lang 提交，留空则不提交）| Optional language form field.
     */
    private String lang = "";

    @Min(value = 100, message = "adi.ocr.connect-timeout-ms 至少 100ms")
    private int connectTimeoutMs = 10_000;

    @Min(value = 100, message = "adi.ocr.read-timeout-ms 至少 100ms")
    private int readTimeoutMs = 60_000;

    /**
     * 扫描件渲染 DPI | Rendering DPI for scanned PDF pages.
     */
    @Min(value = 50, message = "adi.ocr.dpi 合理区间 50-600")
    @Max(value = 600, message = "adi.ocr.dpi 合理区间 50-600（过高拖慢渲染）")
    private int dpi = 200;

    /**
     * 单个 PDF 最多识别页数，超出部分截断并告警 | Max pages recognized per PDF.
     */
    @Min(value = 1, message = "adi.ocr.max-pages 必须 >= 1")
    private int maxPages = 20;

    /**
     * 单文件上限（MB），超出直接放弃 OCR | Max file size (MB) accepted by OCR.
     */
    @Min(value = 1, message = "adi.ocr.max-file-mb 必须 >= 1")
    private int maxFileMb = 40;
}
