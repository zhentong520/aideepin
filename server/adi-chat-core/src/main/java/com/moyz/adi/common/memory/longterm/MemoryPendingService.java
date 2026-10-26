package com.moyz.adi.common.memory.longterm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.MemoryPending;
import com.moyz.adi.common.enums.MemoryType;
import com.moyz.adi.common.mapper.MemoryPendingMapper;
import com.moyz.adi.common.memory.vo.ExtractedEpisodicEvent;
import com.moyz.adi.common.util.UuidUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 二开新增（T7-07，2026-10-20）：敏感（PII）记忆 pending → confirm 两段式服务。
 * <p>
 * 写入侧：{@link #createPending} 由 {@code LongTermMemoryService} 在抽取结果命中
 * {@link SensitiveMemoryDetector} 时调用——只落本表 PENDING，绝不触碰向量库；
 * 确认侧：用户通过 API（ adi-chat 的 MemoryPendingController）调用
 * {@link #confirm}/{@link #reject}。confirm 按 memory_type 路由写对应向量库
 * （semantic → SemanticMemoryService.addConfirmed，episodic → EpisodicMemoryService.addConfirmedEvent）。
 * <p>
 * ⚠️ 隔离铁律：confirm/reject/list 全部以当前登录用户过滤——
 * <b>共享的是"写入资格确认"能力，不是数据</b>：A 用户的 pending 行对 B 用户不存在，
 * B 无法替 A 确认任何敏感记忆入库（单测 MemoryPendingServiceTest 断言）。
 * 查询/确认时校验 user_id 匹配，不匹配按不存在处理（不泄露行是否存在）。
 */
@Slf4j
@Service
public class MemoryPendingService extends ServiceImpl<MemoryPendingMapper, MemoryPending> {

    @Resource
    private SemanticMemoryService semanticMemoryService;

    @Resource
    private EpisodicMemoryService episodicMemoryService;

    /**
     * Record a sensitive memory awaiting user confirmation. No vector-store write
     * happens here by design — persistence is deferred until {@link #confirm}.
     * <p>
     * 登记一条待确认的敏感记忆。此处刻意不做任何向量库写入——持久化推迟到 confirm。
     *
     * @return the created PENDING row (never null)
     */
    public MemoryPending createPending(Long userId, Long characterId, MemoryType memoryType,
                                       String content, Long sourceMsgId) {
        MemoryPending pending = new MemoryPending();
        pending.setMemoryUuid(UuidUtil.createShort());
        pending.setUserId(userId);
        pending.setCharacterId(null == characterId ? 0L : characterId);
        pending.setMemoryType(null == memoryType ? MemoryType.SEMANTIC : memoryType);
        pending.setContent(null == content ? "" : content);
        pending.setStatus(MemoryPending.STATUS_PENDING);
        pending.setSourceMsgId(null == sourceMsgId ? 0L : sourceMsgId);
        this.save(pending);
        log.info("Sensitive memory pending created, uuid={}, userId={}, type={}",
                pending.getMemoryUuid(), userId, pending.getMemoryType());
        return pending;
    }

    /**
     * List the current user's pending rows (oldest first for a natural confirm flow).
     * Rows of other users are invisible — isolation is enforced in the query itself.
     * <p>
     * 列出当前登录用户的待确认记忆（按创建时间正序）。他人的行在查询条件里就不可见。
     */
    public List<MemoryPending> listMyPending() {
        Long userId = ThreadContext.getCurrentUserId();
        LambdaQueryWrapper<MemoryPending> qw = new LambdaQueryWrapper<>();
        qw.eq(MemoryPending::getUserId, userId)
                .eq(MemoryPending::getStatus, MemoryPending.STATUS_PENDING)
                .orderByAsc(MemoryPending::getId);
        return this.list(qw);
    }

    /**
     * Confirm a pending row: verify ownership, then persist to the vector store routed
     * by memory_type. Returns the updated row.
     * <p>
     * 确认：先校验归属（非本人按不存在处理），再按 memory_type 写入对应向量库。
     *
     * @throws IllegalArgumentException 行不存在 / 不属于当前用户 / 状态不是 PENDING
     */
    public MemoryPending confirm(String memoryUuid) {
        MemoryPending pending = getOwnedPendingOrThrow(memoryUuid);
        if (!MemoryPending.STATUS_PENDING.equals(pending.getStatus())) {
            throw new IllegalArgumentException("Memory pending is not in PENDING state, uuid:" + memoryUuid);
        }
        Long userId = pending.getUserId();
        switch (pending.getMemoryType()) {
            case EPISODIC -> {
                ExtractedEpisodicEvent event = new ExtractedEpisodicEvent();
                event.setSummary(pending.getContent());
                episodicMemoryService.addConfirmedEvent(pending.getCharacterId(), userId,
                        pending.getSourceMsgId() != null && pending.getSourceMsgId() > 0 ? pending.getSourceMsgId() : null,
                        event);
            }
            case SEMANTIC -> semanticMemoryService.addConfirmed(pending.getCharacterId(), userId, pending.getContent());
            default -> throw new IllegalArgumentException("Unsupported memory type: " + pending.getMemoryType());
        }
        pending.setStatus(MemoryPending.STATUS_CONFIRMED);
        this.updateById(pending);
        log.info("Sensitive memory confirmed and persisted, uuid={}, userId={}, type={}",
                memoryUuid, userId, pending.getMemoryType());
        return pending;
    }

    /**
     * Reject a pending row: ownership-checked, marks REJECTED — content never reaches
     * any vector store.
     * <p>
     * 驳回：校验归属后标记 REJECTED——内容永不进入任何向量库。
     */
    public MemoryPending reject(String memoryUuid) {
        MemoryPending pending = getOwnedPendingOrThrow(memoryUuid);
        if (!MemoryPending.STATUS_PENDING.equals(pending.getStatus())) {
            throw new IllegalArgumentException("Memory pending is not in PENDING state, uuid:" + memoryUuid);
        }
        pending.setStatus(MemoryPending.STATUS_REJECTED);
        this.updateById(pending);
        log.info("Sensitive memory rejected, uuid={}, userId={}", memoryUuid, pending.getUserId());
        return pending;
    }

    /**
     * Load by uuid and enforce ownership against the current login user.
     * Mismatching user gets the same "not found" error — existence is not leaked.
     * <p>
     * 按 uuid 加载并校验归属。非本人与不存在报同样的错——不泄露行是否存在。
     */
    private MemoryPending getOwnedPendingOrThrow(String memoryUuid) {
        if (StringUtils.isBlank(memoryUuid)) {
            throw new IllegalArgumentException("memory uuid is blank");
        }
        Long userId = ThreadContext.getCurrentUserId();
        MemoryPending pending = this.getOne(new LambdaQueryWrapper<MemoryPending>()
                .eq(MemoryPending::getMemoryUuid, memoryUuid)
                .eq(MemoryPending::getIsDeleted, false));
        if (null == pending || !userId.equals(pending.getUserId())) {
            // 归属校验失败与不存在同一报错：B 用户拿 A 的 uuid 试探也只会得到 404 语义
            throw new IllegalArgumentException("Memory pending not found, uuid:" + memoryUuid);
        }
        return pending;
    }
}
