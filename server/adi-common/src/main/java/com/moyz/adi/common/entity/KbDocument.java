package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.moyz.adi.common.enums.EmbeddingStatusEnum;
import com.moyz.adi.common.enums.GraphicalStatusEnum;
import com.moyz.adi.common.enums.SegmentModeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("adi_document")
@Schema(title = "知识库文档实体 | Knowledge Base Document Entity", description = "知识库文档表 | Knowledge Base Document Table")
public class KbDocument extends BaseEntity {

    @Schema(title = "知识库id | Knowledge Base ID")
    @TableField("kb_id")
    private Long kbId;

    @Schema(title = "知识库uuid | Knowledge Base UUID")
    @TableField("kb_uuid")
    private String kbUuid;

    @Schema(title = "名称 | Name")
    @TableField("source_file_id")
    private Long sourceFileId;

    @Schema(title = "uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "标题 | Title")
    @TableField("title")
    private String title;

    @Schema(title = "内容摘要 | Content Summary")
    @TableField("brief")
    private String brief;

    @Schema(title = "内容 | Content")
    @TableField("remark")
    private String remark;

    /**
     * 解析后的原始正文（清洗前）。用于分块预览里对照「解析 → 清洗 → 切分」三步；
     * 老文档没有这一列，预览时回退到 remark。
     * <p>
     * Raw parsed text before cleanup, kept so the chunk preview can show the parse → clean →
     * split chain side by side. Documents ingested before this column exist fall back to remark.
     */
    @Schema(title = "解析原始文本(清洗前) | Raw parsed text before cleanup")
    @TableField("raw_text")
    private String rawText;

    @Schema(title = "分段模式: text/qa/parent_child | Segment Mode")
    @TableField("segment_mode")
    private SegmentModeEnum segmentMode;

    @Schema(title = "父子模式下子块最大token数(文档级) | Parent-child mode: max child chunk size in tokens")
    @TableField("child_max_chunk_size")
    private Integer childMaxChunkSize;

    @Schema(title = "最近一次失败的异步任务原因(向量化/QA生成) | Fail reason of the latest failed async pipeline")
    @TableField("fail_reason")
    private String failReason;

    @Schema(title = "向量化状态 | Embedding Status")
    @TableField("embedding_status")
    private EmbeddingStatusEnum embeddingStatus;

    @Schema(title = "向量化状态变更时间点 | Embedding Status Change Time")
    @TableField("embedding_status_change_time")
    private LocalDateTime embeddingStatusChangeTime;

    @Schema(title = "图谱化状态 | Graphical Status")
    @TableField("graphical_status")
    private GraphicalStatusEnum graphicalStatus;

    @Schema(title = "图谱化状态变更时间点 | Graphical Status Change Time")
    @TableField("graphical_status_change_time")
    private LocalDateTime graphicalStatusChangeTime;

    @Schema(title = "向量命中次数 | Embedding Hit Count")
    @TableField("embedding_hit_count")
    private Integer embeddingHitCount;

    @Schema(title = "图谱命中次数 | Graph Hit Count")
    @TableField("graph_hit_count")
    private Integer graphHitCount;

    @Schema(title = "字符数 | Word Count (auto-computed by PostgreSQL)")
    @TableField(value = "word_count", insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer wordCount;

    @Schema(title = "是否启用 | Is Enabled")
    @TableField("is_enabled")
    private Boolean isEnabled;

    @Schema(title = "启用/停用变更时间 | Enabled Status Change Time")
    @TableField("enabled_change_time")
    private LocalDateTime enabledChangeTime;

    @Schema(title = "索引版本(索引任务快照比对用) | Index Version")
    @TableField("index_version")
    private Integer indexVersion;

    @Schema(title = "文档来源: file/url/api | Document source type: file | url | api")
    @TableField("source_type")
    private String sourceType = com.moyz.adi.common.constant.AdiConstant.DocSourceType.FILE;

    @Schema(title = "抓取地址(url/api来源) | Fetch address for url/api sources")
    @TableField("source_url")
    private String sourceUrl;

    @Schema(title = "API请求方法: GET/POST | HTTP method for api sources")
    @TableField("source_method")
    private String sourceMethod;

    @Schema(title = "自定义请求头(JSON文本) | Custom request headers, stored as JSON text")
    @TableField("source_headers")
    private String sourceHeaders;

    @Schema(title = "请求体(POST) | Request body for api sources")
    @TableField("source_body")
    private String sourceBody;

    @Schema(title = "JSON响应取值路径(如 data.list.content) | Dot path to the text inside a JSON response")
    @TableField("content_path")
    private String contentPath;

    @Schema(title = "上次成功抓取时间 | Last successful fetch time")
    @TableField("last_fetch_time")
    private LocalDateTime lastFetchTime;

    @Schema(title = "自动同步周期(分钟), 0=仅手动 | Auto re-fetch period in minutes, 0 = manual only")
    @TableField("fetch_interval_minutes")
    private Integer fetchIntervalMinutes = 0;

    @Schema(title = "下次同步时间 | Next scheduled fetch time")
    @TableField("next_fetch_time")
    private LocalDateTime nextFetchTime;
}
