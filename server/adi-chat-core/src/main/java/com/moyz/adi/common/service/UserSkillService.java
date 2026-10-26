package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.UserSkill;
import com.moyz.adi.common.mapper.UserSkillMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户安装的技能（照 UserMcpService 范式）：市场安装记录 + 用户级启停。
 */
@Slf4j
@Service
public class UserSkillService extends ServiceImpl<UserSkillMapper, UserSkill> {

    public List<UserSkill> listByUserId(Long userId) {
        return this.lambdaQuery()
                .eq(UserSkill::getUserId, userId)
                .eq(UserSkill::getIsDeleted, false)
                .list();
    }

    public UserSkill getByUserAndSkill(Long userId, Long skillId) {
        return this.lambdaQuery()
                .eq(UserSkill::getUserId, userId)
                .eq(UserSkill::getSkillId, skillId)
                .eq(UserSkill::getIsDeleted, false)
                .one();
    }

    /**
     * 安装或更新启停（幂等：已删除的行原地复活）
     */
    public void saveOrUpdate(Long userId, Long skillId, Boolean isEnable) {
        UserSkill exist = getByUserAndSkill(userId, skillId);
        if (null == exist) {
            UserSkill userSkill = new UserSkill();
            userSkill.setUuid(com.moyz.adi.common.util.UuidUtil.createShort());
            userSkill.setUserId(userId);
            userSkill.setSkillId(skillId);
            userSkill.setIsEnable(isEnable);
            this.save(userSkill);
        } else {
            exist.setIsEnable(isEnable);
            exist.setIsDeleted(false);
            this.updateById(exist);
        }
    }

    public void remove(Long userId, Long skillId) {
        this.lambdaUpdate()
                .eq(UserSkill::getUserId, userId)
                .eq(UserSkill::getSkillId, skillId)
                .set(UserSkill::getIsDeleted, true)
                .update();
    }

    public Long currentUserId() {
        return ThreadContext.getCurrentUserId();
    }
}
