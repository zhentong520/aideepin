package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 召回测试结果。
 * <p>
 * 设计原则是"把判断依据全给出去"，而不是只给一个排名：
 * <ul>
 *   <li>{@link Stage} 把漏斗每一格的条数摊开——尤其是<b>候选池</b>与被阈值滤掉的条数，
 *       因为"重排救不了池外的东西"，池子大小才是召回上限；</li>
 *   <li>{@link Hit} 同时给 store score 与它对应的余弦（{@code cos = 2*score-1}），
 *       避免把被压到 [0,1] 的分数当成余弦去比阈值（这是本仓库踩过的坑）；</li>
 *   <li>{@link #notes} 用自然语言说明本次的口径与降级（自动池、阈值等价余弦、关键词路未启用、
 *       扩窗不参与排序等），让页面不必自己猜。</li>
 * </ul>
 */
@Data
public class KbRecallTestResp {

    @Schema(title = "知识库uuid")
    private String kbUuid;

    @Schema(title = "知识库名称")
    private String kbTitle;

    @Schema(title = "查询文本")
    private String query;

    @Schema(title = "总耗时（毫秒）")
    private long costMs;

    /**
     * 本次实际生效的参数（知识库配置 + 临时覆盖的结果）
     */
    @Data
    public static class EffectiveParams {
        private int topK;
        @Schema(title = "topK 是否来自「自动」（知识库未显式配置）")
        private boolean topKAuto;
        private double minScore;
        @Schema(title = "minScore 等价的余弦阈值 = 2*minScore-1")
        private double minScoreAsCosine;
        private boolean hybridEnabled;
        private int keywordLimit;
        private int rrfK;
        private boolean rerankEnabled;
        private Long rerankModelId;
        private String rerankModelName;
        private int rerankTopN;
        private double rerankMinScore;
        private boolean neighborEnabled;
        private int neighborWindow;
        @Schema(title = "本次被临时覆盖的字段名")
        private List<String> overridden = new ArrayList<>();
    }

    /**
     * 召回漏斗：从候选池到最终命中，每一格的条数与各阶段耗时
     */
    @Data
    public static class Stage {
        @Schema(title = "向向量库请求的候选数（池大小）")
        private int poolRequested;
        @Schema(title = "向量库实际返回的候选数")
        private int poolReturned;
        @Schema(title = "其中能映射到知识库片段（关系层）的条数")
        private int poolResolved;
        @Schema(title = "池内达到 minScore 的条数")
        private int poolAboveMinScore;
        @Schema(title = "被 minScore 滤掉的条数")
        private int filteredByMinScore;
        @Schema(title = "关键词路命中条数")
        private int keywordReturned;
        @Schema(title = "融合后条数（未开混合检索时=向量路条数）")
        private int fusedReturned;
        @Schema(title = "重排是否真正执行")
        private boolean rerankExecuted;
        @Schema(title = "重排输入条数")
        private int rerankInput;
        @Schema(title = "重排后保留条数")
        private int rerankReturned;
        @Schema(title = "最终命中条数")
        private int finalReturned;
        @Schema(title = "查询向量耗时（毫秒，命中缓存时接近 0）")
        private long embeddingMs;
        @Schema(title = "向量检索耗时（毫秒）")
        private long vectorMs;
        @Schema(title = "关键词检索耗时（毫秒）")
        private long keywordMs;
        @Schema(title = "重排耗时（毫秒）")
        private long rerankMs;
    }

    /**
     * 一条命中
     */
    @Data
    public static class Hit {
        @Schema(title = "最终排名，从 1 开始")
        private int rank;
        @Schema(title = "片段主键（adi_document_segment.id）")
        private Long segmentId;
        private String docUuid;
        private String docTitle;
        @Schema(title = "段序（0 基，页面显示 +1）")
        private Integer position;
        private String content;
        @Schema(title = "内容是否被截断")
        private boolean truncated;
        @Schema(title = "命中来源：vector / keyword / vector+keyword")
        private String source;
        @Schema(title = "命中形态：正文 / 问答 / 子块上卷")
        private String kind;
        @Schema(title = "向量 store score（(1+cos)/2）")
        private Double storeScore;
        @Schema(title = "等价余弦 = 2*storeScore-1")
        private Double cosine;
        @Schema(title = "在向量池中的名次")
        private Integer poolRank;
        @Schema(title = "在关键词路中的名次")
        private Integer keywordRank;
        @Schema(title = "RRF 融合分（开启混合检索时）")
        private Double rrfScore;
        @Schema(title = "重排分（sigmoid 归一化后，真正执行重排时才有）")
        private Double rerankScore;
    }

    private EffectiveParams params;
    private Stage stage;
    private List<Hit> hits = new ArrayList<>();
    @Schema(title = "口径与提示")
    private List<String> notes = new ArrayList<>();
}
