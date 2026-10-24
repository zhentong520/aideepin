package com.moyz.adi.common.rag;

import com.moyz.adi.common.entity.AiModel;
import com.moyz.adi.common.helper.RerankModelContext;
import com.moyz.adi.common.languagemodel.OpenAiCompatibleScoringModel;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 重排器的「静默失败」防线（2026-09-30 补）。
 * <p>
 * 背景：{@code OpenAiCompatibleScoringModel.scoreAll} 在网络异常 / 非 2xx / 响应解析失败时会
 * 预填 0 分返回（为满足"返回列表长度与入参一致"的契约）。若重排器不识别这批 0 分，它们经
 * sigmoid 变成清一色 0.5、排序退回原顺序——在界面和日志上与「模型认为各片段都很相近」完全一样，
 * 等于把故障伪装成成功。本测试把"全 0 必须判为未执行"钉死，防止以后被改回去。
 */
class RerankContentRerankerTest {

    private final RerankContentReranker reranker = new RerankContentReranker();

    @AfterEach
    void tearDown() {
        RerankModelContext.clearAll();
    }

    private static OpenAiCompatibleScoringModel model(String name, List<Double> scores) {
        OpenAiCompatibleScoringModel m = mock(OpenAiCompatibleScoringModel.class);
        AiModel aiModel = new AiModel();
        aiModel.setName(name);
        when(m.getAiModel()).thenReturn(aiModel);
        when(m.scoreAll(anyList(), anyString())).thenReturn(Response.from(scores));
        return m;
    }

    private static List<Content> contents(int n) {
        List<Content> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(Content.from(TextSegment.from("seg-" + i)));
        }
        return list;
    }

    private static RetrieverContext enabledContext() {
        RetrieverContext ctx = new RetrieverContext();
        ctx.setRerankEnabled(true);
        ctx.setRerankModelId(0L);   // 0 = 取第一个可用模型
        return ctx;
    }

    @Test
    void allZeroScoresAreTreatedAsCallFailure() {
        RerankModelContext.addService(model("zero-rerank", List.of(0.0, 0.0, 0.0)));

        RerankContentReranker.RerankOutcome outcome =
                reranker.rerankWithScores("query", contents(3), enabledContext());

        assertFalse(outcome.isExecuted(), "全 0 分必须判定为未真正执行，否则会被 sigmoid 伪装成 0.5 分");
        assertEquals(3, outcome.getContents().size(), "未执行时应原样返回全部内容");
        assertEquals("seg-0", outcome.getContents().get(0).textSegment().text(), "顺序应保持召回顺序");
        assertTrue(outcome.getScoreByContent().isEmpty(), "未执行时不得回填任何重排分");
    }

    @Test
    void normalScoresRerankAndSortDescending() {
        RerankModelContext.addService(model("real-rerank", List.of(-2.0, 5.0, 0.5)));

        RerankContentReranker.RerankOutcome outcome =
                reranker.rerankWithScores("query", contents(3), enabledContext());

        assertTrue(outcome.isExecuted(), "存在非 0 分时应正常执行");
        assertEquals("seg-1", outcome.getContents().get(0).textSegment().text(), "最高分应排首位");
        assertEquals(3, outcome.getScoreByContent().size(), "每条内容都应带上归一化后的分数");
        assertTrue(outcome.getScoreByContent().values().stream().allMatch(s -> s > 0 && s < 1),
                "分数应经 sigmoid 归一化到 (0,1)");
    }

    @Test
    void sizeMismatchFallsBackToOriginOrder() {
        RerankModelContext.addService(model("bad-size-rerank", List.of(1.0, 2.0)));

        RerankContentReranker.RerankOutcome outcome =
                reranker.rerankWithScores("query", contents(3), enabledContext());

        assertFalse(outcome.isExecuted(), "分数条数与内容条数不符时必须整体放弃");
        assertEquals("seg-0", outcome.getContents().get(0).textSegment().text());
    }

    @Test
    void allFilteredByMinScoreFallsBackToOriginOrder() {
        RerankModelContext.addService(model("strict-rerank", List.of(-9.0, -8.0, -7.0)));
        RetrieverContext ctx = enabledContext();
        ctx.setRerankMinScore(0.9);

        RerankContentReranker.RerankOutcome outcome =
                reranker.rerankWithScores("query", contents(3), ctx);

        assertFalse(outcome.isExecuted(),
                "阈值把全部结果滤空时应退回原顺序，避免严格模式拿到空上下文直接中断");
        assertEquals(3, outcome.getContents().size());
    }

    @Test
    void disabledByContextIsNoOp() {
        RerankModelContext.addService(model("real-rerank", List.of(5.0, 1.0, -1.0)));
        RetrieverContext ctx = enabledContext();
        ctx.setRerankEnabled(false);

        RerankContentReranker.RerankOutcome outcome =
                reranker.rerankWithScores("query", contents(3), ctx);

        assertFalse(outcome.isExecuted(), "开关关闭时不得调用模型");
        assertEquals("seg-0", outcome.getContents().get(0).textSegment().text());
    }

    @Test
    void singleContentSkipsRerank() {
        RerankModelContext.addService(model("real-rerank", List.of(5.0)));

        RerankContentReranker.RerankOutcome outcome =
                reranker.rerankWithScores("query", contents(1), enabledContext());

        assertFalse(outcome.isExecuted(), "只有一条候选时重排没有意义，应直通");
    }
}
