package com.moyz.adi.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * S1-07a：ShedLock 定时任务防重。
 *
 * <p>多实例共库部署时，{@code @Scheduled} 默认会在每个实例上各自触发（健康检查、
 * KB 统计、文档源同步、索引轮询、finetune 轮询、工作流触发等）。启用 ShedLock 后，
 * 持有 {@code shedlock} 表行锁的实例才执行，其余实例跳过本轮。
 *
 * <p>约定：所有 {@code @Scheduled} 方法必须同时标注 {@code @SchedulerLock}，
 * 由 WholeAppArchitectureTest 的 schedulerLockCoverage 规则强制（新增定时任务漏加锁 = CI 红）。
 * {@code lockAtMostFor} 取「该任务最长执行时间的 2 倍以上」，实例宕机后另一实例最迟在该
 * 时长内接管。
 *
 * <p>锁以数据库时钟为准（{@code usingDbTime}），避免实例间时钟漂移；表结构见
 * {@code V18__shedlock.sql}。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
public class SchedulerLockConfig {

    @Bean
    public LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbcTemplate)
                        .usingDbTime()
                        .build());
    }
}
