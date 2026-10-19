package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.mapper.RagQueryLogMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.LocalDateTimeUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 二开新增（T1-01，2026-10-16）：RAG 检索日志服务。
 * <p>
 * 写入端 {@link #saveAsync} 用 mainExecutor 异步落库，任何异常只 warn，
 * 绝不阻断检索链路（日志是可观测手段，不是业务依赖）。
 * 查询端给管理端 {@code GET /admin/rag/query-log} 用。
 */
@Slf4j
@Service
public class RagQueryLogService extends ServiceImpl<RagQueryLogMapper, RagQueryLog> {

    /** kb uuid 只允许短 uuid 字符集，防注入的同时过滤脏参数 */
    private static final Pattern KB_UUID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Resource
    private AsyncTaskExecutor mainExecutor;

    /**
     * 异步写入一条检索日志。失败只 warn：日志缺失可以被接受，检索被拖垮不行。
     */
    public void saveAsync(RagQueryLog logEntry) {
        if (null == logEntry) {
            return;
        }
        mainExecutor.execute(() -> {
            try {
                baseMapper.insert(logEntry);
            } catch (Exception e) {
                log.warn("saveAsync rag query log failed,queryId:{},error:{}", logEntry.getQueryId(), e.getMessage());
            }
        });
    }

    /**
     * 管理端分页查询：按时间区间 / 用户 / 知识库（jsonb 包含）筛选。
     */
    public Page<RagQueryLog> pageQuery(String userUuid, String kbUuid, String beginTime, String endTime,
                                       Integer currentPage, Integer pageSize) {
        LambdaQueryWrapper<RagQueryLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(userUuid)) {
            wrapper.eq(RagQueryLog::getUserUuid, userUuid);
        }
        if (StringUtils.isNotBlank(kbUuid)) {
            if (!KB_UUID_PATTERN.matcher(kbUuid).matches()) {
                return new Page<>(currentPage, pageSize);
            }
            // kb_uuids 是 jsonb 数组，用包含查询走 GIN 索引；值经绑定参数传入，不拼 SQL
            wrapper.apply("kb_uuids @> {0}", "[\"" + kbUuid + "\"]");
        }
        if (StringUtils.isNotBlank(beginTime) && StringUtils.isNotBlank(endTime)) {
            LocalDateTime begin = LocalDateTimeUtil.parse(beginTime);
            LocalDateTime end = LocalDateTimeUtil.parse(endTime);
            if (null != begin && null != end) {
                wrapper.between(RagQueryLog::getCreateTime, begin, end);
            }
        }
        wrapper.eq(RagQueryLog::getIsDeleted, false);
        wrapper.orderByDesc(RagQueryLog::getCreateTime);
        return baseMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
    }

    /**
     * 便捷构造：把一次检索的要素组装成日志实体（kbUuids/hitSegments 序列化为 jsonb）。
     */
    public static RagQueryLog build(String queryId, String userUuid, List<String> kbUuids,
                                    String filterExpression, String hitSegmentsJson,
                                    int hitCount, int aclFilteredCount, int elapsedMs,
                                    String embeddingModel) {
        RagQueryLog row = new RagQueryLog();
        row.setQueryId(queryId);
        row.setUserUuid(null == userUuid ? "" : userUuid);
        row.setKbUuids(CollectionUtils.isEmpty(kbUuids)
                ? JsonUtil.toJsonNode("[]")
                : JsonUtil.classToJsonNode(kbUuids));
        row.setFilterExpression(null == filterExpression ? "" : filterExpression);
        row.setHitSegments(null == hitSegmentsJson
                ? JsonUtil.toJsonNode("[]")
                : JsonUtil.toJsonNode(hitSegmentsJson));
        row.setHitCount(hitCount);
        row.setAclFilteredCount(aclFilteredCount);
        row.setElapsedMs(elapsedMs);
        row.setPromptTokens(0);
        row.setEmbeddingModel(null == embeddingModel ? "" : embeddingModel);
        row.setFeedback("");
        return row;
    }
}
