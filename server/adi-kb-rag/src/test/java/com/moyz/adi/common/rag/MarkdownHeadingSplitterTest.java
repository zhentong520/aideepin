package com.moyz.adi.common.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiChatModelName;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Markdown 标题层级切分器单测（2026-11-05，解析升级 P1 配套）。
 * 覆盖：标题切节 + 前缀注入、超长节递归切、无标题退化 recursive、前缀预算截断。
 */
class MarkdownHeadingSplitterTest {

    private final TokenCountEstimator estimator = new OpenAiTokenCountEstimator(OpenAiChatModelName.GPT_3_5_TURBO);

    private MarkdownHeadingSplitter newSplitter(int size) {
        return new MarkdownHeadingSplitter(size, 0, estimator);
    }

    @Test
    void countHeadings_shouldCountAtxHeadingsOnly() {
        String md = "# a\npara\n## b\n###c(no space)\nnot # heading";
        assertEquals(2, MarkdownHeadingSplitter.countHeadings(md));
        assertEquals(0, MarkdownHeadingSplitter.countHeadings("plain text\nno heading"));
        assertEquals(0, MarkdownHeadingSplitter.countHeadings(""));
    }

    @Test
    void split_shouldSectionByHeading_andPrefixAncestorPath() {
        String md = """
                # 第一章 概述
                第一章正文内容。
                ## 1.1 背景
                背景正文内容。
                ## 1.2 目标
                目标正文内容。
                """;
        List<TextSegment> segments = newSplitter(512).split(Document.from(md));
        // 导语（无） + 3 节；# 第一章 节含其正文，1.1/1.2 各一节
        assertEquals(3, segments.size());
        // 每块带祖先标题路径前缀
        assertTrue(segments.get(0).text().startsWith("第一章 概述\n\n"));
        assertTrue(segments.get(1).text().startsWith("第一章 概述 > 1.1 背景\n\n"));
        assertTrue(segments.get(2).text().startsWith("第一章 概述 > 1.2 目标\n\n"));
        assertTrue(segments.get(1).text().contains("背景正文内容。"));
    }

    @Test
    void split_oversizedSection_shouldSplitFurther_withPrefixOnEveryChunk() {
        String body = "很长的一行正文。".repeat(200);
        String md = "# 标题A\n" + body + "\n## 标题B\n短正文。";
        List<TextSegment> segments = newSplitter(128).split(Document.from(md));
        assertTrue(segments.size() > 2, "超长节应被递归切成多块");
        // 所有块都带标题前缀
        for (int i = 0; i < segments.size() - 1; i++) {
            assertTrue(segments.get(i).text().startsWith("标题A\n\n"), "第 " + i + " 块应带标题A前缀");
        }
        assertTrue(segments.get(segments.size() - 1).text().startsWith("标题A > 标题B\n\n"));
    }

    @Test
    void split_noHeading_shouldFallback() {
        String text = "没有标题的普通文本。".repeat(50);
        List<TextSegment> segments = newSplitter(64).split(Document.from(text));
        assertFalse(segments.isEmpty());
        // 退化切分不应带 "x > y" 形式的前缀注入
        for (TextSegment seg : segments) {
            assertFalse(seg.text().startsWith("\n\n"));
        }
    }

    @Test
    void split_longHeadingPath_shouldRespectPrefixBudget() {
        String md = "# " + "超长标题".repeat(50) + "\n## 二级\n正文内容。\n### 三级\n更深正文。";
        List<TextSegment> segments = newSplitter(64).split(Document.from(md));
        assertFalse(segments.isEmpty());
        // 每块总长不应显著超过 maxSegmentSize（前缀被预算约束）
        for (TextSegment seg : segments) {
            assertTrue(estimator.estimateTokenCountInText(seg.text()) <= 64 + 32,
                    "块超预算: " + seg.text().length());
        }
    }

    @Test
    void split_imagePlaceholder_shouldStayWithItsSection() {
        String md = "# 图表节\n前文。\n![某图说明](/file/abc.png)\n后文。\n## 下一节\n其他内容。";
        List<TextSegment> segments = newSplitter(512).split(Document.from(md));
        assertEquals(2, segments.size());
        assertTrue(segments.get(0).text().contains("/file/abc.png"));
    }

    @Test
    void split_blankDocument_shouldReturnEmpty() {
        // langchain4j Document.from("") 自身即抛 IllegalArgument（文档不允许空白），
        // 故空文档场景只验证 null 入参的防御分支（实现返回空列表）
        assertTrue(newSplitter(512).split(null).isEmpty());
    }
}
