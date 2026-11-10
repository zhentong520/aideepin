package com.moyz.adi.common.dto;

import com.moyz.adi.common.annotation.AskReqCheck;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

@Schema(description = "对话的请求对象 | Character Request Object")
@Data
@AskReqCheck
public class AskReq {

    @Length(min = 32, max = 32)
    private String characterUuid;

    private String parentMessageId;

    private String prompt;

//Day
    //语音聊天时产生的音频文件uuid
    private String audioUuid;
//Day
    //语音聊天时产生的音频时长，单位秒
    private Integer audioDuration;

    /**
     * 图片地址，多模态LLM使用，目前只支持本地图片uuid
     */
    private List<String> imageUrls = List.of();

    /**
     * If not empty, it means will request AI with the exist prompt, param {@code prompt} is ignored
     */
    private String regenerateQuestionUuid;

    private String modelPlatform;
    private String modelName;

    /**
     * 业务编排 Harness M1（2026-10-30）：本次对话所在场景 uuid（可选）。
     * 场景页（ChatPage）发起对话时由前端携带；非空且灰度开关开启时，注入该场景绑定的
     * launch_workflow 工具。普通聊天为 null，行为与上线前完全一致。
     */
    private String sceneUuid;

//Backend temporary variable
    //后端用的临时变量
    private String processedPrompt;
}
