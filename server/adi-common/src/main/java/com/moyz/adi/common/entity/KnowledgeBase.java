package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
// 二开（T1-03，2026-10-16）：default_visibility_groups 是 jsonb，必须 autoResultMap +
// 字段级 typeHandler + updateById 写入（铁律 4），否则「更新了但查出来是 null」
@TableName(value = "adi_knowledge_base", autoResultMap = true)
@Schema(title = "知识库实体 | Knowledge Base Entity", description = "知识库表 | Knowledge Base Table")
public class KnowledgeBase extends BaseEntity {

    @Schema(title = "uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "名称 | Name")
    @TableField("title")
    private String title;

    @Schema(title = "描述 | Description")
    @TableField("remark")
    private String remark;

    @Schema(title = "是否公开 | Is Public")
    @TableField("is_public")
    private Boolean isPublic;

    @Schema(title = "是否严格模式 | Is Strict Mode")
    @TableField("is_strict")
    private Boolean isStrict;

    @Schema(title = "点赞数 | Star Count")
    @TableField("star_count")
    private Integer starCount;

    @Schema(title = "知识点数量 | Knowledge Item Count")
    @TableField("item_count")
    private Integer itemCount;

    @Schema(title = "向量数 | Embedding Count")
    @TableField("embedding_count")
    private Integer embeddingCount;

    @Schema(title = "所属人uuid | Owner UUID")
    @TableField("owner_uuid")
    private String ownerUuid;

    @Schema(title = "所属人id | Owner ID")
    @TableField("owner_id")
    private Long ownerId;

    @Schema(title = "所属人名称 | Owner Name")
    @TableField("owner_name")
    private String ownerName;

    @Schema(title = "文档切割时重叠数量(按token来计) | Document Chunking Overlap Count (by Token)")
    @TableField("ingest_max_overlap")
    private Integer ingestMaxOverlap;

    @Schema(title = "分段策略: recursive/paragraph/line/sentence/custom | Split strategy")
    @TableField("ingest_split_strategy")
    private String ingestSplitStrategy;

    @Schema(title = "每段最大token数 | Max segment size in tokens")
    @TableField("ingest_max_segment_size")
    private Integer ingestMaxSegmentSize;

    @Schema(title = "自定义分隔符,仅custom策略生效 | Custom separator, only used when strategy is custom")
    @TableField("ingest_custom_separator")
    private String ingestCustomSeparator;

    @Schema(title = "索引(图谱化)文档时使用的LLM,如不指定的话则使用第1个可用的LLM | LLM Used for Indexing (Graph) - Defaults to First Available")
    @TableField("ingest_model_name")
    private String ingestModelName;

    @Schema(title = "索引(图谱化)文档时使用的LLM,如不指定的话则使用第1个可用的LLM | LLM Used for Indexing (Graph) - Defaults to First Available")
    @TableField("ingest_model_id")
    private Long ingestModelId;

    @Schema(title = "token数量估计器,默认使用OpenAiTokenizer | Token Estimator (Default: OpenAiTokenizer)")
    @TableField("ingest_token_estimator")
    private String ingestTokenEstimator;

    @Schema(title = "文档召回最大数量 | Max Document Recall Count")
    @TableField("retrieve_max_results")
    private Integer retrieveMaxResults;

    @Schema(title = "文档召回最小分数 | Min Document Recall Score")
    @TableField("retrieve_min_score")
    private Double retrieveMinScore;

    /**
     * 二开新增（2026-09-21）：是否参与对话自动检索。false = 对话链路跳过该库，
     * 文档仍可在管理界面查看维护；ext API 显式指定 kbUuid 的调用不受影响。
     */
    @Schema(title = "是否参与对话检索 | Whether chat retrieval includes this KB")
    @TableField("is_retrieval_enabled")
    private Boolean isRetrievalEnabled;

    @Schema(title = "请求LLM时的temperature | LLM Temperature")
    @TableField("query_llm_temperature")
    private Double queryLlmTemperature;

    @Schema(title = "请求LLM时的系统提示词 | System Prompt for LLM")
    @TableField("query_system_message")
    private String querySystemMessage;

    @Schema(title = "外部系统对接密钥 | API key for external system integration")
    @TableField("api_key")
    private String apiKey;

    // ===== 二开新增（2026-09-15）：混合检索与重排配置 =====

    @Schema(title = "是否启用混合检索（向量+关键词RRF融合） | Enable hybrid retrieval")
    @TableField("retrieve_hybrid_enabled")
    private Boolean retrieveHybridEnabled;

    @Schema(title = "RRF融合常数k | RRF fusion constant k")
    @TableField("retrieve_rrf_k")
    private Integer retrieveRrfK;

    @Schema(title = "关键词路召回条数上限 | Keyword path result limit")
    @TableField("retrieve_keyword_limit")
    private Integer retrieveKeywordLimit;

    @Schema(title = "是否启用重排 | Enable rerank")
    @TableField("retrieve_rerank_enabled")
    private Boolean retrieveRerankEnabled;

    @Schema(title = "绑定的重排模型id，0表示取第一个可用 | Bound rerank model id, 0 means first available")
    @TableField("retrieve_rerank_model_id")
    private Long retrieveRerankModelId;

    @Schema(title = "重排后保留条数，0表示不截断 | Keep top-N after rerank, 0 means no truncation")
    @TableField("retrieve_rerank_top_n")
    private Integer retrieveRerankTopN;

    @Schema(title = "重排分数下限，低于该值丢弃 | Drop reranked contents below this score")
    @TableField("retrieve_rerank_min_score")
    private Double retrieveRerankMinScore;

    // ===== 二开新增（2026-09-22）：命中片段左右扩窗 =====

    @Schema(title = "是否把命中片段的相邻片段一并送入上下文 | Expand each hit with its neighbouring segments")
    @TableField("retrieve_neighbor_enabled")
    private Boolean retrieveNeighborEnabled;

    @Schema(title = "左右各取多少个相邻片段，0表示不扩窗 | Segments taken on each side of a hit, 0 disables")
    @TableField("retrieve_neighbor_window")
    private Integer retrieveNeighborWindow;

    // ===== 二开新增（2026-10-09）：知识库分类 =====

    @Schema(title = "分类id，0=未分类 | Category id, 0 = uncategorized")
    @TableField("category_id")
    private Long categoryId;

    // ===== 二开新增（T1-03，2026-10-16）：库级 ACL 默认值（chunk 固化 + 检索过滤共用）=====

    @Schema(title = "默认可见组（jsonb数组，恒含合成组 owner:<uuid>）| Default visibility groups")
    @TableField(value = "default_visibility_groups", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode defaultVisibilityGroups;

    @Schema(title = "默认密级 PUBLIC/ORG/PRIVATE | Default sensitivity")
    @TableField("default_sensitivity")
    private String defaultSensitivity;
}
