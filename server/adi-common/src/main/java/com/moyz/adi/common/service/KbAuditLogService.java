package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.KnowledgeBaseAuditLog;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.mapper.KnowledgeBaseAuditLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 知识库操作审计服务（二开新增）。
 * <p>
 * 设计原则：审计是「旁路」，任何异常都被吞掉，绝不影响主业务流程（上传/删除/重索引等）。
 */
@Slf4j
@Service
public class KbAuditLogService extends ServiceImpl<KnowledgeBaseAuditLogMapper, KnowledgeBaseAuditLog> {

    // 操作类型
    public static final String ACTION_KB_CREATE = "KB_CREATE";
    public static final String ACTION_KB_UPDATE = "KB_UPDATE";
    public static final String ACTION_KB_DELETE = "KB_DELETE";
    public static final String ACTION_KB_DOC_UPLOAD = "KB_DOC_UPLOAD";
    public static final String ACTION_DOC_CREATE = "DOC_CREATE";
    public static final String ACTION_DOC_UPDATE = "DOC_UPDATE";
    public static final String ACTION_DOC_DELETE = "DOC_DELETE";
    public static final String ACTION_DOC_TOGGLE = "DOC_TOGGLE";
    public static final String ACTION_DOC_REINDEX = "DOC_REINDEX";
    public static final String ACTION_DOC_OCR = "DOC_OCR";
    public static final String ACTION_SHARE_GRANT = "SHARE_GRANT";
    public static final String ACTION_SHARE_REVOKE = "SHARE_REVOKE";
    public static final String ACTION_AUDIT_VIEW = "AUDIT_VIEW";

    // 审计目标类型
    public static final String TARGET_KB = "KB";
    public static final String TARGET_DOC = "DOC";
    public static final String TARGET_SHARE = "SHARE";

    public void record(String kbUuid, String kbName, String action, String targetType,
                       String targetUuid, boolean success, String detail) {
        try {
            User user = ThreadContext.getCurrentUser();
            KnowledgeBaseAuditLog log = new KnowledgeBaseAuditLog();
            log.setKbUuid(kbUuid);
            log.setKbName(kbName);
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetUuid(targetUuid);
            log.setOperatorId(user == null ? null : user.getId());
            log.setOperatorName(user == null ? null : user.getName());
            log.setSuccess(success);
            log.setDetail(detail);
            baseMapper.insert(log);
        } catch (Exception e) {
            log.error("kb audit log insert failed,action:{},kbUuid:{}", action, kbUuid, e);
        }
    }

    /**
     * 按知识库分页拉取审计流水（二开新增）。供「审计」面板展示，调用方须已校验写权限。
     */
    public Page<KnowledgeBaseAuditLog> pageByKb(String kbUuid, int currentPage, int pageSize) {
        Page<KnowledgeBaseAuditLog> page = new Page<>(Math.max(currentPage, 1), Math.max(pageSize, 1));
        LambdaQueryWrapper<KnowledgeBaseAuditLog> wrapper = new LambdaQueryWrapper<KnowledgeBaseAuditLog>()
                .eq(KnowledgeBaseAuditLog::getKbUuid, kbUuid)
                .orderByDesc(KnowledgeBaseAuditLog::getCreateTime);
        return this.page(page, wrapper);
    }
}
