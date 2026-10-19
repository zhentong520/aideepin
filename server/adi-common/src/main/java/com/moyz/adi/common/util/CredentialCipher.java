package com.moyz.adi.common.util;

import lombok.extern.slf4j.Slf4j;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 二开新增（T2-07，2026-10-19）：凭据集中管理加解密工具（AES-256-GCM）。
 * <p>
 * 主密钥【只】从环境变量 {@code ADI_CRED_KEY} 读取（Base64 编码的 32 字节密钥）：
 * <ul>
 *   <li>不入库、不入日志、不进配置文件</li>
 *   <li>生产环境必须显式配置——fail-fast 校验在 {@code Initializer.init()}（配置校验处）：
 *       prod profile 且未配置时启动直接失败</li>
 *   <li>dev 未配置时生成一次性随机密钥并 warn（仅本地可用，重启后历史密文不可解）</li>
 * </ul>
 * 加密输出三段：cipher_text（Base64 密文）+ iv（Base64，12 字节随机 GCM nonce）+
 * key_version（主密钥版本，默认 1，供后续轮换）。
 * <p>
 * 每次加密随机生成 IV，同一明文两次加密得到不同密文（语义安全）。
 */
@Slf4j
public class CredentialCipher {

    /** 主密钥环境变量名 */
    public static final String ENV_MASTER_KEY = "ADI_CRED_KEY";

    /** 当前主密钥版本（换主密钥时 +1，解密按存储行的 key_version 选择密钥） */
    public static final int KEY_VERSION = 1;

    private static final int KEY_BYTES = 32;      // AES-256
    private static final int IV_BYTES = 12;       // GCM 推荐 nonce 长度
    private static final int TAG_BITS = 128;      // GCM 认证标签长度

    private static final SecureRandom RANDOM = new SecureRandom();
    private static volatile SecretKeySpec masterKey;

    private CredentialCipher() {
    }

    /**
     * 加密结果三段式输出。
     */
    public record CipherResult(String cipherText, String iv, int keyVersion) {
    }

    /**
     * 用环境变量 {@code ADI_CRED_KEY} 初始化主密钥。
     * 未配置时生成随机密钥（仅 dev 可用，warn 提醒）；配置了但格式非法则抛异常（fail-fast）。
     * <p>
     * 由 {@code Initializer.init()} 在启动期调用；测试可传固定密钥。
     */
    public static synchronized void initFromEnv() {
        String env = System.getenv(ENV_MASTER_KEY);
        if (env == null || env.isBlank()) {
            byte[] random = new byte[KEY_BYTES];
            RANDOM.nextBytes(random);
            masterKey = toKey(random);
            Arrays.fill(random, (byte) 0);
            log.warn("[credential-vault] env {} not set; generated an EPHEMERAL random master key. " +
                            "Credentials encrypted in DB can NOT be decrypted after restart. " +
                            "This is dev-only convenience; production MUST set {} (Base64 of 32 random bytes).",
                    ENV_MASTER_KEY, ENV_MASTER_KEY);
            return;
        }
        init(env);
    }

    /**
     * 用显式 Base64 密钥初始化（32 字节）。格式非法直接抛异常。
     */
    public static synchronized void init(String base64Key) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("env " + ENV_MASTER_KEY + " is not valid Base64", e);
        }
        if (key.length != KEY_BYTES) {
            throw new IllegalStateException("env " + ENV_MASTER_KEY + " must decode to exactly " + KEY_BYTES
                    + " bytes (AES-256), got " + key.length + " bytes");
        }
        masterKey = toKey(key);
        Arrays.fill(key, (byte) 0);
        log.info("[credential-vault] master key loaded from env {}, key_version={}", ENV_MASTER_KEY, KEY_VERSION);
    }

    /**
     * 未显式初始化时兜底走 env（dev 场景首次调用即初始化）。
     */
    private static void ensureInit() {
        if (masterKey == null) {
            synchronized (CredentialCipher.class) {
                if (masterKey == null) {
                    initFromEnv();
                }
            }
        }
    }

    /**
     * 加密明文凭据。每次生成随机 12 字节 IV（GCM nonce）。
     */
    public static CipherResult encrypt(String plainText) {
        ensureInit();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return new CipherResult(
                    Base64.getEncoder().encodeToString(cipherBytes),
                    Base64.getEncoder().encodeToString(iv),
                    KEY_VERSION);
        } catch (Exception e) {
            // 不携带明文/密钥信息
            throw new IllegalStateException("credential encrypt failed", e);
        }
    }

    /**
     * 解密。密钥错误 / 密文被篡改时 GCM 认证失败抛 AEADBadTagException（包装后上抛）。
     */
    public static String decrypt(String cipherTextBase64, String ivBase64) {
        ensureInit();
        try {
            byte[] cipherBytes = Base64.getDecoder().decode(cipherTextBase64);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 不携带密文/密钥信息
            throw new IllegalStateException("credential decrypt failed (wrong key or tampered data?)", e);
        }
    }

    /**
     * 测试/内部用：当前主密钥是否已初始化。
     */
    public static synchronized boolean isInitialized() {
        return masterKey != null;
    }

    /**
     * 测试用：重置主密钥（切密钥场景模拟错误密钥解密失败）。
     */
    public static synchronized void reset() {
        masterKey = null;
    }

    private static SecretKeySpec toKey(byte[] key) {
        return new SecretKeySpec(key, "AES");
    }
}
