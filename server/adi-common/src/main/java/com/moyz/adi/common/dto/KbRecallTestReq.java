package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 召回测试请求（管理后台「知识库 → 召回测试」）。
 * <p>
 * 除 kbUuid / query 外的字段全部可选：<b>为 null 表示沿用知识库上已保存的配置</b>，
 * 传了值只在本页临时生效、不落库——这正是这个页面存在的意义：把参数调好再存回知识库。
 * <p>
 * Recall-test request. Every field except kbUuid/query is nullable; null means "use the value
 * stored on the knowledge base". Overrides apply to this test only and are never persisted.
 */
@Data
public class KbRecallTestReq {

    @Schema(title = "知识库uuid | Knowledge base UUID")
    @NotBlank
    private String kbUuid;

    @Schema(title = "查询文本 | Query text")
    @NotBlank
    private String query;

    /**
     * 候选池大小（= 生产链路的 retrieve_max_results）。<=0 表示按知识库配置的"自动"，
     * 自动模式下按 {@code AdiConstant.RAG_RETRIEVE_NUMBER_MAX} 取池——池大小是召回上限，
     * 必须让使用者看得见，否则无法判断"是没取回来"还是"取了但排不上"
     */
    @Schema(title = "候选池大小 | Candidate pool size")
    private Integer topK;

    /**
     * 命中的最小 store score。注意量纲：pgvector 存的是 {@code (1+cos)/2}，不是余弦本身
     */
    @Schema(title = "最小命中分数 | Minimum hit score")
    private Double minScore;

    @Schema(title = "启用混合检索（向量+关键词） | Enable hybrid retrieval")
    private Boolean hybridEnabled;

    @Schema(title = "关键词路召回条数上限 | Keyword path limit")
    private Integer keywordLimit;

    @Schema(title = "RRF 融合常数 k | RRF constant")
    private Integer rrfK;

    @Schema(title = "启用重排 | Enable rerank")
    private Boolean rerankEnabled;

    @Schema(title = "重排模型id，0=第一个可用的 rerank 模型 | Rerank model id")
    private Long rerankModelId;

    @Schema(title = "重排后保留条数，0=不截断 | Keep top N after rerank")
    private Integer rerankTopN;

    @Schema(title = "重排分数下限（sigmoid 归一化后） | Rerank min score")
    private Double rerankMinScore;

    @Schema(title = "启用命中片段左右扩窗 | Enable neighbour window")
    private Boolean neighborEnabled;

    @Schema(title = "扩窗半径 | Neighbour window radius")
    private Integer neighborWindow;
}
