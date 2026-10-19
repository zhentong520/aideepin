package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库细粒度共享（二开新增）。
 * <p>
 * 在原有「owner / public / admin」三态之上，增加按「用户」的显式授权，实现
 * 「前端按授权看知识库」：被共享的用户即使不是 owner、知识库也不是 public，也能看到并使用该库。
 * 粒度先支持 USER；ROLE/TEAM 后续扩展（当前 User 实体仅有 isAdmin 单角色字段）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_knowledge_base_share")
@Schema(title = "知识库共享 | Knowledge Base Share")
public class KnowledgeBaseShare extends BaseEntity {

    @Schema(title = "知识库uuid | KB UUID")
    @TableField("kb_uuid")
    private String kbUuid;

    @Schema(title = "共享目标类型 | Target Type (USER)")
    @TableField("target_type")
    private String targetType;

    @Schema(title = "共享目标id（用户id） | Target ID (user id)")
    @TableField("target_id")
    private Long targetId;

    @Schema(title = "权限 | Permission (READ/WRITE)")
    @TableField("permission")
    private String permission;

    @Schema(title = "授权人id | Grantor ID")
    @TableField("grantor_id")
    private Long grantorId;

    @Schema(title = "授权人名称 | Grantor Name")
    @TableField("grantor_name")
    private String grantorName;
}
