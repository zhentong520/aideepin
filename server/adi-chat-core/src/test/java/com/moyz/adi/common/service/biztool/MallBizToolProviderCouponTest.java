package com.moyz.adi.common.service.biztool;

import com.moyz.adi.common.entity.MallCoupon;
import com.moyz.adi.common.entity.MallUserCoupon;
import com.moyz.adi.common.mapper.MallCouponMapper;
import com.moyz.adi.common.mapper.MallUserCouponMapper;
import com.moyz.adi.common.service.mall.MallService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商城插件领券幂等的回归测试（T9-M2，2026-10-29）。
 *
 * <p>幂等设计：mall_user_coupon 上 (user_id, coupon_code) 唯一索引兜底，
 * MallBizToolProvider 捕获 DuplicateKeyException 转成「已领取」友好提示。
 * 这里验证领券路径的关键分支（纯 Mockito，不起 Spring 容器）。
 */
class MallBizToolProviderCouponTest {

    private MallBizToolProvider provider;
    private MallService mallService;
    private MallUserCouponMapper userCouponMapper;
    private MallCouponMapper couponMapper;

    @BeforeEach
    void setUp() {
        provider = new MallBizToolProvider();
        mallService = mock(MallService.class);
        userCouponMapper = mock(MallUserCouponMapper.class);
        couponMapper = mock(MallCouponMapper.class);
        when(mallService.userCouponMapper()).thenReturn(userCouponMapper);
        when(mallService.couponMapper()).thenReturn(couponMapper);
        ReflectionTestUtils.setField(provider, "mallService", mallService);
    }

    private MallCoupon coupon(String code, int totalCount, int claimedCount) {
        MallCoupon c = new MallCoupon();
        c.setId(1L);
        c.setCode(code);
        c.setTitle("满100减20");
        c.setAmount(new BigDecimal("20"));
        c.setMinSpend(new BigDecimal("100"));
        c.setTotalCount(totalCount);
        c.setClaimedCount(claimedCount);
        c.setExpireTime(LocalDateTime.now().plusDays(7));
        c.setIsEnable(true);
        c.setIsDeleted(false);
        return c;
    }

    private String claim(String code, Long userId) {
        return provider.execute("mall_claim_coupon", "{\"couponCode\":\"" + code + "\"}", userId, "char-uuid");
    }

    @DisplayName("正常领取：插入用户券 + claimed_count 原子 +1，返回 OK")
    @Test
    void claimSuccess() {
        when(mallService.getCoupon("MALL100")).thenReturn(coupon("MALL100", 1000, 10));
        when(userCouponMapper.insert(any(MallUserCoupon.class))).thenReturn(1);

        String result = claim("MALL100", 42L);

        assertTrue(result.startsWith("OK"), result);
        verify(userCouponMapper).insert(any(MallUserCoupon.class));
        // claimed_count 用 COALESCE 原子自增，防并发丢计数
        verify(couponMapper).update(any(), any());
    }

    @DisplayName("重复领取：唯一索引抛 DuplicateKeyException，收敛为「已领取」提示且不再加计数")
    @Test
    void claimIdempotent() {
        when(mallService.getCoupon("MALL100")).thenReturn(coupon("MALL100", 1000, 10));
        when(userCouponMapper.insert(any(MallUserCoupon.class))).thenThrow(new DuplicateKeyException("uk_mall_user_coupon"));

        String result = claim("MALL100", 42L);

        assertTrue(result.contains("已经领过"), result);
        verify(userCouponMapper).insert(any(MallUserCoupon.class));
        verify(couponMapper, never()).update(any(), any());
    }

    @DisplayName("券不存在或已下架：返回友好提示，不写任何表")
    @Test
    void claimCouponNotFound() {
        when(mallService.getCoupon("GHOST")).thenReturn(null);

        String result = claim("GHOST", 42L);

        assertTrue(result.contains("不存在"), result);
        verify(userCouponMapper, never()).insert(any(MallUserCoupon.class));
        verify(couponMapper, never()).update(any(), any());
    }

    @DisplayName("库存领完：不发放，返回领取失败提示")
    @Test
    void claimSoldOut() {
        when(mallService.getCoupon("MALL100")).thenReturn(coupon("MALL100", 100, 100));

        String result = claim("MALL100", 42L);

        assertTrue(result.contains("领完"), result);
        verify(userCouponMapper, never()).insert(any(MallUserCoupon.class));
    }

    @DisplayName("不限量券（total_count=-1）不受库存校验限制，可正常领取")
    @Test
    void claimUnlimitedStock() {
        when(mallService.getCoupon("MALL20")).thenReturn(coupon("MALL20", -1, 99999));
        when(userCouponMapper.insert(any(MallUserCoupon.class))).thenReturn(1);

        String result = claim("MALL20", 42L);

        assertTrue(result.startsWith("OK"), result);
        verify(userCouponMapper).insert(any(MallUserCoupon.class));
    }

    @DisplayName("缺用户上下文（工具回调线程 userId 不可缺失）：拒绝领取")
    @Test
    void claimWithoutUserContext() {
        String result = claim("MALL100", null);

        assertTrue(result.startsWith("ERROR"), result);
        verify(mallService, never()).getCoupon(any());
        verify(userCouponMapper, never()).insert(any(MallUserCoupon.class));
    }

    @DisplayName("缺 couponCode 参数：返回参数错误")
    @Test
    void claimWithoutCode() {
        String result = provider.execute("mall_claim_coupon", "{}", 42L, "char-uuid");
        assertTrue(result.startsWith("ERROR"), result);
    }

    @DisplayName("未知 mall 子工具：返回 ERROR 字符串而非异常")
    @Test
    void unknownSubTool() {
        String result = provider.execute("mall_nonexistent", "{}", 42L, "char-uuid");
        assertTrue(result.startsWith("ERROR"), result);
    }
}
