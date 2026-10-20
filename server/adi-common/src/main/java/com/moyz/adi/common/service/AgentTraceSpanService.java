package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.entity.AgentTraceSpan;
import com.moyz.adi.common.mapper.AgentTraceSpanMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 二开新增（T6-14，2026-10-20）：Agent trace span 服务。
 * <p>
 * 写入端 {@link #saveAsync} 用 mainExecutor 异步落库，任何异常只 warn——
 * trace 是可观测手段，不是业务依赖，绝不阻断对话链路。
 * 查询端给管理端 {@code /admin/agent-trace} 用。
 */
@Slf4j
@Service
public class AgentTraceSpanService extends ServiceImpl<AgentTraceSpanMapper, AgentTraceSpan> {

    @Resource
    private AsyncTaskExecutor mainExecutor;

    public void saveAsync(AgentTraceSpan span) {
        if (null == span) {
            return;
        }
        mainExecutor.execute(() -> {
            try {
                baseMapper.insert(span);
            } catch (Exception e) {
                log.warn("saveAsync agent trace span failed,traceId:{},span:{},error:{}",
                        span.getTraceId(), span.getName(), e.getMessage());
            }
        });
    }

    public void saveAsyncAll(List<AgentTraceSpan> spans) {
        if (CollectionUtils.isEmpty(spans)) {
            return;
        }
        spans.forEach(this::saveAsync);
    }

    /**
     * 按 traceId 取全部 span，按开始时间升序（时间线顺序）。
     */
    public List<AgentTraceSpan> listByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return Collections.emptyList();
        }
        return lambdaQuery()
                .eq(AgentTraceSpan::getTraceId, traceId)
                .eq(AgentTraceSpan::getIsDeleted, false)
                .orderByAsc(AgentTraceSpan::getStartTime)
                .list();
    }

    /**
     * 最近的 trace 列表（每个 traceId 取其第一条 span 作代表行），供管理端不输 traceId 直接点开。
     */
    public List<AgentTraceSpan> listRecent(int limit) {
        List<AgentTraceSpan> spans = lambdaQuery()
                .eq(AgentTraceSpan::getIsDeleted, false)
                .orderByDesc(AgentTraceSpan::getStartTime)
                .last("limit " + Math.max(1, Math.min(limit, 200)))
                .list();
        // 同一 trace 只保留最早一条（入口 span）
        List<String> seen = new java.util.ArrayList<>();
        List<AgentTraceSpan> result = new java.util.ArrayList<>();
        for (AgentTraceSpan span : spans) {
            if (!seen.contains(span.getTraceId())) {
                seen.add(span.getTraceId());
                result.add(span);
            }
        }
        return result;
    }
}
