package com.moyz.adi.common.multiagent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.multiagent.DelegationContract.DifficultySignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * T7-02 委派契约（{@link DelegationContract}）单测：四要素结构化 + 难度信号。
 * <p>
 * 契约四要素（objective / output_format / context_summary / must_not_do）是委派可追溯的
 * 最小单位：objective 必填（缺了既无法执行也无法审计），其余可缺省；难度信号是
 * effort-scaling 的输入，必须是纯函数、信号与分值单调可预期。
 */
class DelegationContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static ObjectNode json(String raw) throws Exception {
        return (ObjectNode) OBJECT_MAPPER.readTree(raw);
    }

    @Test
    @DisplayName("四要素完整解析：objective / output_format / context_summary / must_not_do")
    void parsesAllFourFields() throws Exception {
        DelegationContract contract = DelegationContract.fromJson(json("""
                {
                  "objective": "调研三家供应商的报价并给出对比结论",
                  "output_format": "Markdown 表格",
                  "context_summary": "预算上限 50 万，已有两家候选",
                  "must_not_do": "不要直接联系供应商，不要访问用户知识库"
                }
                """));
        assertEquals("调研三家供应商的报价并给出对比结论", contract.getObjective());
        assertEquals("Markdown 表格", contract.getOutputFormat());
        assertEquals("预算上限 50 万，已有两家候选", contract.getContextSummary());
        assertEquals("不要直接联系供应商，不要访问用户知识库", contract.getMustNotDo());
    }

    @Test
    @DisplayName("objective 必填：缺失或空白时拒参（契约缺目标无法执行也无法审计）")
    void objectiveIsRequired() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> DelegationContract.fromJson(json("{\"output_format\":\"json\"}")));
        assertThrows(IllegalArgumentException.class,
                () -> DelegationContract.fromJson(json("{\"objective\":\"   \"}")));
    }

    @Test
    @DisplayName("可选字段容错：只有 objective 时其余为 null，不炸")
    void optionalFieldsTolerated() throws Exception {
        DelegationContract contract = DelegationContract.fromJson(json("{\"objective\":\"查一下汇率\"}"));
        assertEquals("查一下汇率", contract.getObjective());
        assertNull(contract.getOutputFormat());
        assertNull(contract.getContextSummary());
        assertNull(contract.getMustNotDo());
    }

    @Test
    @DisplayName("toPromptText 渲染四段式结构，目标 Agent 无需额外系统提示")
    void rendersPromptText() {
        DelegationContract contract = DelegationContract.builder()
                .objective("写一段产品文案")
                .outputFormat("不超过 100 字")
                .contextSummary("新品是面向开发者的 CLI 工具")
                .mustNotDo("不要使用夸张修辞")
                .build();
        String text = contract.toPromptText();
        assertTrue(text.contains("【委派目标】写一段产品文案"), text);
        assertTrue(text.contains("【产出格式】不超过 100 字"), text);
        assertTrue(text.contains("【背景摘要】新品是面向开发者的 CLI 工具"), text);
        assertTrue(text.contains("【禁止事项】不要使用夸张修辞"), text);
    }

    @Test
    @DisplayName("toPromptText 跳过空白可选字段，不产生空段")
    void skipsBlankOptionals() {
        DelegationContract contract = DelegationContract.builder().objective("只做一件事").build();
        assertEquals("【委派目标】只做一件事", contract.toPromptText());
    }

    @Test
    @DisplayName("难度信号：长度/句数/问号/列举行四项均可独立识别")
    void difficultySignalsAreCounted() {
        DifficultySignal signal = new DelegationContract.DifficultySignal(
                "请完成以下三步：\n1. 查数据\n2. 做对比\n3. 写结论。有疑问吗？");
        assertEquals("请完成以下三步：\n1. 查数据\n2. 做对比\n3. 写结论。有疑问吗？".length(), signal.getObjectiveLength());
        assertEquals(1, signal.getQuestionMarkCount());
        assertTrue(signal.getSentenceCount() >= 2, "句末标点与换行都应计入句子，实际：" + signal.getSentenceCount());
        assertTrue(signal.getListLineCount() >= 3, "编号行应计入列举，实际：" + signal.getListLineCount());
    }

    @Test
    @DisplayName("复杂度分值随难度单调上升且封顶 100")
    void complexityScoreIsMonotonicAndCapped() {
        int simple = new DelegationContract.DifficultySignal("查一下天气").complexityScore();
        int harder = new DelegationContract.DifficultySignal(
                "请按顺序完成：\n1. 拉取上周销售数据\n2. 按区域汇总\n3. 找出环比下降最大的三个区域并分析原因。是否需要发邮件？要不要看板？").complexityScore();
        assertTrue(harder > simple, "多步多子项任务分值必须更高，simple=" + simple + " harder=" + harder);
        assertTrue(simple >= 0 && harder <= 100);
        // 四类信号拉满：长度 40 + 句数 25 + 列举 20 + 问号 15 = 100 封顶
        StringBuilder maxed = new StringBuilder("长".repeat(2000));
        for (int i = 0; i < 30; i++) {
            maxed.append("。\n");
        }
        for (int i = 0; i < 6; i++) {
            maxed.append("- 项").append(i).append('\n');
        }
        for (int i = 0; i < 6; i++) {
            maxed.append("？");
        }
        assertEquals(100, new DelegationContract.DifficultySignal(maxed.toString()).complexityScore());
    }

    @Test
    @DisplayName("describeSignals 汇总全部信号，供落库 detail 字段")
    void describeSignalsListsAll() {
        List<String> signals = new DelegationContract.DifficultySignal("做 A 和 B。").describeSignals();
        assertEquals(5, signals.size());
        assertTrue(signals.stream().anyMatch(s -> s.startsWith("objectiveLength=")));
        assertTrue(signals.stream().anyMatch(s -> s.startsWith("complexityScore=")));
    }
}
