package com.moyz.adi.common.workflow;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 业务编排 Harness M2-T2/T3（2026-11-11）：已暂停（PAUSED）正在等待用户继续的流程。
 * <p>
 * 与 {@link InterruptedFlow}（WAITING_INPUT 人机交互停驻）同构：进程内注册表持有引擎实例，
 * resume 时取出引擎从 MemorySaver checkpoint 续跑。
 * <p>
 * 过期时间 30 分钟（暂停语义下用户可能离开更久；过期后走 T4 的 checkpoint 落库恢复路径）。
 * TODO 需要考虑项目多节点部署的情况（与 InterruptedFlow 相同）。
 */
@Slf4j
public class PausedFlow {

    private PausedFlow() {
    }

    private static final Cache<String, WorkflowEngine> CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(1000)
            .removalListener(notification -> {
                log.info("PausedFlow removed entry, key:{}, cause:{}", notification.getKey(), notification.getCause());
            })
            .build();

    public static void put(String runtimeUuid, WorkflowEngine engine) {
        CACHE.put(runtimeUuid, engine);
    }

    public static WorkflowEngine get(String runtimeUuid) {
        return CACHE.getIfPresent(runtimeUuid);
    }

    public static void remove(String runtimeUuid) {
        CACHE.invalidate(runtimeUuid);
    }

    public static void cleanUp() {
        CACHE.cleanUp();
    }

    public static long size() {
        return CACHE.size();
    }
}
