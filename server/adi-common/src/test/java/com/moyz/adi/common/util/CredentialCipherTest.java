package com.moyz.adi.common.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T2-07 单测：CredentialCipher（AES-256-GCM）核心行为。
 * 断言真实性约定（铁律 #6）：往返断言解密结果等于原文；错钥断言真实抛异常；
 * 断言同一明文两次加密密文不同（随机 IV 语义安全）。
 */
class CredentialCipherTest {

    /** 32 字节随机密钥的 Base64（每个测试类固定一份） */
    private static final String KEY_A = Base64.getEncoder().encodeToString(new byte[]{
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
            17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32});
    private static final String KEY_B = Base64.getEncoder().encodeToString(new byte[]{
            32, 31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17,
            16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1});

    @BeforeEach
    void setUp() {
        CredentialCipher.reset();
        CredentialCipher.init(KEY_A);
    }

    @AfterEach
    void tearDown() {
        CredentialCipher.reset();
    }

    @Test
    void encryptDecryptRoundTrip() {
        String plain = "smtp-password-密码-123!@#";
        CredentialCipher.CipherResult result = CredentialCipher.encrypt(plain);
        assertEquals(CredentialCipher.KEY_VERSION, result.keyVersion());
        String decrypted = CredentialCipher.decrypt(result.cipherText(), result.iv());
        assertEquals(plain, decrypted);
    }

    @Test
    void roundTripEmptyAndLongText() {
        String longText = "x".repeat(10_000);
        CredentialCipher.CipherResult r1 = CredentialCipher.encrypt("");
        assertEquals("", CredentialCipher.decrypt(r1.cipherText(), r1.iv()));
        CredentialCipher.CipherResult r2 = CredentialCipher.encrypt(longText);
        assertEquals(longText, CredentialCipher.decrypt(r2.cipherText(), r2.iv()));
    }

    @Test
    void randomIvMakesCiphertextNonDeterministic() {
        Set<String> cipherTexts = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            cipherTexts.add(CredentialCipher.encrypt("same-plain").cipherText());
        }
        // 8 次加密同一明文，密文互不相同（随机 IV）
        assertEquals(8, cipherTexts.size());
    }

    @Test
    void wrongKeyDecryptFails() {
        CredentialCipher.CipherResult result = CredentialCipher.encrypt("secret-value");
        // 换一把错误密钥 → GCM 认证失败必须抛异常（不能静默返回错值）
        CredentialCipher.reset();
        CredentialCipher.init(KEY_B);
        assertThrows(IllegalStateException.class,
                () -> CredentialCipher.decrypt(result.cipherText(), result.iv()));
    }

    @Test
    void tamperedCiphertextDecryptFails() {
        CredentialCipher.CipherResult result = CredentialCipher.encrypt("secret-value");
        byte[] bytes = Base64.getDecoder().decode(result.cipherText());
        bytes[0] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class,
                () -> CredentialCipher.decrypt(tampered, result.iv()));
    }

    @Test
    void initRejectsInvalidKey() {
        CredentialCipher.reset();
        // 非 Base64
        assertThrows(IllegalStateException.class, () -> CredentialCipher.init("not-base64!!!"));
        // Base64 但不是 32 字节
        assertThrows(IllegalStateException.class,
                () -> CredentialCipher.init(Base64.getEncoder().encodeToString(new byte[16])));
    }

    @Test
    void initFromEnvWithoutEnvGeneratesEphemeralKeyAndStillWorks() {
        CredentialCipher.reset();
        // 测试进程通常无 ADI_CRED_KEY；未配置时生成临时密钥（dev 行为），往返仍可用
        CredentialCipher.initFromEnv();
        assertTrue(CredentialCipher.isInitialized());
        CredentialCipher.CipherResult result = CredentialCipher.encrypt("roundtrip");
        assertEquals("roundtrip", CredentialCipher.decrypt(result.cipherText(), result.iv()));
    }

    @Test
    void ivIsStoredAndUsable() {
        CredentialCipher.CipherResult result = CredentialCipher.encrypt("v");
        byte[] iv = Base64.getDecoder().decode(result.iv());
        assertEquals(12, iv.length, "GCM nonce 必须是 12 字节");
        assertNotEquals(result.iv(), CredentialCipher.encrypt("v").iv());
    }
}
