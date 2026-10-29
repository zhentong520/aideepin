package com.moyz.adi.admin.controller;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.entity.ChatPage;
import com.moyz.adi.common.service.ChatPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配置化聊天页面管理（管理端，二开 P1）。
 * <p>
 * 页面 = name + page_schema（区块数组）+ 场景绑定。数据访问全部收敛到
 * {@link ChatPageService}（架构门禁 controllerMustNotTouchMapper）。
 * </p>
 */
@Tag(name = "聊天页面管理 | Chat Page Management", description = "配置化聊天页面（全局共享，用户端只读）")
@RestController
@RequestMapping("/admin/chatpage")
@AdminOnly
public class AdminChatPageController {

    @Resource
    private ChatPageService chatPageService;

    @Operation(summary = "页面列表（含停用） | List pages, including disabled")
    @GetMapping("/list")
    public List<ChatPage> list() {
        return chatPageService.listAll();
    }

    @Operation(summary = "新增或更新页面 | Create or update page")
    @PostMapping("/saveOrUpdate")
    public ChatPage saveOrUpdate(@RequestBody ChatPage page) {
        return chatPageService.saveOrUpdatePage(page);
    }

    @Operation(summary = "启用/停用页面 | Enable or disable page")
    @PostMapping("/toggle/{uuid}")
    public Boolean toggle(@PathVariable String uuid, @RequestParam Boolean isEnable) {
        return chatPageService.toggle(uuid, isEnable);
    }

    @Operation(summary = "删除页面 | Delete page")
    @PostMapping("/delete")
    public Boolean delete(@RequestParam String uuid) {
        chatPageService.softDelete(uuid);
        return true;
    }
}
