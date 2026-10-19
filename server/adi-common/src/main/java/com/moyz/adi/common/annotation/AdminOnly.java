package com.moyz.adi.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 管理端鉴权声明（ARCH-01，2026-10-16）。
 * <p>
 * 双保险的第二道：{@code TokenFilter} 已按 URL 前缀（/admin/）拦截非管理员，
 * 本注解是方法/类级的声明式兜底——新增 admin 接口若 URL 前缀写错或 service 层
 * 复用 admin 方法时，仍会被切面拦住。同时可被架构测试/静态扫描枚举所有 admin 面。
 * <p>
 * 非管理员调用抛 401 语义的 {@code ErrorEnum.A_NO_PERMISSION}（与 TokenFilter 行为对齐）。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminOnly {
}
