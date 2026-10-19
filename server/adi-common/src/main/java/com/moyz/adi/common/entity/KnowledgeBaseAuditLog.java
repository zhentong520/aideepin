package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库操作审计日志（二开新增）。
 * <p>
 * 覆盖「前端按授权看知识库」所需的操作留痕：文档上传/删除/重索引/重OCR/启停、知识库创建/编辑/删除等。
 * 与 MaxKB / AnythingLLM 的细粒度审计思路对齐——任何会改变知识库内容的动作都落一条记录，
 * 包含操作人、对象、成败与详情，便于回溯与合规。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_knowledge_base_audit_log")
@Schema(title = "知识库操作审计日志 | Knowledge Base Audit Log")
public class KnowledgeBaseAuditLog extends BaseEntity {

    @Schema(title = "知识库uuid | KB UUID")
    @TableField("kb_uuid")
    private String kbUuid;

    @Schema(title = "知识库名称快照 | KB Name Snapshot")
    @TableField("kb_name")
    private String kbName;

    @Schema(title = "操作类型 | Action (UPLOAD_DOC/DELETE_DOC/REINDEX_DOC/RETRY_OCR/TOGGLE_DOC/CREATE_KB/EDIT_KB/DELETE_KB/SHARE_KB/UNSHARE_KB)")
    @TableField("action")
    private String action;

    @Schema(title = "操作对象类型 | Target Type (document/knowledge_base/share)")
    @TableField("target_type")
    private String targetType;

    @Schema(title = "操作对象uuid | Target UUID")
    @TableField("target_uuid")
    private String targetUuid;

    @Schema(title = "操作人id | Operator ID")
    @TableField("operator_id")
    private Long operatorId;

    @Schema(title = "操作人名称 | Operator Name")
    @TableField("operator_name")
    private String operatorName;

    @Schema(title = "是否成功 | Success")
    @TableField("success")
    private Boolean success;

    @Schema(title = "详情/错误信息 | Detail / Error Message")
    @TableField("detail")
    private String detail;
}
