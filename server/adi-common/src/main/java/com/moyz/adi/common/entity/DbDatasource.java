package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_db_datasource")
@Schema(title = "External database datasource (Database MCP) | 外部数据库数据源")
public class DbDatasource extends BaseEntity {

    @Schema(title = "Datasource uuid")
    @TableField("uuid")
    private String uuid;

    @Schema(title = "Owner user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Datasource title")
    @TableField("title")
    private String title;

    @Schema(title = "Database type: postgresql / mysql")
    @TableField("db_type")
    private String dbType;

    @Schema(title = "Host")
    @TableField("host")
    private String host;

    @Schema(title = "Port")
    @TableField("port")
    private Integer port;

    @Schema(title = "Database name")
    @TableField("db_name")
    private String dbName;

    @Schema(title = "Login user")
    @TableField("username")
    private String username;

    @Schema(title = "Login password")
    @TableField("password")
    private String password;

    @Schema(title = "Extra JDBC url parameters")
    @TableField("jdbc_params")
    private String jdbcParams;

    @Schema(title = "Description")
    @TableField("remark")
    private String remark;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
