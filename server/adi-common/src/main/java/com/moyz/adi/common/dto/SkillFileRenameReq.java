package com.moyz.adi.common.dto;

import lombok.Data;

/**
 * 管理端重命名 / 移动技能包内文件。
 * <p>
 * Rename or move a file inside a skill package from the admin console.
 * </p>
 */
@Data
public class SkillFileRenameReq {

    /**
     * 原相对路径，如 scripts/count.py
     */
    private String path;

    /**
     * 新的相对路径，如 references/count.py（同时含改目录与改名两种语义）
     */
    private String newPath;
}
