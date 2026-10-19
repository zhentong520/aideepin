package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 微调产物：LoRA adapter（zip，上传到服务器）、merged 合并模型目录（留在 GPU 机）、
 * GGUF 量化文件（留在 GPU 机，登记 external_path）。
 * GGUF 体积大（7B Q4 约 4~5GB），不回传服务器，由 GPU 机侧直接导入 Ollama。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("adi_finetune_artifact")
public class FinetuneArtifact extends BaseEntity {

    public static final String TYPE_ADAPTER = "adapter";
    public static final String TYPE_MERGED = "merged";
    public static final String TYPE_GGUF = "gguf";

    @TableField("uuid")
    private String uuid;

    @TableField("job_id")
    private Long jobId;

    @TableField("job_uuid")
    private String jobUuid;

    @TableField("user_id")
    private Long userId;

    /**
     * adapter / merged / gguf
     */
    @TableField("type")
    private String type;

    /**
     * 产物名，通常为导出的模型 tag（如 qwen2.5-7b-adi-lora）
     */
    @TableField("name")
    private String name;

    /**
     * 服务器内文件（adapter zip）对应的 adi_file.uuid；留 GPU 机的产物为空
     */
    @TableField("file_uuid")
    private String fileUuid;

    /**
     * GPU 机上的路径（merged / gguf）；服务器内产物为空
     */
    @TableField("external_path")
    private String externalPath;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("base_model")
    private String baseModel;

    @TableField("remark")
    private String remark;
}
