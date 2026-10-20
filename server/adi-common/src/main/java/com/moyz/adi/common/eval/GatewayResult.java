package com.moyz.adi.common.eval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 单题评测网关应答（T6-01 评测链路）。
 * <p>
 * chunks  = 命中的检索块（计 recall）；
 * answer  = 生成的应答文本（withGeneration=false 时为 null）；
 * refused = 网关判定的拒答标记（null 时由 Scorer 按拒绝模式启发式推断）；
 * error   = 网关异常摘要（非空时本题按失败计）。
 * <p>
 * ⚠️ 本文件由 T6-05~T6-12 批次代为补齐（QaEvalGateway/QaEvalScorer/QaEvalRunner/
 * KbQaEvalGateway 均已引用此类但文件缺失导致整树无法编译）；
 * 字段与构造器完全按既有调用点形状实现，评测批次可直接按需扩展。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResult {

    private List<RetrievedChunk> chunks;

    private String answer;

    private Boolean refused;

    private String error;
}
