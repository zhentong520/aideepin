package com.moyz.adi.common.service;

import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.rag.DocumentSplitterFactory;
import com.moyz.adi.common.rag.TokenEstimatorFactory;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.moyz.adi.common.cosntant.AdiConstant.SplitStrategy.SENTENCE_CN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T6-03：qa 生成路径分块统一的断言。
 * <p>
 * 此前 qa 生成（LLM 自动出题）按 {@code GENERATE_CHUNK_SIZE=1500} + 库策略切分待出题文本，
 * 是知识库入库入口里唯一与 025 实测参数（sentence_cn/512/overlap=0）不一致的一个。
 * 统一开关打开后，qa 路径切分结果必须与 025 的 CjkSentenceSplitter(512,0) 完全一致；
 * 关闭时回退历史行为（1500 + 库策略），作为回滚对照保留。
 * <p>
 * 全部离线断言（不依赖 Spring / LLM / 嵌入），改动前 T6-02 基线已跑绿。
 */
class QaChunkingUnifiedTest {

    /**
     * ~600 token 的中文条款语料：512 档切多块、1500 档单块，正好拉开两种参数的差异。
     */
    private static final String POLICY_TEXT = repeatClauses();

    private static String repeatClauses() {
        StringBuilder sb = new StringBuilder();
        String[] clauses = {
                "第一条 物业服务企业应当按照物业服务合同的约定提供相应的服务。",
                "第二条 业主应当根据物业服务合同的约定交纳物业服务费用，逾期不交纳的，物业服务企业可以依法追缴。",
                "第三条 物业管理区域内，供水、供电、供气、供热、通信、有线电视等单位应当向最终用户收取有关费用，但业主与物业服务企业另有约定的除外。",
                "第四条 县级以上地方人民政府房地产行政主管部门应当及时处理业主、业主委员会、物业使用人和物业服务企业在物业管理活动中的投诉。"
        };
        // 拼到远超 512 token，保证 512 档必然多块
        for (int round = 0; round < 6; round++) {
            for (String clause : clauses) {
                sb.append(clause.replace("第", "第" + (round + 1) + "批之"));
            }
        }
        return sb.toString();
    }

    @Test
    void unifiedSwitchOn_qaChunksAreIdenticalTo025SentenceCn() {
        KnowledgeBase kb = kbWithLegacySettings();

        List<TextSegment> qaChunks = DocumentQaModeService
                .resolveQaGenerateSplitter(true, kb)
                .split(new DefaultDocument(POLICY_TEXT));

        // 025 口径：sentence_cn / 512 / overlap=0（与普通文档路径默认完全同参同估算器）
        List<TextSegment> expected = DocumentSplitterFactory.create(
                SENTENCE_CN, DocumentQaModeService.UNIFIED_CHUNK_SIZE, 0, "",
                com.moyz.adi.common.rag.TokenEstimatorFactory.create(kb.getIngestTokenEstimator()))
                .split(new DefaultDocument(POLICY_TEXT));

        assertEquals(texts(expected), texts(qaChunks),
                "统一开关打开时 qa 路径分块结果必须与 025 sentence_cn/512/overlap=0 逐块一致");
        assertTrue(qaChunks.size() > 1, "语料必须被切成多块，否则本断言没有意义");
        // 收口不变量：除最后一块外，每块收在 CJK 句末标点上（025 的核心性质）
        for (int i = 0; i < qaChunks.size() - 1; i++) {
            String chunk = qaChunks.get(i).text().strip();
            assertTrue(chunk.endsWith("。") || chunk.endsWith("！") || chunk.endsWith("？") || chunk.endsWith("；"),
                    "第 " + i + " 块必须收在句界上");
        }
    }

    @Test
    void unifiedSwitchOn_chunkSizeIs512NotLegacy1500() {
        KnowledgeBase kb = kbWithLegacySettings();

        List<TextSegment> unified = DocumentQaModeService
                .resolveQaGenerateSplitter(true, kb)
                .split(new DefaultDocument(POLICY_TEXT));
        List<TextSegment> legacy = DocumentQaModeService
                .resolveQaGenerateSplitter(false, kb)
                .split(new DefaultDocument(POLICY_TEXT));

        assertTrue(unified.size() > legacy.size(),
                "512 档必须比旧的 1500 档切出更多块（旧参数是本卡要消灭的分叉），实际 unified="
                        + unified.size() + " legacy=" + legacy.size());
    }

    @Test
    void unifiedSwitchOff_restoresLegacyBehaviourByteForByte() {
        KnowledgeBase kb = kbWithLegacySettings();

        List<TextSegment> qaChunks = DocumentQaModeService
                .resolveQaGenerateSplitter(false, kb)
                .split(new DefaultDocument(POLICY_TEXT));

        // 回滚对照：开关关闭 = 历史行为（库策略 + 1500 + 库 overlap）逐块一致
        List<TextSegment> legacy = DocumentSplitterFactory.create(
                kb.getIngestSplitStrategy(),
                DocumentQaModeService.GENERATE_CHUNK_SIZE,
                kb.getIngestMaxOverlap(),
                kb.getIngestCustomSeparator(),
                TokenEstimatorFactory.create(kb.getIngestTokenEstimator()))
                .split(new DefaultDocument(POLICY_TEXT));

        assertEquals(texts(legacy), texts(qaChunks), "开关关闭必须与历史行为逐块一致");
    }

    /** 模拟一条仍带旧显式配置的知识库行（recursive / overlap 64）：统一开关必须覆盖它 */
    private static KnowledgeBase kbWithLegacySettings() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setIngestSplitStrategy("recursive");
        kb.setIngestMaxSegmentSize(1000);
        kb.setIngestMaxOverlap(64);
        kb.setIngestCustomSeparator("");
        kb.setIngestTokenEstimator(null); // 库级估算器缺省 → 工厂回退默认，两条路径同参
        return kb;
    }

    private static List<String> texts(List<TextSegment> chunks) {
        return chunks.stream().map(TextSegment::text).toList();
    }
}
