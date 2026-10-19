package com.moyz.adi.common.dto;

import lombok.Data;

import java.util.List;

/**
 * 管理端批量导出技能包。
 * <p>
 * Batch export skill packages from the admin console: all selected skills are packed
 * into a single zip, each under a directory named after the skill.
 * </p>
 */
@Data
public class SkillBatchExportReq {

    /**
     * 待导出的技能 uuid 列表；顺序即 zip 内目录顺序，重复项会被去重
     */
    private List<String> uuids;
}
