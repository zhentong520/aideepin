package com.moyz.adi.common.config.props;

import jakarta.validation.Valid;
import lombok.Data;

/**
 * 知识库配置项，含 ACL 子项。
 * <p>
 * Knowledge-base settings; holds the ACL switch.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Kb} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Kb {

    /**
     * 入库分块统一开关（adi.kb.unified-chunking，默认 true，T6-03）。
     * <p>
     * true 时所有知识库入库入口统一使用 025 实测参数：sentence_cn / 512 / overlap=0
     * （此前 qa 生成路径自用 1500 token 分块，与其余入口不一致）；
     * false 时保留历史行为（qa 生成按库策略 + 1500 token），用于回滚对照。
     * 只影响新索引；已生成的块与向量不受影响，无需重建即可回滚。
     * <p>
     * Unified chunking switch. When on, every KB ingestion entry shares the measured
     * 025 parameters (sentence_cn / 512 / overlap=0); the QA-generation path used to
     * chunk at 1500 tokens on its own. Turning it off restores the legacy behaviour
     * without rebuilding any index.
     */
    private boolean unifiedChunking = true;

    /**
     * ACL 过滤开关（adi.kb.acl.enabled，默认 false）。
     * <p>
     * false 时检索行为与历史版本完全一致；打开前必须先执行 T1-06 存量回填
     * （V6__acl_backfill.sql），否则未固化的存量 chunk 会被判定为不可见。
     * <p>
     * ACL filter switch. Disabled by default: with the switch off retrieval behaves exactly
     * as before. Run the T1-06 backfill before enabling, otherwise legacy chunks without
     * ACL metadata are treated as invisible.
     */
    @Valid
    private Acl acl = new Acl();

    /**
     * 二开新增（T7-05，2026-10-20）：embedding 全量重建开关（adi.kb.embedding-rebuild-enabled，默认 true）。
     * <p>
     * 控制「按知识库重建 embedding 索引」端点（管理端 /admin/kb/rebuildEmbedding/{uuid}
     * 与用户端同权端点）。换 embedding provider（如切 bge-m3）后的标准动作就是调它：
     * 失效全部文档段行与向量 → 按当前 provider 重新入队 → 记录 provider 版本（回滚依据）。
     * <p>
     * false 时端点直接拒绝（A_KB_EMBEDDING_REBUILD_DISABLED），行为与历史版本一致；
     * 关闭它不影响任何自动索引链路（保存文档/编辑参数触发的重索引照常）。
     * <p>
     * Embedding rebuild switch. Guards the per-KB rebuild endpoints; disabling them
     * refuses the call without touching the automatic indexing paths.
     */
    private boolean embeddingRebuildEnabled = true;

    /**
     * 二开新增（T7-06，2026-10-20）：知识包导出/导入与分享开关（adi.kb.pack-sharing-enabled，默认 true）。
     * <p>
     * 控制知识库「技能配置包」的导出端点（SKILL.md + config.json + pack.json 的 ZIP）与导入端点。
     * 语义约束（写进设计与测试）：<b>共享的是能力不是数据</b>——包里只有配置与引用清单，
     * 不含任何文档内容/向量；导入时引用数据按导入方自身 ACL 重新校验，无权即整体拒绝，
     * 绝不静默穿透。
     * <p>
     * false 时两个端点直接拒绝（A_PARAMS_ERROR），历史行为不受影响；热回滚位。
     * <p>
     * Knowledge pack sharing switch. Guards the KB pack export/import endpoints;
     * packs carry configuration only — referenced data is re-validated against the
     * importer's own ACL and denied references reject the whole import.
     */
    private boolean packSharingEnabled = true;
}
