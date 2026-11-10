package com.moyz.adi.common.service.parse;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 解析结果（二开新增，2026-11-05）：DocumentParseService 降级链的统一输出。
 * <p>
 * Unified output of the document-parse fallback chain.
 * <p>
 * {@code text} 永不为 null（空串表示没解析出文本）；{@code unsupported} 表示该类型
 * 没有任何解析器能处理（上层据此拒绝上传）；{@code images} 仅 sidecar（MinerU）路径
 * 会产出，key 为文档内 markdown 引用的占位名（如 {@code images/xxx.jpg}）。
 */
@Data
@Builder
public class ParseResult {

    public static final String SOURCE_LANGCHAIN4J = "langchain4j";
    public static final String SOURCE_MINERU = "mineru";

    /** 解析出的文本（sidecar 路径为 markdown；\u0000 已剥离） | Parsed text, NUL stripped. */
    private String text;

    /** 解析来源 | Which layer produced the text. */
    private String source;

    /** 无任何解析器支持该类型 | No parser handles this file type. */
    private boolean unsupported;

    /** sidecar 解析出的内嵌图片（按文档中出现顺序） | Embedded images extracted by the sidecar. */
    @Builder.Default
    private List<ExtractedImage> images = List.of();

    public String textOrEmpty() {
        return null == text ? "" : text;
    }

    public boolean hasImages() {
        return null != images && !images.isEmpty();
    }

    /**
     * 文档内嵌图片。{@code refKey} 是 markdown 中的引用路径（如 {@code images/0.jpg}），
     * {@code fileName} 是给文件落盘用的展示名。
     */
    public record ExtractedImage(String refKey, String fileName, byte[] data, String ext) {
    }
}
