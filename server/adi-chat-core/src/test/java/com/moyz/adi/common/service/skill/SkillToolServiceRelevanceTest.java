package com.moyz.adi.common.service.skill;

import com.moyz.adi.common.entity.Skill;
import com.moyz.adi.common.interfaces.SkillToolSpi;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T6-07 技能按需注入单测：多技能场景注入数量下降（可测 prompt token 下降）+
 * 相关技能必被召回 + 无相关性信号时保守回退全量。
 */
class SkillToolServiceRelevanceTest {

    private Skill skill(String name, String title, String desc) {
        Skill s = new Skill();
        s.setName(name);
        s.setTitle(title);
        s.setDescription(desc);
        return s;
    }

    private List<Skill> manySkills() {
        List<Skill> skills = new ArrayList<>();
        // 2 个与任务相关的技能
        skills.add(skill("pdf-summary", "PDF 总结", "对上传的 PDF 文档生成摘要总结"));
        skills.add(skill("doc-translator", "文档翻译", "把文档内容翻译成目标语言 (translate documents)"));
        // 10 个无关技能
        for (int i = 0; i < 10; i++) {
            skills.add(skill("skill-noise-" + i, "噪音技能" + i, "与任务完全无关的技能描述" + i));
        }
        return skills;
    }

    @Test
    void relatedSkillsAlwaysRecalled() {
        SkillToolService service = new SkillToolService();
        List<Skill> selected = service.selectRelevantSkills(manySkills(), "帮我总结这份 PDF 报告");
        List<String> names = selected.stream().map(Skill::getName).toList();
        assertTrue(names.contains("pdf-summary"), "相关技能必须被召回");
    }

    @Test
    void injectionCountDropsWithTopK() {
        // 12 个技能 > 默认 topK=8 → 注入数量下降（= prompt token 下降的直接证据）
        SkillToolService service = new SkillToolService();
        List<Skill> selected = service.selectRelevantSkills(manySkills(), "帮我总结这份 PDF 报告");
        assertEquals(8, selected.size(), "注入数量必须收敛到 topK");

        SkillToolSpi.SkillBundle bundle = service.prepare(manySkills(), "帮我总结这份 PDF 报告");
        assertEquals(8, bundle.skills().size(), "manifest 注入的技能数应下降");
        assertFalse(bundle.manifest().contains("skill-noise-9"), "被裁掉的技能描述不应进 prompt");
        assertTrue(bundle.manifest().contains("pdf-summary"));
    }

    @Test
    void allZeroScoreFallsBackToFullInjection() {
        // 查询与所有技能都无重合 → 保守回退全量（防误伤召回）
        SkillToolService service = new SkillToolService();
        List<Skill> selected = service.selectRelevantSkills(manySkills(), "今天天气怎么样");
        assertEquals(12, selected.size());
    }

    @Test
    void smallSkillSetNotFiltered() {
        // 技能数 <= topK → 行为与全量注入一致
        SkillToolService service = new SkillToolService();
        List<Skill> few = List.of(skill("a", "A", "描述A"), skill("b", "B", "描述B"));
        assertSame(few, service.selectRelevantSkills(few, "任意任务"));
    }

    @Test
    void blankQueryNotFiltered() {
        SkillToolService service = new SkillToolService();
        List<Skill> skills = manySkills();
        assertSame(skills, service.selectRelevantSkills(skills, null));
        assertSame(skills, service.selectRelevantSkills(skills, "  "));
    }

    @Test
    void scoringWeightsAreDeterministic() {
        List<String> tokens = SkillToolService.tokenize("PDF 总结 translate");
        assertTrue(tokens.contains("pdf"));
        assertTrue(tokens.contains("translate"));
        assertTrue(tokens.contains("总结"));

        Skill pdfSkill = skill("pdf-summary", "PDF 总结", "对 PDF 生成摘要");
        Skill noise = skill("noise", "噪音", "无关描述");
        assertTrue(SkillToolService.scoreRelevance(pdfSkill, tokens) > SkillToolService.scoreRelevance(noise, tokens));
        assertEquals(0, SkillToolService.scoreRelevance(noise, tokens));
        assertEquals(0, SkillToolService.scoreRelevance(null, tokens));
    }

    @Test
    void englishQueryMatchesNameTokens() {
        SkillToolService service = new SkillToolService();
        List<Skill> selected = service.selectRelevantSkills(manySkills(), "please translate this document for me");
        List<String> names = selected.stream().map(Skill::getName).toList();
        assertTrue(names.contains("doc-translator"), "英文查询也必须召回相关技能");
    }
}
