package com.moyz.adi.common.eval;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 评测运行配置（写入报告的 config 段，保证两份报告可比对的前提可追溯）。
 */
@Data
public class EvalConfig {

    /**
     * 被评测知识库的 uuid（评测前需把语料按当前入库参数灌入该库）
     */
    private String kbUuid;

    /**
     * 召回条数；0 表示按库配置（自适应）
     */
    private int topK = 0;

    /**
     * 是否让生成模型产出答案（false 时只做检索侧评测，拒答按「无检索结果」判定）
     */
    private boolean withGeneration = false;

    /**
     * 入库 overlap 覆盖值（token）：-1 表示不覆盖、用库配置；T6-04 用它跑 {0, 0.1, 0.2} 三组对照
     */
    private int overlapOverride = -1;

    /**
     * 段大小覆盖值（token）：-1 表示不覆盖
     */
    private int segmentSizeOverride = -1;

    /**
     * 混合检索覆盖值：null 表示不覆盖（用库配置）
     */
    private Boolean hybridOverride;

    /**
     * rerank 覆盖值：null 表示不覆盖（用库配置）
     */
    private Boolean rerankOverride;

    private String notes = "";

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kbUuid", kbUuid);
        map.put("topK", topK);
        map.put("withGeneration", withGeneration);
        map.put("overlapOverride", overlapOverride);
        map.put("segmentSizeOverride", segmentSizeOverride);
        map.put("hybridOverride", hybridOverride);
        map.put("rerankOverride", rerankOverride);
        if (StringUtils.isNotBlank(notes)) {
            map.put("notes", notes);
        }
        return map;
    }
}
