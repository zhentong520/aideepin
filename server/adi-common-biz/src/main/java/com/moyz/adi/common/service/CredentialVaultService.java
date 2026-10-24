package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.Credential;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.mapper.CredentialMapper;
import com.moyz.adi.common.util.AesUtil;
import com.moyz.adi.common.util.CredentialCipher;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 二开新增（T2-07，2026-10-19）：凭据存取服务（vault 读写门面）。
 * <p>
 * 开关语义（铁律 10，{@code adi.credential.vault-enabled}，默认 false）：
 * <ul>
 *   <li>false：读取路径走旧机制（node_config jsonb 内 AesUtil 加密字段），行为与历史版本完全一致，可回滚</li>
 *   <li>true：读取路径优先按 credential_uuid 从 adi_credential 解密；写入路径把明文加密入库并置空 jsonb 原字段</li>
 * </ul>
 * 明文只存在于调用栈内存中：日志、异常、响应体一律不出现明文（调用方约定：resolve 结果不落日志）。
 */
@Slf4j
@Service
public class CredentialVaultService {

    /** 凭据类型：邮件节点发件人密码 */
    public static final String TYPE_MAIL_PASSWORD = "mail_password";

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private CredentialMapper credentialMapper;

    /**
     * 开关是否打开。
     */
    public boolean enabled() {
        return adiProperties.getCredential().isVaultEnabled();
    }

    /**
     * 加密并保存一条凭据，返回 credential_uuid（供业务表引用）。
     * 仅在 vault-enabled 时由写入路径调用。
     */
    public String store(String type, String plainText) {
        CredentialCipher.CipherResult result = CredentialCipher.encrypt(plainText);
        Credential credential = new Credential();
        credential.setUuid(UuidUtil.createShort());
        credential.setType(StringUtils.defaultString(type));
        credential.setCipherText(result.cipherText());
        credential.setIv(result.iv());
        credential.setKeyVersion(result.keyVersion());
        credentialMapper.insert(credential);
        log.info("[credential-vault] stored credential, uuid:{}, type:{}", credential.getUuid(), type);
        return credential.getUuid();
    }

    /**
     * 按 credential_uuid 解密取回明文。找不到或解密失败抛异常（调用方转业务错误，不回退明文）。
     */
    public String resolve(String credentialUuid) {
        Credential credential = credentialMapper.selectOne(
                Wrappers.<Credential>lambdaQuery().eq(Credential::getUuid, credentialUuid).last("limit 1"));
        if (null == credential) {
            log.error("[credential-vault] credential not found, uuid:{}", credentialUuid);
            throw new IllegalStateException("credential not found: " + credentialUuid);
        }
        return CredentialCipher.decrypt(credential.getCipherText(), credential.getIv());
    }

    /**
     * 邮件节点发件人密码解析（开关两态）。
     * <p>
     * vault-enabled 且节点带 credential_uuid → 从凭据库解密；
     * 否则走旧机制：node_config 里的 AesUtil 密文字段解密（空值原样返回空，由调用方做必填校验）。
     *
     * @param node              运行时节点（可能为 null，如单测构造的场景）
     * @param legacyAesPassword node_config.sender.password 的历史 AesUtil 密文（可为空）
     * @return 明文密码（仅在内存中使用，禁止落日志）
     */
    public String resolveMailPassword(WorkflowNode node, String legacyAesPassword) {
        if (enabled() && null != node && StringUtils.isNotBlank(node.getCredentialUuid())) {
            return resolve(node.getCredentialUuid());
        }
        return StringUtils.isNotBlank(legacyAesPassword) ? AesUtil.decrypt(legacyAesPassword) : "";
    }
}
