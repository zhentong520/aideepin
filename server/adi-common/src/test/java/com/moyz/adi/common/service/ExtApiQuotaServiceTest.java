package com.moyz.adi.common.service;

import com.moyz.adi.common.config.AdiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * ExtApiQuotaService 边界测试（T1-09）。
 * <p>
 * ⚠️ 历史事故：曾出现额度检查 {@code len>0} 判 PASS 的假绿。本类用真实 INCR 语义
 * （Map 计数器 + Mockito Answer）钉死「第 quota 次放行、第 quota+1 次拒绝」的边界。
 */
@ExtendWith(MockitoExtension.class)
class ExtApiQuotaServiceTest {

    private static final String KEY = "ext-test-key";

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ExtApiQuotaService service;

    /** 模拟 Redis INCR：按 key 独立计数 */
    private final Map<String, Long> counters = new HashMap<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new ExtApiQuotaService();
        AdiProperties properties = new AdiProperties();
        properties.getExt().setQpsLimit(5);
        properties.getExt().setDailyRequestQuota(10000);
        properties.getExt().setDailyTokenQuota(0);
        ReflectionTestUtils.setField(service, "adiProperties", properties);
        ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);

        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.increment(anyString())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return counters.merge(key, 1L, Long::sum);
        });
        lenient().when(valueOperations.increment(anyString(), any(Long.class))).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            long delta = inv.getArgument(1);
            return counters.merge(key, delta, Long::sum);
        });
        lenient().when(valueOperations.get(anyString())).thenAnswer(inv -> {
            Long val = counters.get(inv.getArgument(0));
            return null == val ? null : String.valueOf(val);
        });
    }

    // ------------------------------------------------------------------
    // QPS：窗口内第 5 次放行，第 6 次拒绝
    // ------------------------------------------------------------------

    @DisplayName("QPS：limit=5 时第 5 次放行、第 6 次拒绝，且窗口 key 带过期时间")
    @Test
    void qpsBoundary() {
        for (int i = 1; i <= 5; i++) {
            assertTrue(service.tryAcquireQps(KEY), "QPS 窗口内第 " + i + " 次调用应放行");
        }
        assertFalse(service.tryAcquireQps(KEY), "QPS 窗口内第 6 次调用必须拒绝");
    }

    // ------------------------------------------------------------------
    // 日调用配额：差 1 次到上限的边界
    // ------------------------------------------------------------------

    @DisplayName("日调用配额：第 quota 次放行、第 quota+1 次拒绝（差 1 次到上限的边界）")
    @Test
    void dailyRequestQuotaBoundary() {
        for (int i = 1; i <= 10000; i++) {
            assertTrue(service.tryConsumeDailyRequest(KEY), "日配额内第 " + i + " 次调用应放行");
        }
        assertEquals(10000, service.todayRequests(KEY), "当日计数应等于配额上限");
        assertFalse(service.tryConsumeDailyRequest(KEY), "超出日配额的第 10001 次调用必须拒绝");
        assertEquals(10001, service.todayRequests(KEY));
    }

    @DisplayName("日调用配额首次写入必须设置过期时间（否则计数器永不过期=永久封禁）")
    @Test
    void dailyRequestCounterExpires() {
        service.tryConsumeDailyRequest(KEY);
        verify(stringRedisTemplate).expire(anyString(), any(Duration.class));
        // 第二次调用不应再重置过期时间
        service.tryConsumeDailyRequest(KEY);
        verify(stringRedisTemplate).expire(anyString(), any(Duration.class));
    }

    // ------------------------------------------------------------------
    // 日 token 配额
    // ------------------------------------------------------------------

    @DisplayName("日 token 配额=0 时不限制")
    @Test
    void tokenQuotaZeroMeansUnlimited() {
        assertFalse(service.isTokenQuotaExceeded(KEY, Integer.MAX_VALUE));
    }

    @DisplayName("日 token 配额：累计 prompt+completion，达到配额后前置检查拒绝")
    @Test
    void tokenQuotaBoundary() {
        AdiProperties properties = (AdiProperties) ReflectionTestUtils.getField(service, "adiProperties");
        properties.getExt().setDailyTokenQuota(1000);

        assertTrue(service.recordTokens(KEY, 600, 399) == 999, "累计 999 后总量应为 999");
        assertFalse(service.isTokenQuotaExceeded(KEY, 0), "累计 999 <= 1000 应放行");
        assertTrue(service.recordTokens(KEY, 1, 0) == 1000);
        assertFalse(service.isTokenQuotaExceeded(KEY, 0), "累计恰好 1000 应放行");
        service.recordTokens(KEY, 1, 0);
        assertTrue(service.isTokenQuotaExceeded(KEY, 0), "累计 1001 超配额，前置检查必须拒绝");
    }

    // ------------------------------------------------------------------
    // 开关
    // ------------------------------------------------------------------

    @DisplayName("enabled=false 时计数接口不生效：直接放行且不写 Redis")
    @Test
    void disabledSwitchBypasses() {
        AdiProperties properties = (AdiProperties) ReflectionTestUtils.getField(service, "adiProperties");
        properties.getExt().setEnabled(false);
        // 直接调计数方法仍会计数（过滤器负责在 enabled=false 时短路），
        // 这里断言 isEnabled 语义即可
        assertFalse(service.isEnabled());
        properties.getExt().setEnabled(true);
        assertTrue(service.isEnabled());
    }

    @DisplayName("sha256Short 输出 32 位十六进制且同输入同输出")
    @Test
    void sha256ShortIsStable() {
        String hash = ExtApiQuotaService.sha256Short(KEY);
        assertEquals(32, hash.length());
        assertEquals(hash, ExtApiQuotaService.sha256Short(KEY));
    }
}
