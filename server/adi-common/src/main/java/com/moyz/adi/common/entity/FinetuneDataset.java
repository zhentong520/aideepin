package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.base.ObjectNodeTypeHandler;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 微调训练数据集：指向 adi_file 中的一份 JSONL 文件（alpaca / sharegpt 格式），
 * 入库时解析统计样本数与粗略 token 量，供训练任务引用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "adi_finetune_dataset", autoResultMap = true)
public class FinetuneDataset extends BaseEntity {

    @TableField("uuid")
    private String uuid;

    @TableField("user_id")
    private Long userId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    /**
     * alpaca-jsonl / sharegpt-jsonl
     */
    @TableField("format")
    private String format;

    /**
     * adi_file.uuid，指向原始 JSONL 文件
     */
    @TableField("file_uuid")
    private String fileUuid;

    @TableField("sample_count")
    private Integer sampleCount;

    /**
     * 训练任务创建时按 val_ratio 预留的验证集样本数（展示用，实际切分在 worker 端）
     */
    @TableField("eval_count")
    private Integer evalCount;

    /**
     * 粗略统计：{"total_chars":123,"est_tokens":456}
     */
    @TableField(value = "token_stat", jdbcType = org.apache.ibatis.type.JdbcType.JAVA_OBJECT, typeHandler = ObjectNodeTypeHandler.class)
    private ObjectNode tokenStat;
}
