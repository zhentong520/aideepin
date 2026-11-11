package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.toolkit.ChainWrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.WfRuntimeCheckpoint;
import com.moyz.adi.common.mapper.WfRuntimeCheckpointMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 业务编排 Harness M2-T4（2026-11-11）：运行检查点服务。
 * 写入时机（决策点①）：引擎停驻落 PAUSED/WAITING_INPUT 时 upsert 一行；
 * 清理时机：任意终态（SUCCESS/FAIL/CANCELED）即删。
 */
@Slf4j
@Service
public class WfRuntimeCheckpointService extends ServiceImpl<WfRuntimeCheckpointMapper, WfRuntimeCheckpoint> {

    /**
     * 停驻时保存（upsert：同 runtimeUuid 活跃行只保留最新一份）。
     *
     * @param pausedNodeUuid 停驻节点（恢复时从该节点起跑）
     * @param snapshot       轻量状态快照（可空，空则落空对象）
     */
    public void saveParked(String runtimeUuid, String workflowUuid, int processStatus,
                           String pausedNodeUuid, ObjectNode snapshot) {
        WfRuntimeCheckpoint exist = getByRuntimeUuid(runtimeUuid);
        WfRuntimeCheckpoint checkpoint = null == exist ? new WfRuntimeCheckpoint() : exist;
        checkpoint.setRuntimeUuid(runtimeUuid);
        checkpoint.setWorkflowUuid(workflowUuid);
        checkpoint.setProcessStatus(processStatus);
        checkpoint.setPausedNodeUuid(null == pausedNodeUuid ? "" : pausedNodeUuid);
        checkpoint.setStateSnapshot(null == snapshot ? com.moyz.adi.common.util.JsonUtil.createObjectNode() : snapshot);
        saveOrUpdate(checkpoint);
        log.info("checkpoint saved, runtime:{}, status:{}, pausedNode:{}", runtimeUuid, processStatus, pausedNodeUuid);
    }

    /** 按 runtimeUuid 取活跃检查点（is_deleted=false），无则 null。 */
    public WfRuntimeCheckpoint getByRuntimeUuid(String runtimeUuid) {
        return ChainWrappers.lambdaQueryChain(getBaseMapper())
                .eq(WfRuntimeCheckpoint::getRuntimeUuid, runtimeUuid)
                .eq(WfRuntimeCheckpoint::getIsDeleted, false)
                .one();
    }

    /** 运行进入终态后删除检查点（软删）。 */
    public boolean deleteByRuntimeUuid(String runtimeUuid) {
        WfRuntimeCheckpoint exist = getByRuntimeUuid(runtimeUuid);
        if (null == exist) {
            return false;
        }
        return removeById(exist.getId());
    }
}
