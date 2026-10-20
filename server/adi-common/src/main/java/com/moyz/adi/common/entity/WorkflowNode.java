package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import com.moyz.adi.common.base.NodeInputConfigTypeHandler;
import com.moyz.adi.common.workflow.WfNodeInputConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.ibatis.type.JdbcType;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_workflow_node", autoResultMap = true)
@Schema(title = "工作流定义-节点 | workflow definition node")
public class WorkflowNode extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableField("uuid")
    private String uuid;

    @TableField("workflow_id")
    private Long workflowId;

    @TableField("workflow_component_id")
    private Long workflowComponentId;

    @TableField("title")
    private String title;

    @TableField("remark")
    private String remark;

    @TableField(value = "input_config", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = NodeInputConfigTypeHandler.class)
    private WfNodeInputConfig inputConfig;

    @TableField(value = "node_config", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ObjectNode nodeConfig;

    /**
     * 二开新增（T2-07）：引用 adi_credential.uuid；空 = 仍走 node_config 旧明文机制（可回滚）。
     */
    @TableField("credential_uuid")
    private String credentialUuid;

    @TableField("position_x")
    private Double positionX;

    @TableField("position_y")
    private Double positionY;

    /**
     * 二开新增（T9-08）：画布布局元数据（纯布局，不参与执行语义）。
     * <p>
     * Container 分组节点的引擎侧最小实现：分组归属（container uuid）、折叠标记等
     * 画布组织信息以 JSON 存本字段，引擎构图/执行完全忽略它。
     * <p>
     * T9-08: canvas layout metadata (pure layout, no execution semantics).
     * Container grouping info lives here as opaque JSON; the engine ignores it entirely.
     */
    @TableField(value = "layout", jdbcType = JdbcType.JAVA_OBJECT, typeHandler = JsonNodeTypeHandler.class)
    private ObjectNode layout;
}
