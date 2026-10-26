package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.RagFeedback;
import com.moyz.adi.common.entity.RagQueryLog;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.mapper.RagFeedbackMapper;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 二开新增（T7-01，2026-10-20）：RAG 回答 👍👎 反馈服务。
 * <p>
 * 写入语义（唯一部分索引 uk_rag_feedback_query_user 兜底）：
 * 同一用户对同一检索一票，可改票（PASS 可改 FAIL）；comment 随票更新。
 * 用户端 API（POST /chat/rag-feedback）必须登录；user-web 页面接入由用户确认后进行，
 * 本服务与接口先行就绪。
 */
@Slf4j
@Service
public class RagFeedbackService extends ServiceImpl<RagFeedbackMapper, RagFeedback> {

    /** comment 截断上限（DDL varchar(500)） */
    private static final int COMMENT_MAX_LEN = 500;

    @Resource
    private RagQueryLogService ragQueryLogService;

    /**
     * 提交/修改反馈。queryId 必须对应一条真实检索日志（防对不存在的检索刷票）。
     *
     * @param rating      PASS（👍）/ FAIL（👎），其余值拒绝
     * @param messageUuid 回答消息 uuid，可为空（user-web 接入前）
     * @param comment     可选文字说明，截断到 500 字符
     */
    public RagFeedback submit(String queryId, String messageUuid, String rating, String comment) {
        if (StringUtils.isBlank(queryId)) {
            throw new IllegalArgumentException("queryId 不能为空");
        }
        if (!RagFeedback.RATING_PASS.equals(rating) && !RagFeedback.RATING_FAIL.equals(rating)) {
            throw new IllegalArgumentException("rating 必须是 PASS 或 FAIL");
        }
        User currentUser = ThreadContext.getExistCurrentUser();
        String userUuid = null == currentUser.getUuid() ? "" : currentUser.getUuid();
        // queryId 必须真实存在：既防刷票，也保证看板明细里点过的票一定挂得上
        if (!ragQueryLogService.lambdaQuery().eq(RagQueryLog::getQueryId, queryId).exists()) {
            throw new IllegalArgumentException("queryId 对应的检索日志不存在: " + queryId);
        }

        LambdaQueryWrapper<RagFeedback> wrapper = new LambdaQueryWrapper<RagFeedback>()
                .eq(RagFeedback::getQueryId, queryId)
                .eq(RagFeedback::getUserUuid, userUuid)
                .eq(RagFeedback::getIsDeleted, false);
        RagFeedback existing = baseMapper.selectOne(wrapper);
        if (null != existing) {
            existing.setRating(rating);
            existing.setMessageUuid(StringUtils.defaultIfBlank(messageUuid, existing.getMessageUuid()));
            existing.setComment(StringUtils.abbreviate(StringUtils.defaultString(comment), COMMENT_MAX_LEN));
            baseMapper.updateById(existing);
            return existing;
        }
        try {
            RagFeedback row = new RagFeedback();
            row.setFeedbackUuid(UuidUtil.createShort());
            row.setQueryId(queryId);
            row.setMessageUuid(StringUtils.defaultString(messageUuid));
            row.setUserUuid(userUuid);
            row.setRating(rating);
            row.setComment(StringUtils.abbreviate(StringUtils.defaultString(comment), COMMENT_MAX_LEN));
            baseMapper.insert(row);
            return row;
        } catch (DuplicateKeyException e) {
            // 并发首投撞唯一索引：按改票重试一次（仍失败则上抛，不静默吞票）
            log.info("duplicate rag feedback on first insert, retry as update,queryId:{}", queryId);
            RagFeedback row = baseMapper.selectOne(new LambdaQueryWrapper<RagFeedback>()
                    .eq(RagFeedback::getQueryId, queryId)
                    .eq(RagFeedback::getUserUuid, userUuid)
                    .eq(RagFeedback::getIsDeleted, false));
            if (null == row) {
                throw e;
            }
            row.setRating(rating);
            row.setMessageUuid(StringUtils.defaultIfBlank(messageUuid, row.getMessageUuid()));
            row.setComment(StringUtils.abbreviate(StringUtils.defaultString(comment), COMMENT_MAX_LEN));
            baseMapper.updateById(row);
            return row;
        }
    }

    /**
     * 按 queryId 批量取反馈（明细表分页后一次查，避免 N+1）。
     *
     * @return queryId -> feedback；无反馈的 queryId 不出现在 map 里
     */
    public Map<String, RagFeedback> mapByQueryIds(List<String> queryIds) {
        if (null == queryIds || queryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<RagFeedback> rows = baseMapper.selectList(new LambdaQueryWrapper<RagFeedback>()
                .in(RagFeedback::getQueryId, queryIds)
                .eq(RagFeedback::getIsDeleted, false));
        return rows.stream().collect(Collectors.toMap(RagFeedback::getQueryId, Function.identity(), (a, b) -> a));
    }
}
