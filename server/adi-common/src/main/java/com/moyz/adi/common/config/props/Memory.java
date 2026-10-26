package com.moyz.adi.common.config.props;

import com.moyz.adi.common.constant.MetadataConstant;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

/**
 * 长期记忆配置项。
 * <p>
 * Long-term memory configuration.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Memory} 嵌套类提为顶层类，Spring 绑定键不变；
 * 默认值常量由 {@code AdiConstant.LONG_TERM_MEMORY_MIN_SCORE_DEFAULT} 改指
 * {@link MetadataConstant}（同一常量的域接口名）。
 */
@Data
public class Memory {
    /**
     * 向量检索旧记忆时的最低相似度阈值。默认 {@link MetadataConstant#LONG_TERM_MEMORY_MIN_SCORE_DEFAULT}。
     * <p>
     * Minimum similarity score for retrieving existing memories during extraction.
     */
    @DecimalMin(value = "0.0", message = "adi.memory.min-score 应在 0-1 之间")
    @DecimalMax(value = "1.0", message = "adi.memory.min-score 应在 0-1 之间")
    private double minScore = MetadataConstant.LONG_TERM_MEMORY_MIN_SCORE_DEFAULT;

    /**
     * 二开新增（T7-07，2026-10-20）：记忆用户维度开关（adi.memory.user-dimension-enabled，默认 true）。
     * <p>
     * 打开时：写入路径在 metadata 里记录 {@code user_id}（同一用户跨 Agent 跨会话共享记忆），
     * 检索路径按 {@code character_id + user_id} 双条件过滤（A 用户读不到 B 用户的记忆）。
     * 关闭时：完全不写也不过滤 {@code user_id}，行为回退到历史上的 character 单维度
     * （存量数据已由 V14 迁移按 owner 关系回填，关闭开关不会丢数据，只是不再按用户隔离）。
     * <p>
     * Per-user memory dimension switch. When off, no user_id is written or filtered
     * and retrieval falls back to the historical character-only dimension.
     */
    private boolean userDimensionEnabled = true;

    /**
     * 二开新增（T7-07，2026-10-20）：敏感记忆写入前确认开关（adi.memory.pii-confirm-enabled，默认 true）。
     * <p>
     * 打开时：抽取结果中命中 PII 特征（联系方式/证件/账号密码）的记忆不直接落库，
     * 先进入 pending 状态等用户在 API 层确认（pending → confirm 两段式），确认后才持久化；
     * 非敏感记忆照常直写。关闭时：全部直写（历史行为），热回滚位。
     * <p>
     * Sensitive-memory confirmation switch. When off, all extracted memories are
     * persisted directly (legacy behaviour); when on, PII memories require explicit
     * user confirmation before being persisted.
     */
    private boolean piiConfirmEnabled = true;
}
