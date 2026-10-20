package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;

/**
 * T9-05：工作流定义版本快照。每次保存生成一条（version_no 单调递增，唯一约束），
 * 回滚 = 按历史 definition 重放 + 生成新版本，不覆盖历史行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_workflow_version", autoResultMap = true)
@Schema(title = "工作流版本快照 | workflow version snapshot")
public class WorkflowVersion extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("uuid")
    private String uuid;

    @TableField("workflow_id")
    private Long workflowId;

    /** 同一工作流内单调递增；(workflow_id, version_no) 唯一 */
    @TableField("version_no")
    private Integer versionNo;

    @TableField("title")
    private String title;

    /** 保存备注 / 回滚来源（如 rollback from v3） */
    @TableField("remark")
    private String remark;

    /** 完整定义 JSON（title/remark/nodes/edges，不脱敏：回滚需要真实配置） */
    @TableField(value = "definition", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ObjectNode definition;

    /** 该版本的修改人 */
    @TableField("modifier_user_id")
    private Long modifierUserId;

    /** 快照时节点数（diff 概要用） */
    @TableField("node_count")
    private Integer nodeCount;

    /** 快照时边数（diff 概要用） */
    @TableField("edge_count")
    private Integer edgeCount;
}
