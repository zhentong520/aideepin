package com.moyz.adi.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.moyz.adi.common.entity.FinetuneJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface FinetuneJobMapper extends BaseMapper<FinetuneJob> {

    /**
     * 原子领取一个 pending 任务（外部 Worker 经 HTTP 调用，服务端事务内执行）：
     * FOR UPDATE SKIP LOCKED 保证多 Worker 并发领取互不重复；user_id 过滤保证
     * Worker 只领取其 API Key 属主（ext key owner）名下的任务。
     * 领取即置 running，并写入 worker_id / 新的 worker_epoch / 各时间基线。
     * PG UPDATE..RETURNING 须经 executeQuery，故用 select 标签。
     */
    FinetuneJob claimOne(@Param("workerId") String workerId, @Param("userId") Long userId);

    /**
     * Worker 心跳与进度回写合一。返回 0 表示该行已不属于此 (id, epoch)
     * （心跳超时被回收重派或已被强制失败），此 Worker 已成僵尸。
     */
    int heartbeat(@Param("id") Long id, @Param("workerEpoch") String workerEpoch);

    /**
     * 进度回写（含心跳刷新）：epoch/stop 校验同 heartbeat。
     * <p>
     * 返回该行当前的 stop_flag；返回 null 表示该行已不属于此 (id, epoch)
     * （心跳超时被回收重派或已被强制失败），此 Worker 已成僵尸。
     * 用 RETURNING 直接带回 stop_flag，省掉每次上报后的一次额外 SELECT。
     */
    Boolean reportProgress(@Param("id") Long id, @Param("workerEpoch") String workerEpoch,
                           @Param("progress") Double progress, @Param("currentEpoch") Integer currentEpoch,
                           @Param("trainLoss") Double trainLoss, @Param("evalLoss") Double evalLoss);

    /**
     * 终态落定（success/failed/stopped）。仅 status='running' 且 epoch 匹配时生效：
     * 行已被回收重派时返回 0，僵尸 Worker 不得终结新 Worker 的任务。
     */
    int finish(@Param("id") Long id, @Param("workerEpoch") String workerEpoch,
               @Param("status") String status, @Param("failReason") String failReason);

    /**
     * 管理端请求停止：pending 直接置 stopped（无执行者，安全）；
     * running 只置 stop_flag，Worker 在下一次进度回调响应里读到后自行中止上报。
     */
    int requestStop(@Param("uuid") String uuid);

    /**
     * 崩溃自愈：running 且心跳超时的行重置 pending（Worker 进程死亡）。
     * 重置时清空 worker 字段，由轮询器重新派发。
     */
    int resetStaleRunning(@Param("minutes") int minutes);

    /**
     * 熔断：仍在心跳但 start_time 超过最大时长的行强制失败（进程活着但卡死）。
     * PG UPDATE..RETURNING 须经 executeQuery，故用 select 标签。
     */
    java.util.List<FinetuneJob> failOverdue(@Param("minutes") int minutes);
}
