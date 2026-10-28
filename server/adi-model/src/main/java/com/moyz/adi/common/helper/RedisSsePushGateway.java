package com.moyz.adi.common.helper;

import com.moyz.adi.common.constant.RedisKeyConstant;
import com.moyz.adi.common.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * S2-06：Redis pub/sub 实现（默认）。
 * <ul>
 *   <li>push → 广播到 {@link RedisKeyConstant#SSE_PUSH_CHANNEL}，投递统一走订阅回调
 *       {@link #onMessage(String)}（发布端不本地直投，单一代码路径避免重复投递）；</li>
 *   <li>Redis 故障 → 降级本地直投（S1-10：宁少发不崩主流程）；</li>
 *   <li>订阅由 adi-bootstrap 的 RedisMessageListenerContainer Bean 装配
 *       （Spring Data Redis 自带断线重连 + 自动重订阅，满足「断线重连恢复订阅」验收）。</li>
 * </ul>
 * Redis pub/sub implementation (default). Publishing goes through the channel and
 * delivery happens uniformly in the subscription callback (single code path, no
 * double delivery). Redis failure degrades to local delivery. Subscription is wired
 * by the RedisMessageListenerContainer bean in adi-bootstrap; Spring Data Redis
 * reconnects and re-subscribes automatically.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "adi.sse.push-mode", havingValue = "redis", matchIfMissing = true)
public class RedisSsePushGateway implements SsePushGateway {

    private final StringRedisTemplate stringRedisTemplate;
    private final SseNotificationRegistry registry;

    @Autowired
    public RedisSsePushGateway(StringRedisTemplate stringRedisTemplate, SseNotificationRegistry registry) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.registry = registry;
    }

    @Override
    public void push(long userId, String eventName, String data) {
        try {
            String body = JsonUtil.toJson(new SsePushMessage(userId, eventName, data));
            stringRedisTemplate.convertAndSend(RedisKeyConstant.SSE_PUSH_CHANNEL, body);
        } catch (DataAccessException e) {
            // S1-10：Redis 故障降级本地直投——本实例持有的连接仍能收到，跨实例投递丢失由前端轮询兜底
            log.warn("SSE push degraded to local delivery (redis unavailable), userId:{}, event:{}", userId, eventName);
            registry.sendToUser(userId, eventName, data);
        } catch (Exception e) {
            // 序列化等非 Redis 异常同样只记日志：推送是附加加速信息
            log.warn("SSE push failed, userId:{}, event:{}", userId, eventName, e);
        }
    }

    /**
     * 订阅回调（由 RedisMessageListenerContainer 驱动，所有实例都会收到自己发布的消息）。
     * <p>
     * Subscription callback; every instance receives every published message and
     * delivers only to its locally-held connections.
     */
    public void onMessage(String body) {
        try {
            SsePushMessage msg = JsonUtil.fromJson(body, SsePushMessage.class);
            if (msg == null || msg.getEventName() == null) {
                log.warn("SSE push message ignored (malformed): {}", body);
                return;
            }
            registry.sendToUser(msg.getUserId(), msg.getEventName(), msg.getData());
        } catch (Exception e) {
            log.warn("SSE push message handling failed: {}", body, e);
        }
    }
}
