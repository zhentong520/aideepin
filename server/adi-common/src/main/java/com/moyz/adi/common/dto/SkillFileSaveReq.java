package com.moyz.adi.common.dto;

import lombok.Data;

/**
 * 管理端保存技能包内文件（在线编辑 / 新建）。
 * <p>
 * Save one file inside a skill package from the admin console (edit or create).
 * </p>
 */
@Data
public class SkillFileSaveReq {

    /**
     * 相对技能包根目录的路径，如 SKILL.md、scripts/count.py
     */
    private String path;

    /**
     * 文件全文；新建时可为空字符串
     */
    private String content;
}
