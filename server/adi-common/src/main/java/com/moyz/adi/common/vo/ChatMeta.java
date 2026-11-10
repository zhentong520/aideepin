package com.moyz.adi.common.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatMeta {
    private PromptMeta question;
    private AnswerMeta answer;
    private AudioInfo audioInfo;
    /**
     * 业务编排 Harness M1（2026-10-30）：本次提问关联的工作流运行 uuid（逗号分隔多值，可为 null）。
     * 前端拿到后在本会话内即时渲染工作流卡片，无需刷新页面。
     */
    private String wfRuntimeUuid;
}
