package com.moyz.adi.common.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.util.AesUtil;
import com.moyz.adi.common.util.CredentialCipher;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 二开新增（T2-07，2026-10-19）：存量凭据回填（T2-07 第 3 步）。
 * <p>
 * 扫描 {@code adi_workflow_node.node_config}（jsonb）中的敏感键值
 * （{@code mailPassword / mail_password / apiKey / api_key / secret / secretKey / secret_key / password}），
 * 逐条：AesUtil 解密（历史存储格式，失败按明文兜底）→ AES-256-GCM 加密写入 {@code adi_credential}
 * → node_config 原字段置空 + 写 credential_uuid。
 * <p>
 * 为什么是 service 而不是 SQL 迁移：jsonb 递归扫描可以做，但 AES 加密在 SQL 侧做不了，
 * 且历史值是 AesUtil（AES-CBC）密文，必须经 Java 解密。管理端触发：{@code POST /admin/credential/backfill}。
 * <p>
 * 幂等：候选集 WHERE credential_uuid = ''，已迁移行天然被排除，复跑第二次影响 0 行。
 * 一个节点存在多个敏感键时只迁移第一个（按发现顺序），其余 warn（当前实际 schema 只有邮件节点
 * sender.password 一个敏感键）。
 */
@Slf4j
@Service
public class CredentialBackfillService {

    /** 敏感键（小写匹配），覆盖任务书的 mailPassword / apiKey / secret / password 及常见变体 */
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "mailpassword", "mail_password",
            "apikey", "api_key",
            "secret", "secretkey", "secret_key");

    /** 单批扫描上限，防止一次性载入过多行（分批靠复跑推进，天然幂等） */
    private static final int BATCH_LIMIT = 500;

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 执行存量回填，返回迁移的节点数。幂等：第二次调用应返回 0。
     *
     * @throws IllegalStateException 开关未开启时拒绝执行（回填会置空旧字段，必须在读新机制开启后进行）
     */
    public long backfill() {
        if (!adiProperties.getCredential().isVaultEnabled()) {
            throw new IllegalStateException(
                    "adi.credential.vault-enabled=false, refuse to backfill (backfill blanks legacy fields; enable the vault switch first)");
        }
        List<NodeRow> rows = jdbcTemplate.query(
                "SELECT id, node_config FROM adi_workflow_node WHERE is_deleted = false AND credential_uuid = '' LIMIT "
                        + BATCH_LIMIT,
                (rs, i) -> new NodeRow(rs.getLong("id"), rs.getString("node_config")));
        long migrated = 0;
        for (NodeRow row : rows) {
            if (migrateRow(row)) {
                migrated++;
            }
        }
        log.info("[credential-backfill] done, scanned:{} nodes, migrated:{}", rows.size(), migrated);
        return migrated;
    }

    /**
     * 迁移单个节点。无敏感键返回 false；有则加密入库、置空原字段、写 credential_uuid。
     */
    private boolean migrateRow(NodeRow row) {
        JsonNode config;
        try {
            config = JsonUtil.toJsonNode(row.nodeConfig());
        } catch (Exception e) {
            log.warn("[credential-backfill] node_config not valid json, skip node id:{}", row.id());
            return false;
        }
        if (null == config || !config.isObject()) {
            return false;
        }
        List<SecretHit> hits = new ArrayList<>();
        collectSecrets((ObjectNode) config, new ArrayList<>(), hits);
        if (hits.isEmpty()) {
            return false;
        }
        if (hits.size() > 1) {
            log.warn("[credential-backfill] node id:{} has {} secret keys, only the first ({}) is migrated",
                    row.id(), hits.size(), hits.get(0).path());
        }
        SecretHit hit = hits.get(0);

        // 历史值是 AesUtil(AES-CBC) 密文；解不开则按明文兜底（更早期数据可能是明文）
        String plain = tryLegacyDecrypt(hit.value());

        CredentialCipher.CipherResult encrypted = CredentialCipher.encrypt(plain);
        String credentialUuid = UuidUtil.createShort();

        // 原字段置空（保留结构，便于回滚后可辨识），写入 credential_uuid
        ((ObjectNode) hit.parent()).put(hit.key(), "");

        jdbcTemplate.update(
                "INSERT INTO adi_credential (uuid, type, cipher_text, iv, key_version) VALUES (?, ?, ?, ?, ?)",
                credentialUuid, hit.type(), encrypted.cipherText(), encrypted.iv(), encrypted.keyVersion());
        jdbcTemplate.update(
                "UPDATE adi_workflow_node SET node_config = ?::jsonb, credential_uuid = ? WHERE id = ?",
                config.toString(), credentialUuid, row.id());

        // ⚠️ 日志只记录 uuid/type/键路径，绝不记录明文或密文
        log.info("[credential-backfill] node id:{} migrated, key path:{}, credential uuid:{}",
                row.id(), hit.path(), credentialUuid);
        return true;
    }

    /**
     * 尝试按历史 AesUtil 密文解密；失败按明文兜底（不抛错、不落日志内容）。
     */
    private String tryLegacyDecrypt(String value) {
        try {
            return AesUtil.decrypt(value);
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * 递归收集敏感键。只收集字符串值；空串跳过（已是置空状态）。
     */
    private void collectSecrets(ObjectNode node, List<String> path, List<SecretHit> hits) {
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            if (value.isObject()) {
                path.add(key);
                collectSecrets((ObjectNode) value, path, hits);
                path.remove(path.size() - 1);
            } else if (value.isTextual()) {
                String lower = key.toLowerCase(Locale.ROOT);
                if (SECRET_KEYS.contains(lower) && StringUtils.isNotBlank(value.asText())) {
                    List<String> fullPath = new ArrayList<>(path);
                    fullPath.add(key);
                    hits.add(new SecretHit(node, key, value.asText(), String.join(".", fullPath)));
                }
            } else if (value.isArray()) {
                ArrayNode array = (ArrayNode) value;
                for (int i = 0; i < array.size(); i++) {
                    JsonNode item = array.get(i);
                    if (item.isObject()) {
                        path.add(key);
                        collectSecrets((ObjectNode) item, path, hits);
                        path.remove(path.size() - 1);
                    }
                }
            }
        }
    }

    /**
     * 敏感键命中：所在对象、键名、原始值、完整路径。
     */
    private record SecretHit(ObjectNode parent, String key, String value, String path) {
        String type() {
            return key.toLowerCase(Locale.ROOT);
        }
    }

    private record NodeRow(long id, String nodeConfig) {
    }
}
