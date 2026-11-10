package com.moyz.adi.common.memory.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 二开新增（P0-1，2026-11-03）：画像合并轮的 LLM 输出结构。
 * <p>
 * {@code profile} 是合并后的【全量】画像维度对象（不是增量 patch——全量回写
 * 天然幂等，坏输出可整行回滚）；{@code events} 是本轮新出现的事件性事实；
 * {@code outdatedEvents} 是被新事实取代的旧事件原文（服务层按原文精确匹配
 * 后置 {@code invalid_at}，实现 bi-temporal 失效，绝不物理删除）。
 */
@Data
public class ProfileMergeResult {

    private JsonNode profile;

    private List<String> events = new ArrayList<>();

    private List<String> outdatedEvents = new ArrayList<>();
}
