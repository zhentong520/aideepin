package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_db_sync_task")
@Schema(title = "Sync external DB query result into knowledge base | 数据库内容同步至知识库任务")
public class DbSyncTask extends BaseEntity {

    @Schema(title = "Task uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Datasource uuid")
    @TableField("datasource_uuid")
    private String datasourceUuid;

    @Schema(title = "Target knowledge base uuid")
    @TableField("kb_uuid")
    private String kbUuid;

    @Schema(title = "Task title")
    @TableField("title")
    private String title;

    @Schema(title = "Read-only SQL executed against the datasource")
    @TableField("sql_text")
    private String sqlText;

    @Schema(title = "Template rendering each row into document content")
    @TableField("content_template")
    private String contentTemplate;

    @Schema(title = "Last sync time")
    @TableField("last_sync_time")
    private LocalDateTime lastSyncTime;

    @Schema(title = "Last sync status: success / fail")
    @TableField("last_status")
    private String lastStatus;

    @Schema(title = "Row count of last sync")
    @TableField("last_rows")
    private Integer lastRows;

    @Schema(title = "Last sync message")
    @TableField("last_message")
    private String lastMessage;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
