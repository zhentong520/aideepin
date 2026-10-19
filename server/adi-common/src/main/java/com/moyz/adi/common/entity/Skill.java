package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Agent 技能（SKILL.md 标准）：frontmatter 的 name/description 存独立列供检索与
 * 提示词清单注入；指令正文存 content；附属文件（scripts/references 等）解压在
 * resources_path 指向的本地目录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("adi_skill")
public class Skill extends BaseEntity {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_ZIP = "zip";

    @TableField("uuid")
    private String uuid;

    @TableField("user_id")
    private Long userId;

    /**
     * SKILL.md frontmatter 的 name，read_skill 工具的调用键
     */
    @TableField("name")
    private String name;

    @TableField("title")
    private String title;

    /**
     * SKILL.md frontmatter 的 description，注入对话的技能清单
     */
    @TableField("description")
    private String description;

    @TableField("version")
    private String version;

    /**
     * SKILL.md 指令正文（markdown，不含 frontmatter）
     */
    @TableField("content")
    private String content;

    /**
     * 附属文件目录，相对 local.base-data-path，如 skills/{uuid}
     */
    @TableField("resources_path")
    private String resourcesPath;

    @TableField("has_scripts")
    private Boolean hasScripts;

    /**
     * manual / zip
     */
    @TableField("source")
    private String source;

    @TableField("is_enable")
    private Boolean isEnable;

    @TableField("is_public")
    private Boolean isPublic;
}
