package com.moyz.adi.common.eval;

/**
 * 评测网关 SPI：把「一道题」交给真实检索/生成链路，拿回应答。
 * <p>
 * 生产实现 {@link KbQaEvalGateway} 走现有知识库链路（CompositeRag → EmbeddingRag，
 * 含混合检索/RRF/rerank/扩窗后置处理器）。测试里用 mock 实现固定应答来测计分器。
 */
public interface QaEvalGateway {

    GatewayResult ask(QaEvalItem item, EvalConfig config);
}
