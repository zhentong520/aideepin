package com.moyz.adi.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 管理端批量删除技能（软删除）。
 * <p>
 * Batch delete (soft delete) skills from the admin console.
 * </p>
 */
@Data
public class SkillBatchIdsReq {

    /**
     * 技能主键列表
     */
    private List<Long> ids;
}
