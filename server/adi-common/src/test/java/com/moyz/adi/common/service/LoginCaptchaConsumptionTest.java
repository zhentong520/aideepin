package com.moyz.adi.common.service;

import com.moyz.adi.common.exception.BaseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.MessageFormat;

import static com.moyz.adi.common.constant.RedisKeyConstant.AUTH_LOGIN_CAPTCHA_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录验证码一次性消费 + captchaId 格式校验的回归测试（2026-10-15）。
 *
 * <p>修复的漏洞：验证码比对成功后此前不删除 Redis 键——达到验证码门槛后，
 * 攻击者人工识别一次图片，1 小时过期窗口内可脚本化无限次试密码，
 * 验证码门槛形同虚设。修复后比对成功即删除（一次性消费）。
 *
 * <p>注：login 全流程依赖 lambdaQuery（mock 环境无法运行，见 FinetuneJobServiceOwnershipTest
 * 的同类说明），故直接测消费方法与格式校验方法本身。
 */
class LoginCaptchaConsumptionTest {

    private UserService userService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userService = new UserService();
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(userService, "stringRedisTemplate", redisTemplate);
    }

    private String captchaKey(String captchaId) {
        return MessageFormat.format(AUTH_LOGIN_CAPTCHA_ID, captchaId);
    }

    // ------------------------------------------------------------------
    // 一次性消费（核心回归）
    // ------------------------------------------------------------------

    @DisplayName("验证码比对成功后必须立即删除（一次性消费）")
    @Test
    void captchaConsumedOnSuccess() {
        String captchaId = "abc123def456ghi789jkl012mno345";
        when(valueOperations.get(captchaKey(captchaId))).thenReturn("aB3d");

        assertDoesNotThrow(() -> userService.consumeLoginCaptcha(captchaId, "Ab3D"));

        verify(redisTemplate).delete(captchaKey(captchaId));
    }

    @DisplayName("验证码错误时不删除（错误输入不能消费有效验证码）")
    @Test
    void wrongCodeDoesNotConsume() {
        String captchaId = "abc123def456ghi789jkl012mno345";
        when(valueOperations.get(captchaKey(captchaId))).thenReturn("aB3d");

        assertThrows(BaseException.class, () -> userService.consumeLoginCaptcha(captchaId, "wrong"));

        verify(redisTemplate, never()).delete(anyString());
    }

    @DisplayName("不存在的 captchaId 拒绝且不删除")
    @Test
    void unknownCaptchaIdRejected() {
        when(valueOperations.get(anyString())).thenReturn(null);

        assertThrows(BaseException.class,
                () -> userService.consumeLoginCaptcha("zzz123def456ghi789jkl012mno345", "aB3d"));

        verify(redisTemplate, never()).delete(anyString());
    }

    // ------------------------------------------------------------------
    // captchaId 格式（防 Redis key 注入）
    // ------------------------------------------------------------------

    @DisplayName("captchaId 非法字符/超长拒绝（会拼进 Redis key）")
    @ParameterizedTest(name = "{0} 应拒绝")
    @ValueSource(strings = {
            "abc",                                    // 过短
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",  // 过长(65)
            "has space1234567890123456789012345",     // 空格
            "with*star123456789012345678901234",      // 通配符
            "with{brace}1234567890123456789012",      // Redis key 花括号（hash tag）
            "with:colon1234567890123456789012345",    // 冒号可伪造命名空间
    })
    void maliciousCaptchaIdRejected(String captchaId) throws Exception {
        java.lang.reflect.Method m = UserService.class.getDeclaredMethod("assertSafeCaptchaId", String.class);
        m.setAccessible(true);
        assertThrows(Exception.class, () -> {
            try {
                m.invoke(userService, captchaId);
            } catch (java.lang.reflect.InvocationTargetException te) {
                throw te.getCause();
            }
        }, captchaId + " 不应被拼进 Redis key");
    }

    @DisplayName("合法 captchaId 放行（前端 uuid 形态）")
    @Test
    void validCaptchaIdAllowed() throws Exception {
        java.lang.reflect.Method m = UserService.class.getDeclaredMethod("assertSafeCaptchaId", String.class);
        m.setAccessible(true);
        assertDoesNotThrow(() -> m.invoke(userService, "abc123def456ghi789jkl012mno345"));
        assertDoesNotThrow(() -> m.invoke(userService, "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6"));
        // 空值放行：未到验证码门槛时允许为空
        assertDoesNotThrow(() -> m.invoke(userService, (Object) null));
        assertDoesNotThrow(() -> m.invoke(userService, ""));
    }
}
