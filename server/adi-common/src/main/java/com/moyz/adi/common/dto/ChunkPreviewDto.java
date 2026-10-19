package com.moyz.adi.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 分块预览结果：把「解析 → 清洗 → 切分」三步的中间产物一次摊开。
 * <p>
 * Chunk preview: the intermediate artefacts of parse → clean → split, in one payload.
 * <p>
 * 这是纯只读的「干跑」结果：不写任何分段行、不写向量、不触发索引任务，页面可以随意改参数试切，
 * 满意了再回到知识库配置里改正式参数并重建索引。
 * <p>
 * Strictly a dry run: no segment rows, no vectors, no index task. The page can re-run it with any
 * parameters; committing is a separate, explicit step.
 */
@Data
public class ChunkPreviewDto {

    private String docUuid;

    /**
     * 文档分段模式：text / qa / parent_child
     */
    private String segmentMode;

    /**
     * 本次预览实际生效的参数（未传的取知识库配置，非法值已被就地修正）
     */
    private Params params;

    /**
     * 清洗环节的量化结果
     */
    private CleanStats clean;

    /**
     * 切分结果统计
     */
    private Stats stats;

    /**
     * 当前页的块（parent_child 模式下每块带 children）
     */
    private List<ChunkItem> chunks;

    /**
     * 全量块数（chunks 只是其中一页）
     */
    private Integer totalChunks;

    /**
     * 父子模式下的子块总数
     */
    private Integer childChunkCount;

    /**
     * 正文超过预览上限被截断（只影响预览，不影响真实入库）
     */
    private boolean truncated;

    /**
     * 降级/提示信息：切分器异常降级、QA 模式不切正文等
     */
    private String warning;

    /**
     * 解析原文开头片段，供页面对照「解析 → 清洗」的差异
     */
    private String rawSample;

    /**
     * 清洗后正文开头片段（切分的真正输入）
     */
    private String cleanedSample;

    @Data
    public static class Params {
        private String strategy;
        private Integer maxSegmentSize;
        private Integer overlap;
        private String separator;
        private String tokenEstimator;
        private boolean cleanEnabled;
        private boolean mergeSoftLineBreaks;
        private boolean dropDuplicateLines;
        /**
         * 父子模式子块尺寸（文档级，回退 200）
         */
        private Integer childMaxChunkSize;
    }

    @Data
    public static class CleanStats {
        private boolean applied;
        private Integer rawChars;
        private Integer cleanedChars;
        private Integer removedChars;
        private Integer rawLines;
        private Integer cleanedLines;
        private Integer mergedLines;
        private Integer droppedLines;
    }

    @Data
    public static class Stats {
        private Integer chunkCount;
        private Integer totalChars;
        private Integer avgChars;
        private Integer minChars;
        private Integer maxChars;
        private Integer avgTokens;
        private Integer maxTokens;
        /**
         * token 数超过段大小上限的块（切分器没兜住的异常块）
         */
        private Integer oversizeCount;
        /**
         * 过短块（&lt; 段大小的 10%）：语义不完整，检索价值低
         */
        private Integer tinyCount;
    }

    @Data
    public static class ChunkItem {
        /**
         * 从 0 开始的块序号，与入库后的 position 一致
         */
        private Integer index;
        private String content;
        private Integer charCount;
        private Integer tokenCount;
        /**
         * token 数超过段大小上限
         */
        private boolean oversize;
        /**
         * 过短块
         */
        private boolean tiny;
        /**
         * 父子模式下该父块下的子块
         */
        private List<ChunkItem> children;
    }
}
