package com.moyz.adi.common.workflow.node.classifier;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.moyz.adi.common.util.JsonUtil;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.util.List;

/**
 * 分类节点提示词。<br/>
 * <br/>
 * 参照 Jev 的 questions 写法做的两处关键调整：<br/>
 * 1. <b>判据与标识分离</b>：category_name 只是机器读取的 KEY，criteria 才是判断依据。
 * 原来只有 name，导致类别名被迫身兼两职，为了让模型看懂，编排者只能把判据塞进名字里，
 * 结果路由分支名变成了一长串病句。<br/>
 * 2. <b>不再要求抽取 keywords</b>：节点从不消费这个字段，纯属浪费 token、扩大解析面。<br/>
 * <br/>
 * 仍然要求 0-1 的 confidence，但要清楚它的来历：这是让模型自评的结果，
 * 不是训练校准出来的概率，只适合做粗筛。
 */
public class ClassifierPrompt {

    public static String createPrompt(String input, List<ClassifierCategory> categories) {
        List<PromptCategory> promptCategories = categories.stream().map(item -> {
            PromptCategory promptCategory = new PromptCategory();
            BeanUtils.copyProperties(item, promptCategory);
            promptCategory.setCriteria(item.effectiveDescription());
            return promptCategory;
        }).toList();
        return """
                ### Job
                You are a text classification engine. You decide which branch a given input should be routed to.
                ### Task
                Assign exactly ONE category to the input text.
                ### Constraint
                - You MUST choose from the provided categories only. Never invent a new category.
                - category_uuid MUST be copied verbatim from the categories list.
                - confidence is your own 0-1 judgement of how sure you are. It is a routing hint, not a probability of being correct.
                - Respond with a single JSON object ONLY. No markdown fence, no explanation, no extra text.
                ### Output format
                {"category_uuid": "<uuid copied verbatim>", "category_name": "<name>", "confidence": <0-1 number>}
                ### Example
                {"input_text": ["bad service, slow to bring the food"], "categories": [{"category_uuid": "80fb86a0","category_name": "Food Quality","criteria": "Complaints about the food itself"},{"category_uuid": "cc771f63","category_name": "Price","criteria": "Complaints about cost or billing"}]}
                {"category_uuid": "80fb86a0", "category_name": "Food Quality", "confidence": 0.72}
                ### User Input
                {"input_text": ["%s"], "categories": %s}
                ### Output
                """.formatted(input, JsonUtil.toJson(promptCategories));
    }

    @Data
    public static class PromptCategory {
        @JsonProperty("category_uuid")
        private String categoryUuid;
        @JsonProperty("category_name")
        private String categoryName;
        /**
         * 判据：什么情况归入本类。缺失时由 {@link ClassifierCategory#effectiveDescription()} 退回类别名。
         */
        private String criteria;
    }
}
