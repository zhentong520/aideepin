package com.moyz.adi.common.task;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S2-05：任务消费角色门（{@link TaskQueueConsumer.RoleCondition}）判定矩阵。
 * <p>
 * 语义：api 不装配消费循环；缺失（=all）/all/worker 装配；未识别值 fail-open 装配
 * （避免拼写错误导致任务静默无人消费）；大小写不敏感。
 */
class TaskConsumerRoleConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(GatedConfig.class);

    @Configuration
    static class GatedConfig {
        @Bean
        @Conditional(TaskQueueConsumer.RoleCondition.class)
        String gated() {
            return "ok";
        }
    }

    @Test
    void missingRoleDefaultsToAll() {
        runner.run(ctx -> assertTrue(ctx.containsBean("gated"), "缺省必须保持历史行为（消费启动）"));
    }

    @Test
    void allConsumes() {
        runner.withPropertyValues("adi.role=all").run(ctx -> assertTrue(ctx.containsBean("gated")));
    }

    @Test
    void workerConsumes() {
        runner.withPropertyValues("adi.role=worker").run(ctx -> assertTrue(ctx.containsBean("gated")));
    }

    @Test
    void apiDoesNotConsume() {
        runner.withPropertyValues("adi.role=api").run(ctx -> assertFalse(ctx.containsBean("gated")));
    }

    @Test
    void apiIsCaseInsensitive() {
        runner.withPropertyValues("adi.role=API").run(ctx -> assertFalse(ctx.containsBean("gated")));
    }

    @Test
    void unknownValueFailsOpen() {
        runner.withPropertyValues("adi.role=typo").run(ctx -> assertTrue(ctx.containsBean("gated"),
                "未识别角色必须 fail-open，避免静默不消费"));
    }
}
