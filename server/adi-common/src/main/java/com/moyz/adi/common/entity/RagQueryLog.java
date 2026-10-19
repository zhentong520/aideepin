package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 二开新增（T1-01，2026-10-16）：RAG 检索日志。
 * <p>
 * 知识库向量检索一行一条（异步写入，见 {@code RagQueryLogService#saveAsync}），
 * 是 ACL（T1-05）的观测基础：过滤失效时可按 user/kb/time 定位。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_rag_query_log", autoResultMap = true)
@Schema(title = "RAG检索日志 | RAG Query Log", description = "知识库检索日志表 | RAG Query Log Table")
public class RagQueryLog extends BaseEntity {

    @Schema(title = "本次检索请求唯一id | Unique id of this retrieval")
    @TableField("query_id")
    private String queryId;

    @Schema(title = "发起用户uuid | Requesting user uuid")
    @TableField("user_uuid")
    private String userUuid;

    @Schema(title = "知识库uuid数组 | Knowledge base uuids")
    @TableField(value = "kb_uuids", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode kbUuids;

    @Schema(title = "下发到向量库的过滤表达式 | Filter expression sent to the vector store")
    @TableField("filter_expression")
    private String filterExpression;

    @Schema(title = "原始命中明细（ACL过滤后、展开前）| Raw hits after ACL double-check")
    @TableField(value = "hit_segments", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode hitSegments;

    @Schema(title = "保留命中数 | Number of hits kept")
    @TableField("hit_count")
    private Integer hitCount;

    @Schema(title = "双保险剔除命中数 | Hits dropped by ACL double-check")
    @TableField("acl_filtered_count")
    private Integer aclFilteredCount;

    @Schema(title = "检索耗时（毫秒）| Elapsed ms")
    @TableField("elapsed_ms")
    private Integer elapsedMs;

    @Schema(title = "后续LLM的prompt token（暂为0）| Prompt tokens (0 until wired up)")
    @TableField("prompt_tokens")
    private Integer promptTokens;

    @Schema(title = "embedding模型配置 | Embedding model config")
    @TableField("embedding_model")
    private String embeddingModel;

    @Schema(title = "用户反馈：空/PASS/FAIL | Feedback: empty/PASS/FAIL")
    @TableField("feedback")
    private String feedback;
}
