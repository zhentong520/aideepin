package com.moyz.adi.common.service;

import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.text.MessageFormat;
import java.util.Set;

import static com.moyz.adi.common.constant.RedisKeyConstant.FIND_MY_PASSWORD;
import static com.moyz.adi.common.constant.RedisKeyConstant.USER_TOKEN;
import static com.moyz.adi.common.constant.RedisKeyConstant.USER_TOKEN_SET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * S-07/S-08 单测：找回密码流程重构后的核心行为。
 * <p>
 * 断言真实性约定（铁律 #6）：每个用例都断言「异常错误码」或「对 Redis/Mapper 的真实交互」，
 * 不用恒真断言。不依赖 Spring 容器与真实数据库（与 KnowledgeBaseServiceAuthorizationTest 同风格）。
 */
class UserServicePasswordResetTest {

    private UserService userService;
    private UserMapper userMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private SetOperations<String, String> setOperations;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userService = new UserService();
        userMapper = mock(UserMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        setOperations = mock(SetOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
        ReflectionTestUtils.setField(userService, "stringRedisTemplate", stringRedisTemplate);
    }

    // ---------- verifyResetCode ----------

    @Test
    void verifyResetCodeRejectsInvalidCode() {
        when(valueOperations.get(anyString())).thenReturn(null);

        BaseException exception = assertThrows(BaseException.class,
                () -> userService.verifyResetCode("bad-code"));

        assertEquals(ErrorEnum.A_FIND_PASSWORD_CODE_ERROR.getCode(), exception.getCode());
    }

    @Test
    void verifyResetCodeReturnsUserIdForValidCode() {
        when(valueOperations.get(MessageFormat.format(FIND_MY_PASSWORD, "good-code"))).thenReturn("42");

        assertEquals(42L, userService.verifyResetCode("good-code"));
    }

    // ---------- resetPasswordByCode ----------

    @Test
    void resetPasswordByCodeRejectsWeakPasswordWithoutTouchingRedisOrDb() {
        // 弱密码在入口即被拒：code 不消费、口令不落库、会话不清理
        BaseException exception = assertThrows(BaseException.class,
                () -> userService.resetPasswordByCode("any-code", "12345"));

        assertEquals(ErrorEnum.A_PARAMS_ERROR.getCode(), exception.getCode());
        verify(stringRedisTemplate, never()).delete(anyString());
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void resetPasswordByCodeConsumesCodeWritesHashAndKicksAllSessions() {
        String code = "good-code";
        String codeKey = MessageFormat.format(FIND_MY_PASSWORD, code);
        when(valueOperations.get(codeKey)).thenReturn("42");
        String setKey = MessageFormat.format(USER_TOKEN_SET, 42L);
        when(setOperations.members(setKey)).thenReturn(Set.of("tok-a", "tok-b"));

        userService.resetPasswordByCode(code, "new-secure-password");

        // 1) 口令已加密落库（S-07：不再出现明文默认口令）
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        assertEquals(42L, captor.getValue().getId());
        String hashed = captor.getValue().getPassword();
        assertTrue(hashed != null && hashed.startsWith("$2")
                        && !hashed.contains("123456") && !hashed.equals("new-secure-password"),
                "must store a bcrypt hash, never the plain password");

        // 2) code 一次性消费
        verify(stringRedisTemplate).delete(codeKey);

        // 3) 全端踢会话：逐一删除集合内的 token 并清掉集合本身（S-08③）
        verify(stringRedisTemplate).delete(MessageFormat.format(USER_TOKEN, "tok-a"));
        verify(stringRedisTemplate).delete(MessageFormat.format(USER_TOKEN, "tok-b"));
        verify(stringRedisTemplate).delete(setKey);
    }

    @Test
    void resetPasswordByCodeRejectsInvalidCode() {
        when(valueOperations.get(anyString())).thenReturn(null);

        BaseException exception = assertThrows(BaseException.class,
                () -> userService.resetPasswordByCode("expired-code", "valid-password-1"));

        assertEquals(ErrorEnum.A_FIND_PASSWORD_CODE_ERROR.getCode(), exception.getCode());
        verify(userMapper, never()).updateById(any(User.class));
    }

    // ---------- assertPasswordStrength（包级反射访问）----------

    @Test
    void passwordStrengthBoundary() {
        // 6 位下限与 64 位上限均合法，越界拒绝
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(userService,
                "assertPasswordStrength", "123456");
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(userService,
                "assertPasswordStrength", "a".repeat(64));

        assertThrows(BaseException.class, () -> org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(userService, "assertPasswordStrength", "12345"));
        assertThrows(BaseException.class, () -> org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(userService, "assertPasswordStrength", (Object) null));
        assertThrows(BaseException.class, () -> org.springframework.test.util.ReflectionTestUtils
                .invokeMethod(userService, "assertPasswordStrength", "a".repeat(65)));
    }

    // ---------- forgotPassword：cooldown（S-08①）----------

    @Test
    void forgotPasswordIsSilentlySkippedDuringCooldown() {
        when(stringRedisTemplate.hasKey(anyString())).thenReturn(true);

        // 冷却期内静默返回：不查库、不发信、不抛异常（防枚举 + 防轰炸）
        userService.forgotPassword("victim@example.com");

        verify(userMapper, never()).selectOne(any());
        Mockito.verifyNoInteractions(setOperations);
    }
}
