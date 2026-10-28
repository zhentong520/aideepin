package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@TableName("mall_user_coupon")
@Schema(title = "User claimed coupon | 用户已领取的优惠券（唯一索引幂等）")
public class MallUserCoupon extends BaseEntity {

    @Schema(title = "Owner user id")
    @TableField("user_id")
    private Long userId;

    @Schema(title = "Coupon code")
    @TableField("coupon_code")
    private String couponCode;

    @Schema(title = "Claim time")
    @TableField("claimed_time")
    private LocalDateTime claimedTime;

    @Schema(title = "Used or not")
    @TableField("is_used")
    private Boolean isUsed;
}
