package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.entity.AgentDelegationLog;
import com.moyz.adi.common.mapper.AgentDelegationLogMapper;
import com.moyz.adi.common.util.UuidUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * T7-02（2026-10-20）：委派记录服务（adi_agent_delegation_log）。
 * <p>
 * 写入端 {@link #saveAsync} 异步落库、任何异常只 warn——委派日志是可追溯手段，
 * 不阻断 Agent 主链路；查询端供管理端/审计按 traceId、目标 Agent 检索。
 * <p>
 * Delegation log service. Async best-effort writes (never block the agent chain),
 * query methods for audit by traceId / target agent.
 */
@Slf4j
@Service
public class DelegationLogService extends ServiceImpl<AgentDelegationLogMapper, AgentDelegationLog> {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAIL = "FAIL";
    public static final String STATUS_REJECTED = "REJECTED";

    @Resource
    private AsyncTaskExecutor mainExecutor;

    public void saveAsync(AgentDelegationLog delegationLog) {
        if (null == delegationLog) {
            return;
        }
        if (StringUtils.isBlank(delegationLog.getUuid())) {
            delegationLog.setUuid(UuidUtil.createShort());
        }
        mainExecutor.execute(() -> {
            try {
                baseMapper.insert(delegationLog);
            } catch (Exception e) {
                log.warn("saveAsync delegation log failed,traceId:{},target:{},error:{}",
                        delegationLog.getTraceId(), delegationLog.getTargetAgentId(), e.getMessage());
            }
        });
    }

    /** 按 traceId 查全部委派记录（按创建时间升序）。 */
    public List<AgentDelegationLog> listByTraceId(String traceId) {
        if (StringUtils.isBlank(traceId)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(AgentDelegationLog::getTraceId, traceId)
                .eq(AgentDelegationLog::getIsDeleted, false)
                .orderByAsc(AgentDelegationLog::getId)
                .list();
    }

    /**
     * 按目标 Agent 查最近的委派记录（管理端审计入口；limit 截断防全表扫描）。
     */
    public List<AgentDelegationLog> listByTargetAgent(String targetAgentId, int limit) {
        if (StringUtils.isBlank(targetAgentId)) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(AgentDelegationLog::getTargetAgentId, targetAgentId)
                .eq(AgentDelegationLog::getIsDeleted, false)
                .orderByDesc(AgentDelegationLog::getId)
                .last("limit " + Math.max(1, Math.min(limit, 200)))
                .list();
    }
}
