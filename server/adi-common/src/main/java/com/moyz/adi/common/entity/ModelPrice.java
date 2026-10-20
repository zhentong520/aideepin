package com.moyz.adi.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 二开新增（T6-13，2026-10-20）：模型单价。
 * <p>
 * 成本看板费用 = token 数 × 单价；单价按 model_name（可细分 platform）配置，
 * 未配置的模型单价按 0 计（费用为 0 时前端仍展示 token 数，不隐藏）。
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("adi_model_price")
@Schema(title = "模型单价 | Model unit price")
public class ModelPrice extends BaseEntity {

    @Schema(title = "模型名，关联 adi_llm_call_record.model_name")
    @TableField("model_name")
    private String modelName;

    @Schema(title = "模型平台，空表示对所有平台生效")
    @TableField("model_platform")
    private String modelPlatform;

    @Schema(title = "输入 token 单价")
    @TableField("input_price")
    private BigDecimal inputPrice;

    @Schema(title = "输出 token 单价")
    @TableField("output_price")
    private BigDecimal outputPrice;

    @Schema(title = "币种，如 CNY/USD")
    @TableField("currency")
    private String currency;

    @Schema(title = "计价单位：1k_tokens / 1m_tokens")
    @TableField("price_unit")
    private String priceUnit;
}
