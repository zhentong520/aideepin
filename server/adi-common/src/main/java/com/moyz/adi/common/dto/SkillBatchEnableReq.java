package com.moyz.adi.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 管理端批量启用 / 停用技能。
 * <p>
 * Batch enable or disable skills from the admin console.
 * </p>
 */
@Data
public class SkillBatchEnableReq {

    /**
     * 技能主键列表（列表接口返回的是 id，不是 uuid）
     */
    private List<Long> ids;

    /**
     * true 启用 / false 停用
     */
    private Boolean isEnable;
}
