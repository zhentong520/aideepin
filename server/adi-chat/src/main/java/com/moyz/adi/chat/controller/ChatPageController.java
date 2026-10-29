package com.moyz.adi.chat.controller;

import com.moyz.adi.common.entity.ChatPage;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.service.ChatPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端配置化聊天页面接口：**只读**（二开 P1）。
 * <p>
 * 页面由管理员在后台配置（见 AdminChatPageController），用户端只消费
 * 「启用页面列表 → 页面详情（schema + 场景绑定）→ 现有场景会话链路」。
 * </p>
 */
@Tag(name = "聊天页面（只读） | Chat Page (read-only)")
@RestController
@RequestMapping("/chatpage")
public class ChatPageController {

    @Resource
    private ChatPageService chatPageService;

    @Operation(summary = "启用的页面列表 | Enabled pages")
    @GetMapping("/list")
    public List<ChatPage> list() {
        return chatPageService.listEnabled();
    }

    @Operation(summary = "页面详情（含 schema 与场景绑定） | Page detail with schema and scene binding")
    @GetMapping("/{uuid}")
    public ChatPage detail(@PathVariable String uuid) {
        ChatPage page = chatPageService.getByUuid(uuid);
        // 停用页面对用户端不可见
        if (Boolean.FALSE.equals(page.getIsEnable())) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return page;
    }
}
