package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.config.props.Encrypt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1-06（2026-10-27）：默认 AES key 启动拦截回归。
 *
 * <p>{@link Initializer#init()} 的第一道门必须是密钥校验：沿用 yml 内置默认值
 * {@code Ap9da0CopbjiKGc1} 启动会直接 fail-fast（敏感字段用公开密钥加密等于没加密）。
 *
 * <p>无容器测试：只手动注入 {@code adiProperties}。默认密钥场景下校验发生在
 * init() 其余步骤之前，不触碰任何其他依赖；自定义密钥场景通过异常类型与
 * NPE 区分「已通过密钥门、死在后续未注入依赖」。
 */
class InitializerDefaultAesKeyTest {

    private static void setAdiProperties(Initializer initializer, AdiProperties props) throws Exception {
        Field f = Initializer.class.getDeclaredField("adiProperties");
        f.setAccessible(true);
        f.set(initializer, props);
    }

    private static AdiProperties propsWithKey(String key) {
        AdiProperties props = new AdiProperties();
        props.setEncrypt(new Encrypt());
        props.getEncrypt().setAesKey(key);
        return props;
    }

    @DisplayName("默认 AES key 启动必须被拦截（fail-fast）")
    @Test
    void defaultAesKeyRefusesStartup() throws Exception {
        Initializer initializer = new Initializer();
        setAdiProperties(initializer, propsWithKey(Initializer.DEFAULT_AES_KEY));

        RuntimeException ex = assertThrows(RuntimeException.class, initializer::init);
        assertTrue(ex.getMessage().contains("default AES key"),
                "异常信息应指明默认密钥不可用，实际:" + ex.getMessage());
    }

    @DisplayName("自定义 AES key 通过密钥门（后续 NPE 仅因无容器依赖，非密钥拦截）")
    @Test
    void customAesKeyPassesKeyGate() throws Exception {
        Initializer initializer = new Initializer();
        setAdiProperties(initializer, propsWithKey("0123456789abcdef"));

        Exception ex = assertThrows(Exception.class, initializer::init);
        assertFalse(ex.getMessage() != null && ex.getMessage().contains("default AES key"),
                "自定义密钥不应被密钥门拦截");
    }

    @DisplayName("yml 内置默认值与 Initializer.DEFAULT_AES_KEY 必须一致（防两处漂移）")
    @Test
    void ymlDefaultKeyMatchesConstant() throws Exception {
        // 直接读 classpath 的 application.yml，锁死「改一处必须同步另一处」
        String yml;
        try (var in = getClass().getResourceAsStream("/application.yml")) {
            assertTrue(in != null, "classpath 找不到 application.yml");
            yml = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        // adi.encrypt.aes-key 的 env 默认值即内置默认密钥
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("aes-key:\\s*\\$\\{ADI_ENCRYPT_AES_KEY:([^}]+)}")
                .matcher(yml);
        assertTrue(m.find(), "application.yml 未找到 adi.encrypt.aes-key 的默认值写法");
        assertTrue(yml.contains(Initializer.DEFAULT_AES_KEY),
                "yml 内置默认密钥与 Initializer.DEFAULT_AES_KEY 漂移，改一处必须同步另一处");
        assertFalse(m.group(1).isBlank(), "默认密钥不应为空串");
    }
}
