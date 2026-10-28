package com.moyz.adi.common.helper;

/**
 * S2-06：SSE 跨实例推送网关。
 * <p>
 * 抽象「把一个用户级事件送达其 SSE 连接所在实例」的能力：
 * <ul>
 *   <li>{@code redis} 实现（默认）：发布到 Redis pub/sub 频道广播，所有实例订阅后本地投递；
 *       Redis 故障时降级为本地直投（S1-10 约定，宁少发不崩主流程）；</li>
 *   <li>{@code local} 实现：只投本实例（单实例部署或显式关闭 pub/sub 时使用）。</li>
 * </ul>
 * 由 {@code adi.sse.push-mode} 切换（redis|local，默认 redis）。
 * <p>
 * SSE cross-instance push gateway abstraction. The {@code redis} implementation
 * broadcasts over a pub/sub channel and each instance delivers locally; on Redis
 * failure it degrades to local-only delivery. The {@code local} implementation
 * never leaves the instance. Switched by {@code adi.sse.push-mode}.
 */
public interface SsePushGateway {

    /**
     * 推送用户级事件（尽力而为，实现必须自捕获异常，不得影响调用方主流程）。
     * <p>
     * Push a user-level event; implementations must swallow errors.
     *
     * @param userId    目标用户 / target user
     * @param eventName SSE 事件名 / SSE event name
     * @param data      事件负载（JSON 字符串）/ payload (JSON string)
     */
    void push(long userId, String eventName, String data);
}
