package com.moyz.adi.common.dto;

import com.moyz.adi.common.enums.EmbeddingStatusEnum;
import com.moyz.adi.common.enums.GraphicalStatusEnum;
import com.moyz.adi.common.enums.SegmentModeEnum;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbDocumentDto {

    private Long kbId;

    private String kbUuid;

    private Long sourceFileId;

    private Long id;

    private String uuid;

    private String title;

    private String brief;

    private String remark;

    private SegmentModeEnum segmentMode;

    private EmbeddingStatusEnum embeddingStatus;

    private LocalDateTime embeddingStatusChangeTime;

    private GraphicalStatusEnum graphicalStatus;

    private LocalDateTime graphicalStatusChangeTime;

    private Integer embeddingHitCount;

    private Integer graphHitCount;

    private Integer wordCount;

    private Boolean isEnabled;

    private LocalDateTime enabledChangeTime;

    private String sourceFileName;

    private String sourceFileUuid;

    private String sourceFileUrl;

    /** 文档来源：file | url | api | Document source type */
    private String sourceType;

    /** url/api 来源的抓取地址 | Fetch address for url/api sources */
    private String sourceUrl;

    /** 上次成功抓取时间 | Last successful fetch time */
    private LocalDateTime lastFetchTime;

    /** 自动同步周期（分钟），0=仅手动 | Auto re-fetch period in minutes */
    private Integer fetchIntervalMinutes;

    /** 下次自动同步时间 | Next scheduled fetch time */
    private LocalDateTime nextFetchTime;

    /** 最近一次失败原因 | Latest failure reason */
    private String failReason;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
