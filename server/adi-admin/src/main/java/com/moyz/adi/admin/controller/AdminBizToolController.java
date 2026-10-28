package com.moyz.adi.admin.controller;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.interfaces.BizToolProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务插件目录（管理端）。
 * <p>
 * Lists every {@link BizToolProvider} Spring bean currently registered in the container.
 * This is the "plug" half of the plug-in mechanism: implement a provider, restart, and it
 * shows up here automatically — the admin agent page reads this list to render the
 * "business plugins" checkbox group. No registry file to maintain, no assembly change.
 * </p>
 */
@Tag(name = "业务插件目录 | Biz Tool Catalog", description = "容器内已注册的业务插件（BizToolProvider）动态枚举 | Dynamic catalog of registered BizToolProvider beans")
@RestController
@RequestMapping("/admin/biztool")
@AdminOnly
public class AdminBizToolController {

    @Autowired(required = false)
    private List<BizToolProvider> providers = List.of();

    @Operation(summary = "可用业务插件列表（code/title） | Available biz plugins (code/title)")
    @GetMapping("/list")
    public List<Map<String, String>> list() {
        return providers.stream()
                .map(p -> Map.of("code", p.code(), "title", p.title()))
                .toList();
    }
}
