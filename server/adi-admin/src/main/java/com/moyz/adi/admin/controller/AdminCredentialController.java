package com.moyz.adi.admin.controller;

import com.moyz.adi.common.annotation.AdminOnly;
import com.moyz.adi.common.service.CredentialBackfillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 二开新增（T2-07，2026-10-19）：凭据集中管理-管理端入口。
 */
@Tag(name = "凭据管理 | Credential Management", description = "凭据集中管理 | Credential Management")
@RestController
@RequestMapping("/admin/credential")
@AdminOnly
public class AdminCredentialController {

    @Resource
    private CredentialBackfillService credentialBackfillService;

    @Operation(summary = "存量凭据回填 | Credential backfill",
            description = "扫描工作流节点配置 jsonb 中的敏感键值，加密写入 adi_credential，原字段置空 + 写 credential_uuid；" +
                    "幂等，复跑第二次影响 0 行；返回迁移节点数。需先开启 adi.credential.vault-enabled=true")
    @PostMapping("/backfill")
    public long backfill() {
        return credentialBackfillService.backfill();
    }
}
