package com.moyz.adi.common.rag;

import com.moyz.adi.common.cosntant.AdiConstant;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour of the Chinese sentence splitter. Every assertion below was derived from running the
 * real splitter first -- including the ones that lock in behaviour which is *not* what the parameter
 * name suggests (overlap), so a future dependency upgrade that changes it shows up as a failure
 * instead of as silently different indexing.
 */
class CjkSentenceSplitterTest {

    private static final TokenCountEstimator ESTIMATOR =
            TokenEstimatorFactory.create(AdiConstant.TokenEstimator.OPENAI);

    /** A regulation-style paragraph: no blank lines, no ASCII punctuation, one long run of clauses. */
    private static final String CLAUSES =
            "第一条 为了规范小区物业管理活动，维护业主和物业服务企业的合法权益，制定本条例。"
                    + "第二条 本条例所称物业管理，是指业主通过选聘物业服务企业，由业主和物业服务企业按照物业服务合同约定，"
                    + "对房屋及配套的设施设备和相关场地进行维修、养护、管理，维护物业管理区域内的环境卫生和相关秩序的活动。"
                    + "第三条 国家提倡业主通过公开、公平、公正的市场竞争机制选择物业服务企业，但下列情形除外："
                    + "业主大会决定自行管理的，或者物业管理区域内的房屋尚未出售的。"
                    + "第四条 物业服务企业应当自物业服务合同签订之日起三十日内，将物业服务合同报物业管理行政主管部门备案。";

    private static DocumentSplitter splitter(int size, int overlap) {
        return DocumentSplitterFactory.create(AdiConstant.SplitStrategy.SENTENCE_CN, size, overlap, "", ESTIMATOR);
    }

    private static List<TextSegment> split(String text, int size, int overlap) {
        return splitter(size, overlap).split(Document.from(text));
    }

    private static String strip(String s) {
        return s.replaceAll("\\s+", "");
    }

    @Test
    void breaksAfterChineseSentenceTerminators() {
        // 200 tokens is above every sentence in this sample, so each clause can be held whole --
        // measured on the product corpus, the longest single sentence is 125 tokens.
        List<TextSegment> segments = split(CLAUSES, 200, 0);
        assertTrue(segments.size() >= 2, "clauses must be cut, got " + segments.size() + " segment(s)");
        for (TextSegment segment : segments) {
            String text = segment.text().strip();
            char last = text.charAt(text.length() - 1);
            assertEquals('。', last, "segment should end at a sentence terminator: " + text);
        }
    }

    /**
     * The documented limitation: when one sentence is bigger than the configured size, the inherited
     * fallback sub-splitter has to cut inside it. The limit still holds and no text is lost, but the
     * boundary is arbitrary. At the shipped default (512) this never happens -- the longest sentence
     * in the corpus is 125 tokens.
     */
    @Test
    void midSentenceCutIsTheFallbackWhenASentenceIsLargerThanTheSize() {
        List<TextSegment> segments = split(CLAUSES, 40, 0);
        assertTrue(segments.size() >= 5, "expected heavy cutting, got " + segments.size());
        for (TextSegment segment : segments) {
            assertTrue(ESTIMATOR.estimateTokenCountInText(segment.text()) <= 40,
                    "the size limit must hold even when a sentence has to be broken");
        }
        assertTrue(segments.stream().anyMatch(s -> !s.text().strip().endsWith("。")),
                "a sentence longer than the size is expected to be cut mid-sentence");
    }

    @Test
    void keepsEverySegmentWithinTheConfiguredTokenLimit() {
        int size = 200;
        for (TextSegment segment : split(CLAUSES, size, 0)) {
            int tokens = ESTIMATOR.estimateTokenCountInText(segment.text());
            assertTrue(tokens <= size, "segment of " + tokens + " tokens exceeds the " + size + " limit");
        }
    }

    @Test
    void losesNoText() {
        String source = strip(CLAUSES);
        StringBuilder joined = new StringBuilder();
        for (TextSegment segment : split(CLAUSES, 200, 0)) {
            joined.append(strip(segment.text()));
        }
        assertEquals(source, joined.toString(), "every character must survive splitting");
    }

    /**
     * Measured, not assumed: with this splitter the number of segments is unchanged by overlap, i.e.
     * the value currently has no effect. The regression this guards against is the *other* documented
     * failure mode, where overlap appended the previous segment whole (duplicating indexed content).
     */
    @Test
    void overlapNeitherDuplicatesSegmentsNorChangesSegmentCount() {
        List<TextSegment> without = split(CLAUSES, 200, 0);
        List<TextSegment> with64 = split(CLAUSES, 200, 64);

        assertEquals(without.size(), with64.size(),
                "overlap must not change how many segments the text is cut into");
        for (int i = 1; i < with64.size(); i++) {
            String previous = strip(with64.get(i - 1).text());
            String current = strip(with64.get(i).text());
            assertTrue(!current.contains(previous) || current.length() >= previous.length() + 40,
                    "segment " + i + " appears to repeat the previous segment wholesale");
        }
    }

    @Test
    void stillChunksTextWithoutAnyChinesePunctuation() {
        String english = "The knowledge base returns the segments that match the question. "
                + "Each segment is embedded and stored in pgvector. "
                + "Reranking happens after retrieval and can be disabled per knowledge base. "
                + "Strict mode returns no answer when nothing matches.";
        List<TextSegment> segments = split(english, 40, 0);
        assertTrue(segments.size() >= 2, "expected the text to be cut, got " + segments.size());
        for (TextSegment segment : segments) {
            int tokens = ESTIMATOR.estimateTokenCountInText(segment.text());
            assertTrue(tokens <= 40, "segment of " + tokens + " tokens exceeds the 40 limit");
        }
        StringBuilder joined = new StringBuilder();
        segments.forEach(s -> joined.append(strip(s.text())));
        assertEquals(strip(english), joined.toString(), "no text may be dropped for non-Chinese input");
    }

    @Test
    void producesNothingForBlankInput() {
        assertTrue(new CjkSentenceSplitter(200, 0, ESTIMATOR).split("   \n\n  ").length == 0,
                "blank text must produce no pieces at all");
    }
}
