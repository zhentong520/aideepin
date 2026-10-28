package com.moyz.adi.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1-14：mainExecutor 等线程池治理回归。
 * mainExecutor 承载 @Async 的 LLM 长调用，禁止 CallerRunsPolicy（饱和时回落
 * Tomcat 线程会拖垮 web 容器），改 AbortPolicy + 扩队列 + 线程名 + 优雅停机。
 * S2-03：imagesExecutor 已删除（绘图执行迁入 task_queue 统一消费）。
 */
class BeanConfigExecutorPolicyTest {

    @Test
    void mainExecutorRejectsInsteadOfCallerRunsAndIsNamed() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new BeanConfig().mainExecutor();

        ThreadPoolExecutor tpe = executor.getThreadPoolExecutor();
        //拒绝策略：AbortPolicy，饱和快速失败，绝不回落调用线程
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, tpe.getRejectedExecutionHandler());
        //队列扩容：500 -> 2000，缓冲突发
        assertInstanceOf(LinkedBlockingQueue.class, tpe.getQueue());
        assertEquals(2000, ((LinkedBlockingQueue<?>) tpe.getQueue()).remainingCapacity());
        //线程 dump 可读
        assertEquals("adi-main-", executor.getThreadNamePrefix());
        //优雅停机（adi-bootstrap 测试类路径无 spring-test，用原生反射读字段）
        assertTrue(waitsOnShutdown(executor));
    }

    private static boolean waitsOnShutdown(ThreadPoolTaskExecutor executor) {
        try {
            java.lang.reflect.Field field = org.springframework.scheduling.concurrent.ExecutorConfigurationSupport.class
                    .getDeclaredField("waitForTasksToCompleteOnShutdown");
            field.setAccessible(true);
            return Boolean.TRUE.equals(field.get(executor));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void workflowAndTaskQueueExecutorsKeepAbortPolicyAndNames() {
        //参照系：AbortPolicy + 前缀的池，改造不得回退。
        //S2-02：indexExecutor 已删除（KB 索引执行迁入 task_queue 统一消费），
        //其断言位置由 S2-01 的 taskQueueExecutor 接管。
        ThreadPoolTaskExecutor wf = (ThreadPoolTaskExecutor) new BeanConfig().workflowExecutor();
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, wf.getThreadPoolExecutor().getRejectedExecutionHandler());
        assertEquals("adi-wf-", wf.getThreadNamePrefix());

        ThreadPoolTaskExecutor tq = (ThreadPoolTaskExecutor) new BeanConfig().taskQueueExecutor();
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, tq.getThreadPoolExecutor().getRejectedExecutionHandler());
        assertEquals("adi-task-", tq.getThreadNamePrefix());
        assertTrue(waitsOnShutdown(tq));
    }
}
