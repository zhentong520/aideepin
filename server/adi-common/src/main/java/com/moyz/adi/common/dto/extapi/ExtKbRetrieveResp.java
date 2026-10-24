package com.moyz.adi.common.dto.extapi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * POST /ext/v1/knowledge/retrieve 响应（T1-08，2026-10-19）。
 * <p>
 * items 只含检索结果（content/score/docUuid/kbUuid/白名单 metadata），不含任何生成内容；
 * metadata 白名单见 {@link com.moyz.adi.common.constant.AdiConstant.MetadataKey}。
 * skippedKbUuids：无权或不存在的库静默跳过（MCP 场景则报错，语义区分见 T1-10）。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExtKbRetrieveResp {

    private List<Item> items;

    private List<String> skippedKbUuids;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Item {

        private String content;

        /**
         * 向量库原始分数（pgvector 为 (1+cos)/2 压缩值）；扩窗邻段等非直接命中为 null
         */
        private Double score;

        private String docUuid;

        private String docTitle;

        private String kbUuid;

        /**
         * metadata 白名单字段：kb_uuid / kb_item_uuid / doc_uuid / doc_title / segment_id / position
         */
        private Map<String, Object> metadata;
    }
}
