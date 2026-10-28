package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("mall_product")
@Schema(title = "Mall product | 商城商品")
public class MallProduct extends BaseEntity {

    @Schema(title = "Product name")
    @TableField("name")
    private String name;

    @Schema(title = "Category code")
    @TableField("category_code")
    private String categoryCode;

    @Schema(title = "Price (current selling price)")
    @TableField("price")
    private BigDecimal price;

    @Schema(title = "Original price (for strikethrough display)")
    @TableField("original_price")
    private BigDecimal originalPrice;

    @Schema(title = "Monthly sales")
    @TableField("monthly_sales")
    private Long monthlySales;

    @Schema(title = "Promotion tags, comma separated")
    @TableField("promo_tags")
    private String promoTags;

    @Schema(title = "Coupon hint, e.g. 满100减20")
    @TableField("coupon_hint")
    private String couponHint;

    @Schema(title = "Short description")
    @TableField("remark")
    private String remark;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
