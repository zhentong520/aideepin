package com.moyz.adi.common.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * 二开新增（T6-14，2026-10-20）：轻量全链路 trace 工具。
 * <p>
 * traceId 存 SLF4J MDC（key=traceId），日志输出经 logback pattern 的 %X{traceId} 带出，
 * 同时写入 adi_agent_trace_span / adi_llm_call_record.trace_id 供管理端时间线查询。
 * 不引入外部 APM，纯 ThreadLocal + MDC 实现。
 * <p>
 * Lifecycle: the thread that starts a conversation call calls {@link #ensureTraceId()}
 * (creating the id if absent), child steps just read {@link #getTraceId()}.
 * {@link #clearIfCreated(boolean)} clears the MDC only when the same call created it,
 * so worker threads (mainExecutor / @Async) never leak a stale context.
 */
public final class TraceUtil {

    public static final String MDC_KEY = "traceId";

    private TraceUtil() {
    }

    /**
     * 取当前 traceId；没有则生成并放入 MDC。
     *
     * @return traceId
     */
    public static String ensureTraceId() {
        String existing = getTraceId();
        if (StringUtils.isNotBlank(existing)) {
            return existing;
        }
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put(MDC_KEY, traceId);
        return traceId;
    }

    /**
     * 只读获取当前 traceId，不创建。无链路上下文时返回空串（如非对话链路的定时任务）。
     */
    public static String getTraceId() {
        return StringUtils.defaultString(MDC.get(MDC_KEY));
    }

    /**
     * 若 traceId 由本次调用创建则清掉，避免线程池复用时串链路。
     *
     * @param created {@link #ensureTraceId()} 所在线程是否为创建方（业务侧自行保存该布尔）
     */
    public static void clearIfCreated(boolean created) {
        if (created) {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 无条件清理（测试或请求拦截器统一出口用）。
     */
    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
