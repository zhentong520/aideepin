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

    /**
     * 二开新增（S2-04，2026-10-28）：短期记忆存储后端开关（adi.memory.store，mapdb | pg，默认 mapdb）。
     * <p>
     * pg 模式：短期记忆落 Postgres（chat_memory 表，一行一会话）+ Redis 读缓存，
     * 多实例共享、重启不丢；首次读取某会话时若 pg 无记录而 MapDB 文件有，按会话
     * 自动搬迁（灰度，不整库一次性迁）。mapdb 模式：历史行为（单机文件，多实例不共享），
     * 作为回滚安全位保留。全量搬迁确认后 mapdb 分支与 org.mapdb 依赖一起删除。
     * <p>
     * Short-term memory backend switch. "pg" stores message windows in Postgres with a
     * Redis read cache (multi-instance safe, restart-safe) and lazily migrates per-session
     * legacy MapDB data on first read; "mapdb" (default) keeps the legacy single-node
     * behaviour as a rollback position.
     */
    private String store = "mapdb";

    /**
     * 二开新增（P0-1，2026-11-03）：结构化用户画像开关（adi.memory.profile-enabled，默认 true）。
     * <p>
     * 打开时：长期记忆抽取轮追加一次 LLM 合并调用，把新事实合并进
     * {@code adi_user_profile.profile}（JSON 维度对象）并追加事件时间线
     * {@code adi_user_profile_event}；聊天注入时纯 SQL 读画像（零 embedding）。
     * 关闭时：不建画像、不注入（热回滚位，向量记忆链路不受影响）。
     * <p>
     * Structured user profile switch. When on, each extraction round merges new
     * facts into the per-user JSON profile + event timeline via one extra LLM call;
     * profile is injected into prompts with a pure-SQL read (no embedding).
     */
    private boolean profileEnabled = true;

    /**
     * 二开新增（P0-2，2026-11-03）：语义记忆 bi-temporal 软关闭开关
     * （adi.memory.bitemporal-enabled，默认 true）。
     * <p>
     * 打开时：UPDATE/DELETE 不物理删除向量行——旧行改打
     * {@code memory_valid="false"} + {@code invalid_at}（同 id 同向量重写，
     * 不重跑 embedding），保留原始事实供审计与时序分析；检索与合并候选一律
     * 排除 memory_valid="false" 的行。关闭时：物理删除（历史行为，回滚位）。
     * <p>
     * Bi-temporal soft-close switch. When on, UPDATE/DELETE soft-closes the old
     * vector row (same id/vector rewrite with validity markers, no re-embedding);
     * when off, physical removal (legacy behaviour).
     */
    private boolean bitemporalEnabled = true;

    /**
     * 二开新增（P0-3，2026-11-03）：记忆注入 token 预算的字符近似上限
     * （adi.memory.inject-budget-chars，默认 1200 ≈ 400~600 token）。
     * <p>
     * 语义记忆与情景记忆两个通道合并进 prompt 的总字符数上限（中文 1 token≈2~3 字符）：
     * 预算内优先保语义记忆，剩余给情景记忆；超出截断。0 或负数 = 不限（历史行为）。
     * <p>
     * Injection budget: max total chars of semantic+episodic memory injected into
     * the prompt (semantic first, episodic gets the remainder). Non-positive = unlimited.
     */
    private int injectBudgetChars = 1200;
}
