package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("mall_activity")
@Schema(title = "Mall promotion activity | 商城优惠活动")
public class MallActivity extends BaseEntity {

    @Schema(title = "Activity title")
    @TableField("title")
    private String title;

    @Schema(title = "Activity description")
    @TableField("description")
    private String description;

    @Schema(title = "Activity banner/emoji")
    @TableField("icon")
    private String icon;

    @Schema(title = "Display order")
    @TableField("sort")
    private Integer sort;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
