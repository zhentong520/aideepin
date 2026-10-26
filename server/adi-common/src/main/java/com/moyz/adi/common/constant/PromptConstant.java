package com.moyz.adi.common.constant;

import dev.langchain4j.model.input.PromptTemplate;

/**
 * 提示词模板域常量（OPT-17 拆分：原 AdiConstant 顶层常量）。
 * <p>
 * {@code AdiConstant} 实现本接口，既有引用无需改动。
 */
public interface PromptConstant {

    /**
     * 可能的 extraInfo 如适用转音频的要求： 3. 回答的内容要尽量口语化，以方便将内容转成语音
     */
    PromptTemplate PROMPT_INFO_EXTRA_TEMPLATE = PromptTemplate.from("""
            ## 任务
            根据已知信息及用户的记忆，尽可能准确地回答用户的问题

            ## 要求
            1. 根据对用户的记忆，回答时可以适当调整语气和风格，以更符合用户的偏好
            2. 回答的内容不能让用户感知到已知信息的存在
            {{extraInfo}}

            ## 用户的问题
            {{question}}

            ## 已知信息
            {{information}}

            ## 关于用户的记忆
            {{memory}}

            ## 输出语言
            使用用户提问的语言进行内容输出
            """);

    PromptTemplate PROMPT_INFO_EXTRA_TEMPLATE_EN = PromptTemplate.from("""
            ## Task
            Answer the user's question as accurately as possible based on the known information and the user's memory

            ## Requirements
            1. Based on the user's memory, adjust the tone and style appropriately to better match the user's preferences
            2. The answer should not reveal the existence of the known information to the user
            {{extraInfo}}

            ## User's Question
            {{question}}

            ## Known Information
            {{information}}

            ## About the User's Memory
            {{memory}}

            ## Output Language
            Respond in the language of the user's question
            """);

    String PROMPT_EXTRA_AUDIO = "3. 回答的内容要尽量口语化，以方便将内容转成语音";
    String PROMPT_EXTRA_AUDIO_EN = "3. The response should be as colloquial as possible to facilitate converting the content to speech";
}
