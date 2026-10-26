package com.moyz.adi.common.service;

import com.moyz.adi.common.rag.DocumentSplitterFactory;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分块参数（迁移 024）的行为断言。
 * <p>
 * 后两条测试锁定的是 langchain4j 的实测行为，而不是我们期望的行为：overlap 的实现是
 * "把上一块的最后一个子段拼到下一块开头"，中文长文既没有空行也没有 ASCII 标点，整块即一个
 * 子段。因此小 overlap 完全不生效，大 overlap 会让下一块整块重复上一块。
 * 这正是迁移 024 只调 segment size、不把 overlap 默认值调大的原因。
 */
class SegmentIndexServiceChunkParamsTest {

    /**
     * 三条物业条例，长度足以在 120 token 的段大小下切成多块
     */
    private static final String POLICY_TEXT =
            "第一条 为规范物业管理活动，维护业主和物业服务企业的合法权益，改善人民群众的生活和工作环境，制定本条例。"
            + "第二条 本条例所称物业管理，是指业主通过选聘物业服务企业，由业主和物业服务企业按照物业服务合同约定，"
            + "对房屋及配套的设施设备和相关场地进行维修、养护、管理，维护物业管理区域内的环境卫生和相关秩序的活动。"
            + "第三条 国家提倡业主通过公开、公平、公正的市场竞争机制选择物业服务企业，鼓励采用新技术新方法提高物业服务水平。";

    @Test
    void segmentSizeFallsBackTo512ForLegacyRows() {
        assertEquals(512, SegmentIndexService.resolveSegmentSize(null), "历史行缺列时必须回落到 512");
        assertEquals(512, SegmentIndexService.resolveSegmentSize(0));
        assertEquals(512, SegmentIndexService.resolveSegmentSize(-1));
        assertEquals(800, SegmentIndexService.resolveSegmentSize(800), "显式配置必须原样保留");
    }

    @Test
    void overlapFallbackStaysZero() {
        assertEquals(0, SegmentIndexService.resolveOverlap(512, null), "缺列时不能兜底成非零重叠");
        assertEquals(0, SegmentIndexService.resolveOverlap(512, 0), "显式 0 表示关闭重叠");
        assertEquals(20, SegmentIndexService.resolveOverlap(512, 20), "显式配置必须原样保留");
        assertEquals(64, SegmentIndexService.resolveOverlap(512, 64));
    }

    @Test
    void overlapIsClampedBelowSegmentSize() {
        // langchain4j 对 overlap > maxSegmentSize 直接抛 IllegalArgumentException
        assertEquals(8, SegmentIndexService.resolveOverlap(32, 64), "32 的段配 64 重叠必须钳到 size/4");
        assertEquals(0, SegmentIndexService.resolveOverlap(1, 64), "极端小段不能返回 >= size 的重叠");
        assertEquals(3, SegmentIndexService.resolveOverlap(13, 64));
        assertEquals(16, SegmentIndexService.resolveOverlap(64, 64), "等于 size 同样非法，必须钳制");
    }

    @Test
    void clampedOverlapStillSplitsRealText() {
        // 默认重叠改动虽已回退，钳制仍要留着：运维在管理端把 overlap 配得比 size 大时，
        // 不能因为一个非法参数把整条索引任务打挂。
        DocumentSplitter splitter = DocumentSplitterFactory.create(
                "recursive", 32, SegmentIndexService.resolveOverlap(32, 64), "", estimator());

        List<TextSegment> chunks = splitter.split(new DefaultDocument(POLICY_TEXT));

        assertFalse(chunks.isEmpty(), "钳制后必须仍能切出内容");
    }

    @Test
    void smallOverlapIsANoOpOnUnsegmentedChineseText() {
        List<TextSegment> withOverlap = split(120, 20);
        List<TextSegment> withoutOverlap = split(120, 0);

        assertEquals(texts(withoutOverlap), texts(withOverlap),
                "overlap=20 与 overlap=0 的切分结果完全相同，小 overlap 对中文长文是空操作");
    }

    @Test
    void overlapReachingChunkSizeDuplicatesThePreviousChunk() {
        List<TextSegment> chunks = split(120, 64);

        assertTrue(chunks.size() > 1, "素材必须被切成多块，否则本断言没有意义");
        String first = chunks.get(0).text().strip();
        assertTrue(chunks.get(1).text().contains(first),
                "overlap=64 时第二块以整个第一块开头——是整块重复，不是保留上下文");
    }

    private static TokenCountEstimator estimator() {
        return new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);
    }

    private static List<TextSegment> split(int size, int overlap) {
        return DocumentSplitterFactory.create("recursive", size, overlap, "", estimator())
                .split(new DefaultDocument(POLICY_TEXT));
    }

    private static List<String> texts(List<TextSegment> chunks) {
        return chunks.stream().map(TextSegment::text).toList();
    }
}
