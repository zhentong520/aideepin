package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.KnowledgeBaseShare;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.mapper.KnowledgeBaseShareMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识库细粒度共享服务（二开新增）。
 * <p>
 * 在 owner / public / admin 三态之上，增加按用户的显式授权，支撑「前端按授权看知识库」：
 * 被共享的用户即使不是 owner、库也不公开，也能看到并使用该库。
 */
@Slf4j
@Service
public class KbShareService extends ServiceImpl<KnowledgeBaseShareMapper, KnowledgeBaseShare> {

    public static final String TARGET_USER = "USER";
    public static final String PERMISSION_READ = "READ";
    public static final String PERMISSION_WRITE = "WRITE";

    /**
     * 授权某用户访问知识库（覆盖式：先删后插，幂等）。
     */
    public void grant(String kbUuid, Long targetId, String permission) {
        baseMapper.deleteByKbAndTarget(kbUuid, TARGET_USER, targetId);
        User user = ThreadContext.getCurrentUser();
        KnowledgeBaseShare share = new KnowledgeBaseShare();
        share.setKbUuid(kbUuid);
        share.setTargetType(TARGET_USER);
        share.setTargetId(targetId);
        share.setPermission(permission);
        share.setGrantorId(user == null ? null : user.getId());
        share.setGrantorName(user == null ? null : user.getName());
        baseMapper.insert(share);
    }

    public void revoke(String kbUuid, Long targetId) {
        baseMapper.deleteByKbAndTarget(kbUuid, TARGET_USER, targetId);
    }

    public List<KnowledgeBaseShare> listByKb(String kbUuid) {
        return baseMapper.listByKb(kbUuid);
    }

    /**
     * 当前用户被共享到的所有知识库 uuid（去重）。
     */
    public List<String> listKbUuidsForUser(Long userId) {
        return baseMapper.listKbUuidsForUser(userId);
    }

    public boolean hasAccess(String kbUuid, Long userId) {
        if (null == userId) {
            return false;
        }
        return baseMapper.listKbUuidsForUser(userId).contains(kbUuid);
    }
}
