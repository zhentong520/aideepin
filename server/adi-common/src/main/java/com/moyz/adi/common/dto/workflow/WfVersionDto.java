package com.moyz.adi.common.dto.workflow;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * T9-05：版本快照 DTO（列表项）。nodeDiff/edgeDiff = 与上一版本的节点/边数变化（diff 概要）。
 */
@Data
public class WfVersionDto {
    private Integer versionNo;
    private String title;
    private String remark;
    private Long modifierUserId;
    private String modifierUserName;
    private Integer nodeCount;
    private Integer edgeCount;
    /** 与上一版本的节点数变化（+/-） */
    private Integer nodeDiff;
    /** 与上一版本的边数变化（+/-） */
    private Integer edgeDiff;
    /** true = 回滚产生的版本 */
    private Boolean rollback;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
