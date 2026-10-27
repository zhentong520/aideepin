package com.moyz.adi.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S1-14：mainExecutor/imagesExecutor 治理回归。
 * mainExecutor 承载 @Async 的 LLM 长调用，禁止 CallerRunsPolicy（饱和时回落
 * Tomcat 线程会拖垮 web 容器），改 AbortPolicy + 扩队列 + 线程名 + 优雅停机。
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
    void imagesExecutorIsNamed() {
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) new BeanConfig().imagesExecutor();

        //本卡范围为 imagesExecutor 补线程名前缀（拒绝策略保持原状）
        assertEquals("adi-image-", executor.getThreadNamePrefix());
    }

    @Test
    void workflowAndIndexExecutorsKeepAbortPolicyAndNames() {
        //参照系：这两个池此前已是 AbortPolicy + 前缀，改造不得回退它们
        ThreadPoolTaskExecutor wf = (ThreadPoolTaskExecutor) new BeanConfig().workflowExecutor();
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, wf.getThreadPoolExecutor().getRejectedExecutionHandler());
        assertEquals("adi-wf-", wf.getThreadNamePrefix());

        ThreadPoolTaskExecutor idx = (ThreadPoolTaskExecutor) new BeanConfig().indexExecutor();
        assertInstanceOf(ThreadPoolExecutor.AbortPolicy.class, idx.getThreadPoolExecutor().getRejectedExecutionHandler());
        assertEquals("adi-index-", idx.getThreadNamePrefix());
    }
}
