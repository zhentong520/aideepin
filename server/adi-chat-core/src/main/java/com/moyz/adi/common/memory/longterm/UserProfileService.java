package com.moyz.adi.common.memory.longterm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.moyz.adi.common.entity.UserProfile;
import com.moyz.adi.common.entity.UserProfileEvent;
import com.moyz.adi.common.mapper.UserProfileEventMapper;
import com.moyz.adi.common.mapper.UserProfileMapper;
import com.moyz.adi.common.util.JsonUtil;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 二开新增（P0-1，2026-11-03）：结构化用户画像服务（Memobase 风格）。
 * <p>
 * 写入侧：{@code LongTermMemoryService} 在抽取轮末尾调用
 * {@link #applyMergeResult}（全量画像回写 + 事件追加 + 旧事件 bi-temporal 失效）；
 * 读取侧：{@link #getProfileText} 纯 SQL 读画像并渲染为 prompt 段落——
 * 零 embedding、零向量检索（<100ms），「用户是做什么的」类稳定属性问题直接命中。
 * <p>
 * ⚠️ 画像与向量记忆是两条独立通道：本服务任何失败都不允许影响向量记忆链路，
 * 调用方（LongTermMemoryService）已按 try/catch 包裹。
 */
@Slf4j
@Service
public class UserProfileService extends ServiceImpl<UserProfileMapper, UserProfile> {

    @Resource
    private UserProfileEventMapper userProfileEventMapper;

    /**
     * Load the current user's profile row (null when absent — callers must treat
     * "no profile yet" as normal, not an error).
     * <p>
     * 读取当前用户画像行；不存在返回 null（新用户无画像是常态，不是错误）。
     */
    public UserProfile getByUserId(Long userId) {
        if (null == userId) {
            return null;
        }
        return this.getOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .eq(UserProfile::getIsDeleted, false)
                .last("limit 1"));
    }

    /**
     * Render the profile as prompt lines ("- occupation: developer"), or "" when
     * the profile is absent/empty. Pure SQL read, no embedding, no LLM.
     * <p>
     * 渲染画像为 prompt 行文本；无画像/空画像返回空串。纯 SQL 读取。
     */
    public String getProfileText(Long userId) {
        UserProfile row = getByUserId(userId);
        if (null == row || null == row.getProfile() || !row.getProfile().isObject()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        row.getProfile().properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            String valueText = value.isTextual() ? value.asText() : value.toString();
            if (StringUtils.isNotBlank(valueText)) {
                sb.append("- ").append(entry.getKey()).append(": ").append(valueText.trim()).append("\n");
            }
        });
        return sb.toString();
    }

    /**
     * Apply one merge-round result: full-profile upsert + event append +
     * bi-temporal invalidation of superseded events.
     * <p>
     * 应用一轮合并结果：全量画像回写 + 新事件追加 + 被取代旧事件打 invalid_at
     * （bi-temporal，绝不物理删除）。
     *
     * @param mergedProfile 全量画像 JSON（LLM 输出，null/非对象则跳过画像回写）
     * @param newEvents     本轮新增事件文本
     * @param outdatedEvents 被取代的旧事件原文（按原文精确匹配后置 invalid_at）
     */
    public void applyMergeResult(Long userId, JsonNode mergedProfile,
                                 List<String> newEvents, List<String> outdatedEvents,
                                 Long sourceMsgId) {
        if (null == userId) {
            return;
        }
        if (null != mergedProfile && mergedProfile.isObject()) {
            upsertProfile(userId, mergedProfile);
        } else {
            log.info("Profile merge result has no object profile, skip upsert, userId:{}", userId);
        }
        if (CollectionUtils.isNotEmpty(newEvents)) {
            appendEvents(userId, newEvents, sourceMsgId);
        }
        if (CollectionUtils.isNotEmpty(outdatedEvents)) {
            invalidateEvents(userId, outdatedEvents);
        }
    }

    /**
     * Full-profile upsert: whole JSON object rewritten in one shot (idempotent by
     * design; a bad LLM round can be repaired by the next good round or by audit).
     * <p>
     * 全量画像 upsert：整个 JSON 对象一次性回写（天然幂等；单轮坏输出可被下一轮
     * 好输出覆盖，也可审计后人工修正）。jsonb 写入必须走 updateById（T1-03 铁律 4）。
     */
    private void upsertProfile(Long userId, JsonNode mergedProfile) {
        UserProfile row = getByUserId(userId);
        if (null == row) {
            row = new UserProfile();
            row.setUuid(UuidUtil.createShort());
            row.setUserId(userId);
            row.setProfile(mergedProfile);
            row.setProfileVersion(1);
            this.save(row);
            log.info("User profile created, userId:{}", userId);
            return;
        }
        row.setProfile(mergedProfile);
        row.setProfileVersion((row.getProfileVersion() == null ? 0 : row.getProfileVersion()) + 1);
        this.updateById(row);
        log.info("User profile merged, userId:{}, version:{}", userId, row.getProfileVersion());
    }

    /**
     * Append event facts to the timeline (append-only; valid_from = now).
     * <p>
     * 追加事件事实（append-only，valid_from = 当前时间）。
     */
    private void appendEvents(Long userId, List<String> events, Long sourceMsgId) {
        for (String event : events) {
            if (StringUtils.isBlank(event)) {
                continue;
            }
            UserProfileEvent row = new UserProfileEvent();
            row.setUuid(UuidUtil.createShort());
            row.setUserId(userId);
            row.setEvent(event.trim());
            row.setSourceMsgId(sourceMsgId == null ? 0L : sourceMsgId);
            row.setValidFrom(LocalDateTime.now());
            userProfileEventMapper.insert(row);
        }
        log.info("User profile events appended, userId:{}, count:{}", userId, events.size());
    }

    /**
     * Bi-temporal invalidation: superseded events keep their rows — only
     * {@code invalid_at} is stamped. History stays auditable and temporal
     * questions ("what did the user do before") remain answerable.
     * <p>
     * bi-temporal 失效：被取代事件只打 invalid_at，不物理删除——历史可审计，
     * 时序问题仍可回答。按原文精确匹配（LLM 被要求原样复制，改写则不命中，安全侧）。
     */
    private void invalidateEvents(Long userId, List<String> outdatedEvents) {
        List<UserProfileEvent> activeEvents = userProfileEventMapper.selectList(
                new LambdaQueryWrapper<UserProfileEvent>()
                        .eq(UserProfileEvent::getUserId, userId)
                        .isNull(UserProfileEvent::getInvalidAt)
                        .eq(UserProfileEvent::getIsDeleted, false));
        int closed = 0;
        for (UserProfileEvent row : activeEvents) {
            boolean matched = outdatedEvents.stream()
                    .filter(StringUtils::isNotBlank)
                    .anyMatch(text -> text.trim().equals(row.getEvent()));
            if (matched) {
                row.setInvalidAt(LocalDateTime.now());
                userProfileEventMapper.updateById(row);
                closed++;
            }
        }
        log.info("User profile events invalidated, userId:{}, closed:{}, candidates:{}",
                userId, closed, outdatedEvents.size());
    }
}
