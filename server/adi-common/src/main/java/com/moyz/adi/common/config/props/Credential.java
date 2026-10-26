package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * 凭据集中管理配置（adi.credential.*，T2-07）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Credential} 嵌套类提为顶层类，Spring 绑定键不变。
 * 注意与实体类 {@code com.moyz.adi.common.entity.Credential} 同名但职责无关。
 */
@Data
public class Credential {

    /**
     * 凭据库开关（adi.credential.vault-enabled，默认 false）。
     * <p>
     * false 时全部读写路径走旧机制（node_config jsonb 内 AesUtil 加密字段），行为与历史版本一致；
     * true 时：保存节点把明文凭据加密进 adi_credential（jsonb 原字段置空 + 写 credential_uuid），
     * 节点执行/响应体按 credential_uuid 解密取值，日志与响应一律不出现明文。
     * <p>
     * ⚠️ 开启前先跑管理端回填（POST /admin/credential/backfill）迁移存量；
     * ⚠️ 生产环境必须配置环境变量 ADI_CRED_KEY（Base64 32 字节），否则启动 fail-fast。
     * <p>
     * Vault switch. Disabled by default: read/write paths keep the legacy mechanism
     * (AesUtil-encrypted fields inside node_config jsonb). When enabled, secrets are stored
     * in adi_credential (AES-256-GCM) and referenced by credential_uuid. Run the admin
     * backfill endpoint before enabling; production must set env ADI_CRED_KEY or startup fails.
     */
    private boolean vaultEnabled = false;
}
