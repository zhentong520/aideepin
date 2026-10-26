package com.moyz.adi.common.constant;

import java.util.List;

/**
 * 知识库/RAG 域常量（OPT-17 拆分：原 AdiConstant 顶层常量与检索相关嵌套类）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有引用无需改动。
 * 已删除零引用成员：isSupportedDocType（仅定义处引用）、RAG_TYPE_KB/RAG_TYPE_SEARCH、
 * GRAPH_METADATA_IDENTIFY_COLUMNS/APPEND_COLUMNS。
 * 注意：RAG_RETRIEVE_NUMBER_DEFAULT 经 EmbeddingRag 的静态星号导入使用，不可删。
 */
public interface KbRagConstant {

    /**
     * 非知识库链路（角色记忆/情景记忆）调用方未指定召回条数时的兜底值。
     * <p>
     * Knowledge-base rows resolve their own size (explicit setting, else the token-budget rule in
     * {@code EmbeddingRag#getRetrieveMaxResults}); this constant only covers callers that pass none.
     */
    int RAG_RETRIEVE_NUMBER_DEFAULT = 3;

    /**
     * 每块文档长度（按token算）
     */
    int RAG_MAX_SEGMENT_SIZE_IN_TOKENS = 1000;

    final class SplitStrategy {
        public static final String RECURSIVE = "recursive";
        public static final String PARAGRAPH = "paragraph";
        public static final String LINE = "line";
        public static final String SENTENCE = "sentence";
        public static final String CUSTOM = "custom";
        /**
         * Sentence-aware splitting for Chinese text: breaks at CJK sentence terminators (。！？；) and at
         * line breaks, then packs whole sentences into segments. See {@code CjkSentenceSplitter}.
         */
        public static final String SENTENCE_CN = "sentence_cn";
    }

    /**
     * Parent-child mode child chunk size bounds in tokens
     */
    final class ChildChunkSize {
        public static final int MIN = 50;
        public static final int MAX = 4000;
        public static final int DEFAULT = 200;
    }

    /**
     * 分段来源（adi_document_segment.source）。
     * doc: 文档入库切分产生；manual: 页面手动创建；annotation: 预留给后续的聊天标注功能
     * （标注 = source=annotation 的 QA 段，复用问答模式结构与检索展开逻辑）。
     */
    final class SegmentSource {
        public static final String DOC = "doc";
        public static final String MANUAL = "manual";
        public static final String ANNOTATION = "annotation";
    }

    /**
     * 文档召回最大数量：token 预算充足时知识库的召回条数，也是自适应路径的上限。
     * <p>
     * 5 → 10（2026-09-22，实测）。召回条数是"排序侧"一切手段的硬天花板：重排、改写、扩窗都只能
     * 在**已经被召回**的候选里做文章。73 条中文金标实测（sentence_cn/512，doubao-embedding-2048，
     * 见 kb-eval 的 RANKDIAG）：
     * <pre>
     *   池=3   答案在池内 86.3%   ← 13.7% 的问题无论怎么重排都不可能答对
     *   池=5   答案在池内 95.9%   ← 原上限，仍有 3 条（4.1%）答案在池外
     *   池=10  答案在池内 100.0%  ← 本次取值
     * </pre>
     * 代价是送进 prompt 的召回文本从 2658 字符增到 5376 字符（约 3.7k token），不增加任何模型调用；
     * 上限受 token 预算保护——模型窗口不够时 {@code getRetrieveMaxResults} 会自行降到预算允许的条数。
     */
    int RAG_RETRIEVE_NUMBER_MAX = 10;

    /**
     * 向量搜索时命中所需的最低分数
     */
    double RAG_MIN_SCORE = 0.6;

    Double RAG_RETRIEVE_MIN_SCORE_DEFAULT = 0.6D;

    String[] GRAPH_ENTITY_EXTRACTION_ENTITY_TYPES = {"ORGANIZATION", "PERSON", "GEO", "EVENT"};
    String GRAPH_TUPLE_DELIMITER = "<|>";
    String GRAPH_RECORD_DELIMITER = "##";
    String GRAPH_COMPLETION_DELIMITER = "<|COMPLETE|>";

    List<String> GRAPH_STORE_MAIN_FIELDS = List.of("name", "label", "text_segment_id", "description");

    String DOC_INDEX_TYPE_EMBEDDING = "embedding";
    String DOC_INDEX_TYPE_GRAPHICAL = "graphical";

    /**
     * 文档解析出 0 字符（扫描件 / 图片型 PDF 没有文本层）时落给用户的可读原因。
     * 以前这类文档会一路"索引成功"，但库里没有任何内容可召回，用户只知道"搜不到"、
     * 排查成本极高；现在直接在文档状态上写明原因和处理办法。
     */
    String EMPTY_PARSE_FAIL_REASON =
            "文档解析内容为空，可能是扫描件或图片型 PDF（没有文本层）；请先做 OCR 处理，或上传带文本层的副本";

    /**
     * 已启用 OCR 但识别结果仍为空时落给用户的可读原因：排查方向从"文档没有文本层"
     * 转为"OCR 服务本身"，避免用户重复上传同一批扫描件。
     */
    String OCR_EMPTY_FAIL_REASON =
            "已启用 OCR，但仍未识别出任何文字；请检查 OCR 服务地址与语言参数是否正常、图片是否过淡或倾斜，处理后可对该文档点\"重试 OCR\"";

    String[] POI_DOC_TYPES = {"doc", "docx", "ppt", "pptx", "xls", "xlsx"};

    /**
     * 纯文本类型：按 UTF-8 全文读取（langchain4j {@code TextDocumentParser}）。
     * <p>
     * md / markdown 与 txt 同为纯文本，此前分支只判 {@code txt}，导致 .md 落不进任何分支：
     * 上传接口照常返回 file uuid，但 adi_document 一行都没有、也没有任何报错（静默丢弃）。
     * 现在与 txt 共用同一个解析器，Markdown 标记原样入库（标题层级对分句切分有利）。
     */
    String[] TEXT_DOC_TYPES = {"txt", "md", "markdown"};

    /**
     * HTML 类型：用 Jsoup 抽取正文（去标签/脚本/样式）后走通用文本切块。
     * 知识库「更强文档解析」扩展（对标 RAGFlow 的网页维度）。
     */
    String[] HTML_DOC_TYPES = {"html", "htm"};

    /**
     * CSV 类型：按纯文本读取（每行作为候选分段），对标结构化表格接入。
     */
    String[] CSV_DOC_TYPES = {"csv"};

    /**
     * 文档来源（adi_document.source_type）。
     * <p>
     * file: 用户上传的文件（含 OCR 兜底）；url: 网页正文抓取；api: 接口定时/手动拉取。
     */
    final class DocSourceType {
        private DocSourceType() {
        }

        public static final String FILE = "file";
        public static final String URL = "url";
        public static final String API = "api";
    }

    /**
     * 数据源（URL / API）抓取的硬上限，防止一个配错的地址拖垮服务或撑爆向量化。
     */
    final class DocSourceLimit {
        private DocSourceLimit() {
        }

        /** 单次响应体上限（字节）：超出直接判失败，不落库 */
        public static final int MAX_BYTES = 5 * 1024 * 1024;
        /** 连接/读取超时（秒） */
        public static final int TIMEOUT_SECONDS = 20;
        /** 正文入库前截断长度（字符），避免超大页面拖垮切分与向量化 */
        public static final int MAX_TEXT_CHARS = 200_000;
        /** 同步周期上限（分钟）：比这更密的周期不接受，防止把目标站打挂 */
        public static final int MAX_INTERVAL_MINUTES = 60 * 24 * 30;
        /** 同步周期下限（分钟） */
        public static final int MIN_INTERVAL_MINUTES = 10;
    }
}
