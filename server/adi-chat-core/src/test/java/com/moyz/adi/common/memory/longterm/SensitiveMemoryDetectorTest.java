package com.moyz.adi.common.memory.longterm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T7-07：敏感（PII）记忆检测器单测。
 * 卡片要求：敏感记忆（联系方式/证件/账号密码）写入前必须用户确认——
 * 检测器是分流的唯一判据，漏拦 = PII 直接落向量库。
 */
class SensitiveMemoryDetectorTest {

    @Test
    void detectsCnMobile() {
        assertTrue(SensitiveMemoryDetector.isSensitive("用户的手机号是13812345678"));
        assertTrue(SensitiveMemoryDetector.isSensitive("phone: 138-1234-5678"));
        assertTrue(SensitiveMemoryDetector.isSensitive("联系 19912345678 找我"));
    }

    @Test
    void detectsEmail() {
        assertTrue(SensitiveMemoryDetector.isSensitive("我的邮箱是 foo.bar@example.com，随时联系"));
    }

    @Test
    void detectsIdCard() {
        assertTrue(SensitiveMemoryDetector.isSensitive("身份证号11010119900307867X"));
        assertTrue(SensitiveMemoryDetector.isSensitive("证件号码 110101199003078"));
    }

    @Test
    void detectsCredentialKeywords() {
        assertTrue(SensitiveMemoryDetector.isSensitive("我的密码是 abc123456"));
        assertTrue(SensitiveMemoryDetector.isSensitive("账号是 adi_user_01"));
        assertTrue(SensitiveMemoryDetector.isSensitive("The API token is sk-xxx"));
        assertTrue(SensitiveMemoryDetector.isSensitive("API key 已更新"));
    }

    @Test
    void nonSensitiveFactsPassThrough() {
        assertFalse(SensitiveMemoryDetector.isSensitive("用户住在杭州，喜欢喝龙井茶"));
        assertFalse(SensitiveMemoryDetector.isSensitive("2026-10-20 用户完成了季度总结"));
        assertFalse(SensitiveMemoryDetector.isSensitive(""));
        assertFalse(SensitiveMemoryDetector.isSensitive(null));
    }

    @Test
    void innocentNumbersDoNotFalsePositive() {
        // 普通数字（订单号/年份/金额）不构成 PII
        assertFalse(SensitiveMemoryDetector.isSensitive("订单 202605001 已发货，共 2 件"));
        assertFalse(SensitiveMemoryDetector.isSensitive("电话问题我们已讨论过 3 次"));
    }
}
