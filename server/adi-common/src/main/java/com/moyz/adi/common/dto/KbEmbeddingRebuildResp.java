package com.moyz.adi.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 按知识库重建 embedding 索引的结果（T7-05）。
 * <p>
 * previous* 两个字段是回滚链路的凭据：运维据此知道「切回去」要把
 * {@code adi.embedding-model} 配成什么、再点一次重建；current* 是本次重建实际使用的
 * provider（与知识库上记录的版本一一对应，启动自检据此发现不一致）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KbEmbeddingRebuildResp {

    @Schema(title = "重建前的 provider 名称（空=未知/存量）")
    private String previousProvider;

    @Schema(title = "重建前的 provider 版本（空=未知/存量）")
    private String previousVersion;

    @Schema(title = "本次重建使用的 provider 名称")
    private String currentProvider;

    @Schema(title = "本次重建使用的 provider 版本（已记录到知识库）")
    private String currentVersion;

    @Schema(title = "入队重建的文档条数")
    private int docCount;
}
