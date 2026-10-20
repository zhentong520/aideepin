package com.moyz.adi.common.eval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 网关对一道评测题的真实应答。评测器只认这里给出的证据，绝不自行脑补。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResult {

    /**
     * 检索链路返回的候选块（已经过展开/融合/重排/扩窗的最终上下文）
     */
    private List<RetrievedChunk> chunks;

    /**
     * 生成模型给出的答案；未开启生成（纯检索评测）时为 null
     */
    private String answer;

    /**
     * 系统是否拒答。null 表示由评分器按规则推断（无答案 / 命中拒答话术 / 无检索结果）
     */
    private Boolean refused;

    /**
     * 网关侧异常信息（检索/生成抛错时填入，本题按失败计，绝不静默跳过）
     */
    private String error;
}
