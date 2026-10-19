package com.moyz.adi.common.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 二开新增（2026-09-21）：随 [META] 下发给前端的知识库引用来源。
 * 目标：用户能直接看到"这段回答来自哪个文档的哪段内容"，而不是只看到一个布尔标志。
 */
@Data
public class KbRefItem {

    @Schema(title = "来源类型: embedding / graph")
    private String source;

    @Schema(title = "知识库名称")
    private String kbTitle;

    @Schema(title = "文档名称")
    private String docTitle;

    @Schema(title = "命中片段内容（截断）")
    private String segmentContent;

    @Schema(title = "向量相似度分数（图谱来源为 null）")
    private Double score;
}
