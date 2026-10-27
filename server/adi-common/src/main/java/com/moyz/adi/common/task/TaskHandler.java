package com.moyz.adi.common.task;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * S2-01：任务处理器 SPI。实现为 @Component 注册（注册制，同 S1-02 模式），
 * {@link TaskQueueConsumer} 按 {@link #type()} 自动发现并分发。
 * <p>
 * 语义约定：
 * <ul>
 *   <li>返回 {@link TaskResult#ok()} 或正常返回 → SUCCESS；</li>
 *   <li>返回 fail / 抛异常 → 指数退避重试，超 max_retry 转 DEAD；</li>
 *   <li>handler 必须幂等：孤儿回收（进程被 kill）会把 RUNNING 重回 PENDING，同一条任务可能被执行两次。</li>
 * </ul>
 */
public interface TaskHandler {

    /** 任务类型，对应 task_queue.task_type，需全局唯一 */
    String type();

    TaskResult handle(JsonNode payload) throws Exception;
}
