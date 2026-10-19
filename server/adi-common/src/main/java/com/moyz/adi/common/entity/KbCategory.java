package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 知识库分类字典（二开新增 2026-10-09）。
 * admin 维护，KB 通过 category_id 引用；0 = 未分类（虚拟组，不落库）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_kb_category")
@Schema(title = "知识库分类 | Knowledge Base Category")
public class KbCategory extends BaseEntity {

    @Schema(title = "分类名 | Category name")
    @TableField("name")
    private String name;

    @Schema(title = "展示顺序，越小越靠前 | Display order, smaller first")
    @TableField("sort")
    private Integer sort;

    @Schema(title = "备注 | Remark")
    @TableField("remark")
    private String remark;
}
