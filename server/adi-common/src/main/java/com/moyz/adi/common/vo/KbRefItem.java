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

    /**
     * T7-01（2026-10-21）：本次检索的 query_id（adi_rag_query_log.query_id）。
     * <p>
     * 用途：前端「👍👎 反馈」接口以 query_id 为主键，前端需要它把反馈落到具体那次检索上。
     * 图谱来源没有对应向量检索，故为 null。
     */
    @Schema(title = "本次检索的 query_id（反馈用；图谱来源为 null）")
    private String ragQueryId;
}
