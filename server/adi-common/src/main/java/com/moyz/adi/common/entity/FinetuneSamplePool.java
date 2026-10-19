package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ObjectNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 会话收集的训练样本候选池：从角色/Agent 会话收集有用对话，
 * LLM 改写为 alpaca 样本，人工审核确认后导出为微调数据集。
 * <p>
 * 铁律：候选池永远只是「候选」——未确认（confirmed）的样本绝不进入训练数据集，
 * 被驳回（rejected）的样本永不复用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_finetune_sample_pool", autoResultMap = true)
public class FinetuneSamplePool extends BaseEntity {

    @TableField("uuid")
    private String uuid;

    /**
     * 收集人（也是样本属主）
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 来源类型：character_chat | agent_conv
     */
    @TableField("source_type")
    private String sourceType;

    @TableField("character_uuid")
    private String characterUuid;

    /**
     * 角色名快照（收集时的显示名，便于审核页识别域）
     */
    @TableField("character_name")
    private String characterName;

    /**
     * 逗号分隔的来源消息 uuid（问题 + 答案）
     */
    @TableField("source_message_ids")
    private String sourceMessageIds;

    /**
     * 原始对话：{"turns":[{"role":"user|assistant","content":"..."}]}
     */
    @TableField(value = "raw_dialog", jdbcType = org.apache.ibatis.type.JdbcType.JAVA_OBJECT, typeHandler = ObjectNodeTypeHandler.class)
    private ObjectNode rawDialog;

    /**
     * 改写后的 alpaca 样本：{"instruction":"...","input":"","output":"..."}；改写前为 null
     */
    @TableField(value = "generated_sample", jdbcType = org.apache.ibatis.type.JdbcType.JAVA_OBJECT, typeHandler = ObjectNodeTypeHandler.class)
    private ObjectNode generatedSample;

    /**
     * 状态机：pending_rewrite -> generated -> confirmed/rejected；改写失败为 discarded；导出后 in_dataset
     */
    @TableField("status")
    private String status;

    /**
     * 规则质检备注（去重/长度/复读），空 = 通过
     */
    @TableField("quality_note")
    private String qualityNote;

    @TableField("reject_reason")
    private String rejectReason;

    /**
     * 导出后回填 adi_finetune_dataset.id；0 = 未导出
     */
    @TableField("dataset_id")
    private Long datasetId;

    @TableField("remark")
    private String remark;
}
