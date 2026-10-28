package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("mall_coupon")
@Schema(title = "Mall coupon template | 商城优惠券（模板）")
public class MallCoupon extends BaseEntity {

    @Schema(title = "Coupon code (unique, tool-level key)")
    @TableField("code")
    private String code;

    @Schema(title = "Coupon title, e.g. 满100减20")
    @TableField("title")
    private String title;

    @Schema(title = "Discount amount")
    @TableField("amount")
    private java.math.BigDecimal amount;

    @Schema(title = "Min order amount (threshold)")
    @TableField("min_spend")
    private java.math.BigDecimal minSpend;

    @Schema(title = "Total stock, -1 = unlimited")
    @TableField("total_count")
    private Integer totalCount;

    @Schema(title = "Claimed count")
    @TableField("claimed_count")
    private Integer claimedCount;

    @Schema(title = "Expire time")
    @TableField("expire_time")
    private LocalDateTime expireTime;

    @Schema(title = "Is enabled")
    @TableField("is_enable")
    private Boolean isEnable;
}
