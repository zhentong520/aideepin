package com.moyz.adi.common.dto.workflow;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowResp {
    private Long id;
    private String uuid;
    private String title;
    private String remark;
    private Boolean isPublic;
    /** 是否启用（MPPageUtil BeanUtils 同名自动拷贝；场景绑定抽屉据此显示停用标记） */
    private Boolean isEnable;
    private Long userId;
    private String userUuid;
    private String userName;
    private List<WfNodeDto> nodes;
    private List<WfEdgeReq> edges;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
