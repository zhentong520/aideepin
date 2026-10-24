package com.moyz.adi.common.multiagent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.CharacterService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * T7-02（2026-10-20）：委派目标 Agent 解析 + ACL 校验。
 * <p>
 * 目标 Agent 按 <b>名称</b>（Character title，大小写不敏感）或 <b>id</b>（Character uuid）解析；
 * 解析成功后按当前用户做 ACL 校验：
 * <ul>
 *   <li>目标 Agent 属于当前用户（userId 相等）→ 允许；</li>
 *   <li>目标 Agent 为系统预置（userId 为空或 0）→ 允许；</li>
 *   <li>其余（他人私有 Agent）→ 拒绝，抛 {@link com.moyz.adi.common.multiagent.AgentNotAllowedException}
 *       的错误信息只说明「无权访问该 Agent」，不泄露目标是否存在之外的信息。</li>
 * </ul>
 * 解析失败（按名称/id 都找不到、已删除）抛参数错误，错误信息带上用户输入的名称/id，
 * 保证「配错了」能一眼定位。
 * <p>
 * Resolves the delegation target agent by name or uuid and enforces per-user ACL:
 * only the owner's own agents and system-preset agents (userId empty/0) may be delegated to.
 */
@Component
public class DelegationAgentResolver {

    @Resource
    private CharacterService characterService;

    /**
     * 解析目标 Agent 并按当前用户做 ACL 校验。
     *
     * @param nameOrUuid 目标 Agent 名称或 uuid（名称优先，其次 uuid 精确匹配）
     * @param user       当前用户（工作流/对话上下文传入）
     * @return 通过 ACL 校验的目标 Agent
     */
    public Character resolve(String nameOrUuid, User user) {
        String key = StringUtils.trimToEmpty(nameOrUuid);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("delegation target agent (name or uuid) is empty");
        }
        Character character = findByNameOrUuid(key);
        if (null == character) {
            throw new IllegalArgumentException("delegation target agent not found: " + key);
        }
        if (!isAccessible(character, user)) {
            throw new AgentNotAllowedException(key);
        }
        return character;
    }

    private Character findByNameOrUuid(String key) {
        // 先按 uuid 精确匹配，再按 title 匹配（大小写不敏感），均要求未删除
        Character byUuid = characterService.lambdaQuery()
                .eq(Character::getUuid, key)
                .eq(Character::getIsDeleted, false)
                .oneOpt()
                .orElse(null);
        if (null != byUuid) {
            return byUuid;
        }
        return characterService.lambdaQuery()
                .eq(Character::getIsDeleted, false)
                .and(w -> w.eq(Character::getTitle, key).or().apply("lower(title) = lower({0})", key))
                .last("limit 1")
                .oneOpt()
                .orElse(null);
    }

    private boolean isAccessible(Character character, User user) {
        if (null == user) {
            return false;
        }
        Long ownerId = character.getUserId();
        // 系统预置 Agent（无归属用户）对所有登录用户开放
        if (null == ownerId || ownerId <= 0) {
            return true;
        }
        return ownerId.equals(user.getId());
    }
}
