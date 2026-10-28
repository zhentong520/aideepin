package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("mall_category")
@Schema(title = "Mall product category | 商城商品分类")
public class MallCategory extends BaseEntity {

    @Schema(title = "Category code (unique)")
    @TableField("code")
    private String code;

    @Schema(title = "Category name")
    @TableField("name")
    private String name;

    @Schema(title = "Icon (emoji or url)")
    @TableField("icon")
    private String icon;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
