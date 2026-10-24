package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.mapper.CredentialMapper;
import com.moyz.adi.common.util.AesUtil;
import com.moyz.adi.common.util.CredentialCipher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T2-07 单测：CredentialVaultService 开关两态 + CredentialBackfillService 回填幂等（mock 依赖，
 * 不起 Spring、不连库，与 UserServicePasswordResetTest 同风格）。
 * 断言真实性约定（铁律 #6）：断言解密往返结果、SQL 参数捕获、幂等复跑返回 0。
 */
class CredentialVaultAndBackfillTest {

    private static final String MASTER_KEY = java.util.Base64.getEncoder().encodeToString(new byte[]{
            9, 8, 7, 6, 5, 4, 3, 2, 1, 12, 13, 14, 15, 16, 17, 18,
            19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34});

    // ---------- CredentialVaultService（开关两态） ----------

    private CredentialVaultService vaultService;
    private AdiProperties adiProperties;
    private CredentialMapper credentialMapper;

    @BeforeEach
    void setUp() {
        CredentialCipher.reset();
        CredentialCipher.init(MASTER_KEY);
        AesUtil.AES_KEY = "0123456789abcdef";

        vaultService = new CredentialVaultService();
        adiProperties = new AdiProperties();
        credentialMapper = mock(CredentialMapper.class);
        ReflectionTestUtils.setField(vaultService, "adiProperties", adiProperties);
        ReflectionTestUtils.setField(vaultService, "credentialMapper", credentialMapper);
    }

    @AfterEach
    void tearDown() {
        CredentialCipher.reset();
        AesUtil.AES_KEY = "";
    }

    @Test
    void storeAndResolveRoundTrip() {
        adiProperties.getCredential().setVaultEnabled(true);
        Mockito.when(credentialMapper.insert(any(com.moyz.adi.common.entity.Credential.class)))
                .thenAnswer(inv -> {
                    com.moyz.adi.common.entity.Credential c = inv.getArgument(0);
                    c.setId(1L);
                    return 1;
                });

        String uuid = vaultService.store("mail_password", "plain-secret-密码");
        assertTrue(uuid != null && uuid.length() == 32);

        // 捕获落库实体：断言不存明文、cipher+iv 可解回原文
        ArgumentCaptor<com.moyz.adi.common.entity.Credential> captor =
                ArgumentCaptor.forClass(com.moyz.adi.common.entity.Credential.class);
        verify(credentialMapper).insert(captor.capture());
        assertEquals("mail_password", captor.getValue().getType());
        assertTrue(!captor.getValue().getCipherText().contains("plain-secret"));

        // resolve 走 selectOne（mock 返回落库实体）
        when(credentialMapper.selectOne(any())).thenReturn(captor.getValue());
        assertEquals("plain-secret-密码", vaultService.resolve(uuid));
    }

    @Test
    void resolveMailPasswordSwitchOffUsesLegacyField() {
        adiProperties.getCredential().setVaultEnabled(false);
        WorkflowNode node = new WorkflowNode();
        node.setCredentialUuid(""); // 未迁移

        String legacyAes = AesUtil.encrypt("legacy-plain");
        assertEquals("legacy-plain", vaultService.resolveMailPassword(node, legacyAes));
    }

    @Test
    void resolveMailPasswordSwitchOnPrefersVault() {
        adiProperties.getCredential().setVaultEnabled(true);
        Mockito.when(credentialMapper.insert(any(com.moyz.adi.common.entity.Credential.class)))
                .thenReturn(1);

        WorkflowNode node = new WorkflowNode();
        node.setCredentialUuid(vaultService.store("mail_password", "vault-plain"));

        // resolve() 按 uuid 查库 → 返回落库实体
        ArgumentCaptor<com.moyz.adi.common.entity.Credential> stored =
                ArgumentCaptor.forClass(com.moyz.adi.common.entity.Credential.class);
        verify(credentialMapper).insert(stored.capture());
        when(credentialMapper.selectOne(any())).thenReturn(stored.getValue());

        // 即便 legacy 字段仍有旧密文，开关开启 + credential_uuid 在场 → 必须走凭据库
        String legacyAes = AesUtil.encrypt("legacy-plain");
        assertEquals("vault-plain", vaultService.resolveMailPassword(node, legacyAes));
    }

    @Test
    void resolveMailPasswordSwitchOnWithoutUuidFallsBackToLegacy() {
        adiProperties.getCredential().setVaultEnabled(true);
        WorkflowNode node = new WorkflowNode();
        node.setCredentialUuid("");

        String legacyAes = AesUtil.encrypt("fallback-plain");
        assertEquals("fallback-plain", vaultService.resolveMailPassword(node, legacyAes));
    }

    @Test
    void resolveUnknownCredentialThrows() {
        adiProperties.getCredential().setVaultEnabled(true);
        when(credentialMapper.selectOne(any())).thenReturn(null);
        assertThrows(IllegalStateException.class, () -> vaultService.resolve("nonexistent"));
    }

    // ---------- CredentialBackfillService（回填幂等，mock JdbcTemplate） ----------

    private CredentialBackfillService backfillService;
    private JdbcTemplate jdbcTemplate;
    private AdiProperties backfillProps;

    private CredentialBackfillService newBackfillService() {
        CredentialBackfillService service = new CredentialBackfillService();
        backfillProps = new AdiProperties();
        jdbcTemplate = mock(JdbcTemplate.class);
        ReflectionTestUtils.setField(service, "adiProperties", backfillProps);
        ReflectionTestUtils.setField(service, "jdbcTemplate", jdbcTemplate);
        return service;
    }

    @SuppressWarnings("unchecked")
    private void mockScan(String... nodeConfigs) {
        // jdbcTemplate.query(sql, RowMapper) 按行回调 RowMapper
        when(jdbcTemplate.query(contains("adi_workflow_node"), any(RowMapper.class)))
                .thenAnswer(inv -> {
                    RowMapper<Object> rm = (RowMapper<Object>) inv.getArgument(1);
                    java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                    List<Object> result = new java.util.ArrayList<>();
                    for (int i = 0; i < nodeConfigs.length; i++) {
                        long id = i + 1L;
                        when(rs.getLong("id")).thenReturn(id);
                        when(rs.getString("node_config")).thenReturn(nodeConfigs[i]);
                        result.add(rm.mapRow(rs, i));
                    }
                    return result;
                });
    }

    @Test
    void backfillRefusesWhenSwitchOff() {
        CredentialBackfillService service = newBackfillService();
        backfillProps.getCredential().setVaultEnabled(false);
        assertThrows(IllegalStateException.class, service::backfill);
    }

    @Test
    void backfillMigratesMailPasswordAndBlanksField() {
        CredentialBackfillService service = newBackfillService();
        backfillProps.getCredential().setVaultEnabled(true);
        // ⚠️ 用 ASCII 明文：AesUtil.decrypt(String) 用平台默认字符集（Windows GBK），
        // 非 ASCII 往返有损是既有存量行为，不属于 T2-07 修复范围
        String plain = "smtp-pwd-ascii-123";
        String legacy = AesUtil.encrypt(plain);
        mockScan("{\"sender_type\":2,\"sender\":{\"name\":\"a\",\"mail\":\"a@b.c\",\"password\":\"" + legacy + "\"}}");

        long migrated = service.backfill();
        assertEquals(1, migrated);

        // INSERT 凭据：捕获参数，断言密文可解回原文、不含明文
        ArgumentCaptor<Object[]> insertCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO adi_credential"), insertCaptor.capture());
        Object[] insertArgs = insertCaptor.getValue();
        assertEquals("password", insertArgs[1]); // type = jsonb 键名小写
        String cipherText = (String) insertArgs[2];
        String iv = (String) insertArgs[3];
        assertTrue(!cipherText.contains(plain) && !cipherText.contains(legacy));
        assertEquals(plain, CredentialCipher.decrypt(cipherText, iv));

        // UPDATE 节点：node_config 原字段置空 + credential_uuid 写入
        ArgumentCaptor<Object[]> updateCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("UPDATE adi_workflow_node"), updateCaptor.capture());
        Object[] updateArgs = updateCaptor.getValue();
        assertTrue(((String) updateArgs[0]).contains("\"password\":\"\""));
        assertEquals(32, ((String) updateArgs[1]).length());
    }

    @Test
    void backfillHandlesPlainValueFallback() {
        CredentialBackfillService service = newBackfillService();
        backfillProps.getCredential().setVaultEnabled(true);
        // 历史明文（AesUtil 解不开）→ 按明文兜底
        mockScan("{\"api_key\":\"sk-plain-key\"}");

        assertEquals(1, service.backfill());
        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO adi_credential"), captor.capture());
        assertEquals("api_key", captor.getValue()[1]);
        assertEquals("sk-plain-key",
                CredentialCipher.decrypt((String) captor.getValue()[2], (String) captor.getValue()[3]));
    }

    @Test
    void backfillSkipsNodesWithoutSecrets() {
        CredentialBackfillService service = newBackfillService();
        backfillProps.getCredential().setVaultEnabled(true);
        mockScan("{\"subject\":\"hello\"}", "{}");

        assertEquals(0, service.backfill());
        verify(jdbcTemplate, never()).update(contains("INSERT INTO adi_credential"), any(Object[].class));
        verify(jdbcTemplate, never()).update(contains("UPDATE adi_workflow_node"), any(Object[].class));
    }

    @Test
    void backfillIsIdempotent() {
        CredentialBackfillService service = newBackfillService();
        backfillProps.getCredential().setVaultEnabled(true);
        // 第一轮：1 个未迁移节点 → 迁移 1 行
        mockScan("{\"sender\":{\"password\":\"" + AesUtil.encrypt("p1") + "\"}}");
        assertEquals(1, service.backfill());
        // 第二轮复跑：credential_uuid 已写入 → 候选集为空 → 0 行（真实 WHERE 语义由 mock 模拟）
        mockScan();
        assertEquals(0, service.backfill());
    }

    @Test
    void backfillBlankValueIsSkipped() {
        CredentialBackfillService service = newBackfillService();
        backfillProps.getCredential().setVaultEnabled(true);
        // 已置空的字段（回填过的结构）不算敏感命中
        mockScan("{\"sender\":{\"password\":\"\"}}");
        assertEquals(0, service.backfill());
    }
}
