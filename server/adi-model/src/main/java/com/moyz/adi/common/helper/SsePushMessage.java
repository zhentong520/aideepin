package com.moyz.adi.common.helper;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * S2-06：SSE 跨实例分发消息信封（Redis pub/sub 频道上的负载）。
 * <p>
 * 广播制：所有实例都收到，持有该用户 SSE 连接的实例本地投递，其余实例丢弃。
 * 实例数量在个位~两位数规模下广播 + 本地过滤最简单且无订阅窗口竞态
 * （按 userId 建频道需要在连接建立/断开时动态 subscribe/unsubscribe，
 * 存在「连接已建立、订阅未生效」的丢消息窗口）。
 * <p>
 * SSE cross-instance push envelope (payload of the Redis pub/sub channel).
 * Broadcast semantics: every instance receives it, instances holding the user's
 * SSE connection deliver locally, others drop it. Simple and free of the
 * subscribe-window race that per-user channels would introduce.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SsePushMessage {

    /** 目标用户 id / target user id */
    private long userId;

    /** SSE 事件名（如 [TASK_STATE_CHANGED]）/ SSE event name */
    private String eventName;

    /** 事件负载（JSON 字符串）/ event payload (JSON string) */
    private String data;
}
