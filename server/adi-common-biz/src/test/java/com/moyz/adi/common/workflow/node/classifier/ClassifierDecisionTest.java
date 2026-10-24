package com.moyz.adi.common.workflow.node.classifier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ClassifierDecision} 与 {@link ClassifierPrompt} 的单元测试（纯逻辑，不启 Spring）。<br/>
 * <br/>
 * 断言约定：按本项目「假 OK」红线，每条断言只问一句——<b>被测功能彻底坏掉，它会红吗</b>。
 * 因此一律断言具体值，不用「非空」「不等于 0」这类在失败路径上也会成立的表述。
 */
class ClassifierDecisionTest {

    private static final String BILLING_UUID = "cat-billing-0001";
    private static final String TECH_UUID = "cat-tech-0002";
    private static final String NODE_BILLING = "node-billing";
    private static final String NODE_TECH = "node-tech";

    private static List<ClassifierCategory> categories() {
        ClassifierCategory billing = new ClassifierCategory();
        billing.setCategoryUuid(BILLING_UUID);
        billing.setCategoryName("Billing");
        billing.setCategoryDescription("Payment, invoice or subscription issues");
        billing.setTargetNodeUuid(NODE_BILLING);

        ClassifierCategory tech = new ClassifierCategory();
        tech.setCategoryUuid(TECH_UUID);
        tech.setCategoryName("Technical");
        tech.setCategoryDescription("Bugs, errors or integration failures");
        tech.setTargetNodeUuid(NODE_TECH);
        return List.of(billing, tech);
    }

    private static ClassifierNodeConfig config(boolean withDescription) {
        List<ClassifierCategory> cats = categories();
        if (!withDescription) {
            cats.forEach(item -> item.setCategoryDescription(null));
        }
        ClassifierNodeConfig config = new ClassifierNodeConfig();
        config.setCategories(cats);
        return config;
    }

    @Test
    @DisplayName("解析：裸 JSON 与 markdown 围栏 JSON 都要能取出")
    void parseHandlesPlainAndFencedJson() {
        String plain = "{\"category_uuid\":\"" + BILLING_UUID + "\",\"category_name\":\"Billing\",\"confidence\":0.9}";
        Optional<ClassifierLLMResp> r1 = ClassifierDecision.parse(plain);
        assertTrue(r1.isPresent(), "裸 JSON 必须解析成功");
        assertEquals(BILLING_UUID, r1.get().getCategoryUuid());
        assertEquals(0.9d, r1.get().getConfidence(), 1e-9);

        String fenced = "Sure! Here is the result:\n```json\n" + plain + "\n```\nHope it helps.";
        Optional<ClassifierLLMResp> r2 = ClassifierDecision.parse(fenced);
        assertTrue(r2.isPresent(), "```json 围栏是 LLM 最常见的返回形式，必须解析成功");
        assertEquals(BILLING_UUID, r2.get().getCategoryUuid());

        String bareFence = "```\n{\"category_uuid\":\"" + TECH_UUID + "\"}\n```";
        Optional<ClassifierLLMResp> r3 = ClassifierDecision.parse(bareFence);
        assertTrue(r3.isPresent(), "无语言标记的围栏也要能解析");
        assertEquals(TECH_UUID, r3.get().getCategoryUuid());
    }

    @Test
    @DisplayName("解析：垃圾文本 / 空串 / 无可用字段一律视为未答，而不是 NPE 或半截对象")
    void parseReturnsEmptyOnJunk() {
        assertFalse(ClassifierDecision.parse("Sorry, I cannot help with that.").isPresent());
        assertFalse(ClassifierDecision.parse("   ").isPresent());
        assertFalse(ClassifierDecision.parse(null).isPresent());
        assertFalse(ClassifierDecision.parse("{\"confidence\":0.99}").isPresent(), "只有置信度、没有类别字段 = 没答");
    }

    @Test
    @DisplayName("提取 JSON 对象：字符串里出现花括号不能被截断")
    void extractJsonObjectIgnoresBracesInString() {
        String raw = "note {oops\n{\"category_uuid\":\"" + BILLING_UUID + "\",\"note\":\"value with {braces} inside\"}";
        Optional<String> json = ClassifierDecision.extractJsonObject(raw);
        assertTrue(json.isPresent());
        assertTrue(json.get().endsWith("}"), "必须取到完整闭合的 JSON 对象");
        ClassifierLLMResp resp = ClassifierDecision.parse(raw).orElseThrow();
        assertEquals(BILLING_UUID, resp.getCategoryUuid());
    }

    @Test
    @DisplayName("匹配：uuid 优先；uuid 抄错时按类别名兜底")
    void matchPrefersUuidThenFallsBackToName() {
        List<ClassifierCategory> cats = categories();

        ClassifierLLMResp byUuid = new ClassifierLLMResp();
        byUuid.setCategoryUuid(TECH_UUID);
        Optional<ClassifierCategory> m1 = ClassifierDecision.match(byUuid, cats);
        assertTrue(m1.isPresent());
        assertEquals(NODE_TECH, m1.get().getTargetNodeUuid(), "必须路由到 Technical 分支");

        // uuid 命中但明明是错的拼写场景不会走到名称兜底，这里模拟"模型自造 uuid"
        ClassifierLLMResp badUuid = new ClassifierLLMResp();
        badUuid.setCategoryUuid("cat-does-not-exist");
        badUuid.setCategoryName("billing");
        Optional<ClassifierCategory> m2 = ClassifierDecision.match(badUuid, cats);
        assertTrue(m2.isPresent(), "uuid 对不上时应按类别名兜底");
        assertEquals(NODE_BILLING, m2.get().getTargetNodeUuid());
        assertEquals(ClassifierDecision.MatchType.NAME,
                ClassifierDecision.decide("{\"category_uuid\":\"x\",\"category_name\":\"billing\"}", config(true)).matchType());
    }

    @Test
    @DisplayName("匹配：候选之外的一律不命中")
    void matchNothingWhenUnknown() {
        ClassifierLLMResp resp = new ClassifierLLMResp();
        resp.setCategoryUuid("nope");
        resp.setCategoryName("Marketing");
        assertTrue(ClassifierDecision.match(resp, categories()).isEmpty());
        assertTrue(ClassifierDecision.match(resp, List.of()).isEmpty());
    }

    @Test
    @DisplayName("置信度归一：百分制换算，越界与 NaN 一律作废而不是钳位")
    void normalizeConfidenceRules() {
        assertEquals(0.0d, ClassifierDecision.normalizeConfidence(0.0d), 1e-9);
        assertEquals(1.0d, ClassifierDecision.normalizeConfidence(1.0d), 1e-9);
        assertEquals(0.8d, ClassifierDecision.normalizeConfidence(80.0d), 1e-9, "80 应读作百分制 0.8");
        assertNull(ClassifierDecision.normalizeConfidence(120.0d), "越界值必须作废，不能钳到 1.0 伪装成高置信");
        assertNull(ClassifierDecision.normalizeConfidence(-0.5d));
        assertNull(ClassifierDecision.normalizeConfidence(Double.NaN));
        assertNull(ClassifierDecision.normalizeConfidence(null));
    }

    @Test
    @DisplayName("阈值：低于阈值时保留类别但标记不可信，由调用方决定是否走兜底")
    void lowConfidenceKeepsCategoryButFlagsIt() {
        ClassifierNodeConfig cfg = config(true);
        cfg.setMinConfidence(0.9d);
        ClassifierDecision.Decision d = ClassifierDecision.decide(
                "{\"category_uuid\":\"" + BILLING_UUID + "\",\"confidence\":0.5}", cfg);
        assertTrue(d.matched(), "低置信不等于没命中，要保留下来供日志排查");
        assertTrue(d.lowConfidence());
        assertEquals(ClassifierDecision.Reason.LOW_CONFIDENCE, d.reason());
        assertEquals(NODE_BILLING, d.category().getTargetNodeUuid());
        assertEquals(0.5d, d.confidence(), 1e-9);
    }

    @Test
    @DisplayName("阈值：模型没给置信度且配了阈值，按未达标处理")
    void missingConfidenceCountsAsBelowWhenThresholdSet() {
        ClassifierNodeConfig cfg = config(true);
        cfg.setMinConfidence(0.5d);
        ClassifierDecision.Decision d = ClassifierDecision.decide(
                "{\"category_uuid\":\"" + BILLING_UUID + "\"}", cfg);
        assertTrue(d.matched());
        assertTrue(d.lowConfidence(), "无法证明置信就不该自动执行");
        assertNull(d.confidence());
    }

    @Test
    @DisplayName("阈值：没配阈值时复用改动前的宽松行为")
    void thresholdDisabledKeepsLegacyBehaviour() {
        ClassifierNodeConfig cfg = config(true);
        cfg.setMinConfidence(null);
        ClassifierDecision.Decision d = ClassifierDecision.decide(
                "{\"category_uuid\":\"" + BILLING_UUID + "\"}", cfg);
        assertFalse(d.lowConfidence());
        assertEquals(ClassifierDecision.Reason.OK, d.reason());

        cfg.setMinConfidence(0.0d);
        assertFalse(ClassifierDecision.decide(
                "{\"category_uuid\":\"" + BILLING_UUID + "\"}", cfg).lowConfidence(), "0 视同未配置");
    }

    @Test
    @DisplayName("决策原因可区分：空返回 / 无法解析 / 类别查不到")
    void decisionReasonsAreDistinguishable() {
        assertEquals(ClassifierDecision.Reason.EMPTY_RESPONSE,
                ClassifierDecision.decide("   ", config(true)).reason());
        assertEquals(ClassifierDecision.Reason.UNPARSEABLE,
                ClassifierDecision.decide("I can't classify this.", config(true)).reason());
        assertEquals(ClassifierDecision.Reason.CATEGORY_NOT_FOUND,
                ClassifierDecision.decide("{\"category_uuid\":\"ghost\"}", config(true)).reason());
        assertNull(ClassifierDecision.decide("{\"category_uuid\":\"ghost\"}", config(true)).category());
    }

    @Test
    @DisplayName("提示词：不再要求 keywords，且把判据与类别名分开下发")
    void promptDropsKeywordsAndShipsCriteria() {
        String prompt = ClassifierPrompt.createPrompt("my invoice is wrong", categories());
        assertFalse(prompt.contains("keywords"), "keywords 已被节点消费方废弃，不应出现在提示词里");
        assertTrue(prompt.contains("criteria"), "必须下发独立判据字段");
        assertTrue(prompt.contains("Payment, invoice or subscription issues"), "判据内容要真的进提示词");
        assertTrue(prompt.contains(BILLING_UUID), "类别 uuid 要在提示词里，模型才能逐字复制");
        assertTrue(prompt.contains("confidence"));

        String legacy = ClassifierPrompt.createPrompt("x", config(false).getCategories());
        assertTrue(legacy.contains("\"category_name\":\"Billing\""), "无判据时退回类别名，旧配置行为不变");
        assertTrue(legacy.contains("\"criteria\":\"Billing\""), "判据缺失时用类别名兜底");
    }

    @Test
    @DisplayName("类别实体：判据缺失时 effectiveDescription 退回类别名")
    void effectiveDescriptionFallsBackToName() {
        List<ClassifierCategory> legacy = config(false).getCategories();
        assertEquals("Billing", legacy.get(0).effectiveDescription());
        assertNotNull(legacy.get(0).getTargetNodeUuid());
    }
}
