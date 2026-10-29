package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.moyz.adi.common.base.JsonNodeTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 配置化聊天页面（二开 P1，2026-10-29）。
 * <p>
 * admin 端配置产出 {@code page_schema}（jsonb，P1 为区块数组，数组顺序即展示顺序），
 * user-web 按 {@code /chat/page/:uuid} 动态渲染外壳并绑定场景；页面 → 场景 → 智能体，
 * 换场景只改绑定字段。P2 升级拖拽画布时在 schema 内追加布局坐标，向后兼容。
 * </p>
 * <p>
 * jsonb 字段必须 autoResultMap + 字段级 typeHandler + updateById 写入
 * （T1-03 铁律 4），否则「更新了但查出来是 null」。
 * </p>
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName(value = "adi_chat_page", autoResultMap = true)
@Schema(title = "Configurable chat page | 配置化聊天页面")
public class ChatPage extends BaseEntity {

    @Schema(title = "Page uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Page name")
    @TableField("name")
    private String name;

    @Schema(title = "Icon")
    @TableField("icon")
    private String icon;

    @Schema(title = "Page schema: blocks array (welcome/quickPrompts/richText/sceneInfo) | 页面 schema：区块数组，顺序即展示顺序")
    @TableField(value = "page_schema", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode pageSchema;

    @Schema(title = "Bound scene uuid | 绑定的场景 uuid")
    @TableField("scene_uuid")
    private String sceneUuid;

    @Schema(title = "Optional agent override; empty = scene default agent | 可选智能体覆盖，空则用场景默认智能体")
    @TableField("agent_uuid")
    private String agentUuid;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
