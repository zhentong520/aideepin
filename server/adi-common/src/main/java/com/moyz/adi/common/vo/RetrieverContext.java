package com.moyz.adi.common.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检索上下文：把知识库级的检索配置从 Service 层透传到 RAG 召回/融合/重排各环节。
 * <p>
 * 二开新增（2026-09-15）：原链路只有 filter/maxResults/minScore，无法把「混合检索 + RRF + rerank」
 * 的开关传下去。此处作为只读载体，避免给单例 Bean 引入并发状态。
 */
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Data
public class RetrieverContext {

    /**
     * 知识库 UUID；为空表示非知识库场景（角色记忆等），走原链路
     */
    private String kbUuid;

    /**
     * 是否启用混合检索（向量 + 关键词 bigram，RRF 融合）
     */
    private boolean hybridEnabled;

    /**
     * RRF 融合常数 k，score = Σ 1/(k + rank)
     */
    private int rrfK;

    /**
     * 关键词路召回条数上限（融合前）
     */
    private int keywordLimit;

    /**
     * 是否启用重排
     */
    private boolean rerankEnabled;

    /**
     * 绑定的 rerank 模型 id（adi_ai_model.id）；0 表示取第一个启用的 rerank 模型
     */
    private Long rerankModelId;

    /**
     * 重排后保留 topN；0 表示不截断
     */
    private int rerankTopN;

    /**
     * 重排分数下限；低于该值的丢弃。0 表示不过滤
     */
    private double rerankMinScore;

    /**
     * 已停用的文档 UUID 集合。
     * <p>
     * 向量路通过 metadata 过滤（{@code excludedItemUuids}）排除停用文档，关键词路直接查
     * adi_document_segment，必须显式带上同一份排除名单——否则客户停用某文档后，其分段仍会
     * 被关键词路捞回并进入答案，与「停用即不参与检索」的预期直接冲突。
     */
    private java.util.Set<String> excludedDocUuids;

    /**
     * 是否把命中片段的相邻片段一并送入上下文（左右扩窗）。
     * <p>
     * 缓解「段边界切断语义」：条款及其例外、标题与其正文、表格与表头常常压在两段之间。
     * 只影响送进 prompt 的文本，不参与向量化，因此无需重建索引。
     */
    private boolean neighborEnabled;

    /**
     * 命中片段左右各取多少个相邻片段；0 或负数表示不扩窗。
     * <p>
     * 实测（73 条中文金标题库）±1 已拿到全部增益，±2 只多付 22% 上下文而零收益，故默认 1。
     */
    private int neighborWindow;

    /**
     * 无增强场景的统一空上下文
     */
    public static RetrieverContext none() {
        return RetrieverContext.builder()
                .hybridEnabled(false)
                .rerankEnabled(false)
                .rrfK(60)
                .keywordLimit(20)
                .rerankTopN(0)
                .rerankMinScore(0.0)
                .excludedDocUuids(java.util.Set.of())
                .neighborEnabled(false)
                .neighborWindow(DEFAULT_NEIGHBOR_WINDOW)
                .build();
    }

    /**
     * 扩窗默认半径：±1 段。见 {@link #neighborWindow}
     */
    public static final int DEFAULT_NEIGHBOR_WINDOW = 1;

    // ===== 二开新增（T1-05，2026-10-16）：检索主体与库级 ACL 快照 =====
    // 仅知识库链路填充（EmbeddingRag 只对 KNOWLEDGE_BASE 挂 context，角色记忆恒为 null），
    // 由 KbAclFilterBuilder 消费；检索在 mainExecutor 线程执行，ThreadContext 不可用，
    // 因此必须在创建 retriever 的同步阶段（web 线程）捕获主体信息。

    /**
     * 检索发起人 uuid；为空时 ACL 过滤不生效（放行）。
     * 检索日志（T1-01）的 user_uuid 也取自此字段。
     */
    private String userUuid;

    /** 是否管理员（管理员全通过） */
    private Boolean userIsAdmin;

    /** 用户所属可见组（TODO 组管理：正式组体系落地后填充，当前恒空 = 只有 owner 语义） */
    private java.util.List<String> userGroups;

    /** 本次检索的知识库 uuid 列表（检索日志 kb_uuids 字段来源） */
    private java.util.List<String> kbUuids;

    /**
     * 库级 ACL 快照：kbUuid → KbAclInfo。由调用方组装（CharacterChatHelper 从 KbInfoResp 构建），
     * 或由 KbAclFilterBuilder.enrichAclContext 按单库反查补齐。
     */
    private java.util.Map<String, com.moyz.adi.common.rag.KbAclInfo> aclInfos;
}
