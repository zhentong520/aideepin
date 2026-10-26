package com.moyz.adi.common.workflow;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum WfComponentNameEnum {
    START("Start"),

    END("End"),

    LLM_ANSWER("Answer"),

    OPENAI_IMAGE("OpenAiImage"),

    TONGYI_WANX("Tongyiwanx"),

    DOCUMENT_EXTRACTOR("DocumentExtractor"),

    KEYWORD_EXTRACTOR("KeywordExtractor"),

    FAQ_EXTRACTOR("FaqExtractor"),

    KNOWLEDGE_RETRIEVER("KnowledgeRetrieval"),

    SWITCHER("Switcher"),

    CLASSIFIER("Classifier"),

    TEMPLATE("Template"),

    GOOGLE_SEARCH("Google"),

    HUMAN_FEEDBACK("HumanFeedback"),

    MAIL_SEND("MailSend"),

    HTTP_REQUEST("HttpRequest"),

    AGENT("Agent"),

    DATABASE("Database"),

    MCP("Mcp"),

    SKILL("Skill"),

    ORCHESTRATOR("Orchestrator"),

    /** T9-02：循环节点（Each 逐项 / While 条件循环） */
    LOOP("Loop"),

    /** T9-03：并行节点（多分支并行 + join 汇合） */
    PARALLEL("Parallel"),

    /** T9-04：代码节点（复用 Skill 脚本沙箱） */
    CODE("Code"),

    /** T9-07：子工作流节点（引用链去重 + 深度上限） */
    SUB_WORKFLOW("SubWorkflow");

    private final String name;

    WfComponentNameEnum(String name) {
        this.name = name;
    }

    public static WfComponentNameEnum getByName(String name) {
        return Arrays.stream(WfComponentNameEnum.values()).filter(item -> item.name.equals(name)).findFirst().orElse(null);
    }
}
