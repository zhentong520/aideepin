package com.moyz.adi.common.config.props;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 入库链路配置：解析之后的清洗与切分行为。
 * <p>
 * Ingest pipeline settings: how parsed text is cleaned before it is split into chunks.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Ingest} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Ingest {

    private Clean clean = new Clean();

    /**
     * 分块预览单次最多返回多少块，防止超大文档把接口与页面拖垮。
     * <p>
     * Max chunks returned by one chunk-preview call.
     */
    @Min(value = 1, message = "adi.ingest.preview-max-chunks 必须 >= 1")
    private int previewMaxChunks = 300;

    /**
     * 分块预览参与切分的正文最大字符数，超出部分截断（仅影响预览，不影响真实入库）。
     * <p>
     * Max characters of document text fed into a preview split (preview only).
     */
    @Min(value = 1000, message = "adi.ingest.preview-max-chars 必须 >= 1000")
    private int previewMaxChars = 400_000;
}
