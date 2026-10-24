package com.moyz.adi.common.rag;

import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.entity.User;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.util.KbAclMetadata;
import com.moyz.adi.common.util.SpringUtil;
import com.moyz.adi.common.vo.RetrieverContext;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.logical.Or;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.store.embedding.filter.Filter.and;

/**
 * 二开新增（T1-05，2026-10-16）：检索侧 ACL 过滤 + 应用层双保险的唯一实现。
 * <p>
 * <b>开关</b>：{@code adi.kb.acl.enabled}（默认 false，铁律 10）。关闭时本类所有方法
 * 都是恒等操作（原样返回 base filter / 恒 true），检索行为与历史版本完全一致。
 * <p>
 * <b>向量层过滤</b>（{@link #wrapFilter}）：在调用方现有 Filter（如
 * {@code IsIn(KB_UUID, kbUuids)}）上追加
 * {@code Or(acl_sensitivity=PUBLIC, kb_owner_uuid=当前用户)}。langchain4j Filter 只支持
 * 相等比较，无法表达"owner ∈ 可见组 CSV"，owner 可见性靠独立的 kb_owner_uuid 键；
 * 组（含 ORG 档的组可见性）无法进向量层，由库选择层 + 应用层双保险兜底（TODO 组管理）。
 * <p>
 * <b>双保险</b>（{@link #isAllowed}）：检索结果取回后按 chunk metadata 再断言一次；
 * metadata 缺失（存量未回填）时回退库级快照判定，快照也缺失则 fail-closed 并 warn。
 * 剔除的条数计入检索日志的 acl_filtered_count（T1-01）。
 * <p>
 * <b>简化语义</b>（TODO 组管理后升级）：当前用户的可见组只有合成组
 * {@code owner:<uuid>}（owner 自身），管理员全通过；即用户对「自己 owner 的库 +
 * sensitivity=PUBLIC 的库（含公开库推导）」可见。
 */
@Slf4j
public final class KbAclFilterBuilder {

    /** 单测直控开关（Spring 上下文不可用时 enabled() 的逃生门），生产恒为 null */
    private static volatile Boolean enabledForTest;

    private KbAclFilterBuilder() {
    }

    public static void setEnabledForTest(Boolean value) {
        enabledForTest = value;
    }

    /**
     * 读 adi.kb.acl.enabled。无 Spring 上下文（单测/工具场景）时恒 false，即关闭状态。
     */
    public static boolean enabled() {
        Boolean override = enabledForTest;
        if (null != override) {
            return override;
        }
        try {
            AdiProperties properties = SpringUtil.getBean(AdiProperties.class);
            return null != properties.getKb()
                    && null != properties.getKb().getAcl()
                    && properties.getKb().getAcl().isEnabled();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 在调用方 Filter 上追加 ACL 条件。与现有 {@code IsIn(KB_UUID, kbUuids)}
     * 组合方式保持同风格（EmbeddingRag 里 excludedItemUuids 也用 Filter.and 组合）。
     *
     * @param base       调用方既有 Filter，可为 null
     * @param ctx        检索上下文（携带用户与库 ACL 快照），null 视为非 KB 链路
     * @param aclEnabled 开关（显式传入便于单测；生产传 {@link #enabled()}）
     */
    public static Filter wrapFilter(Filter base, RetrieverContext ctx, boolean aclEnabled) {
        if (!aclEnabled || null == ctx
                || StringUtils.isBlank(ctx.getUserUuid())
                || Boolean.TRUE.equals(ctx.getUserIsAdmin())) {
            return base;
        }
        // 向量层只放行 PUBLIC chunk 与 owner 自己的 chunk；非 owner 的 ORG/PRIVATE 一律拦截，
        // 组可见性（可见组命中的 ORG 库）暂由库选择层与应用层双保险兜底（TODO 组管理）。
        Filter acl = new Or(
                new IsEqualTo(AdiConstant.MetadataKey.ACL_SENSITIVITY, AdiConstant.Sensitivity.PUBLIC),
                new IsEqualTo(AdiConstant.MetadataKey.KB_OWNER_UUID, ctx.getUserUuid()));
        return null == base ? acl : and(base, acl);
    }

    public static Filter wrapFilter(Filter base, RetrieverContext ctx) {
        return wrapFilter(base, ctx, enabled());
    }

    /**
     * 应用层双保险：对单条命中按 chunk metadata 再断言一次。
     *
     * @return true = 放行；false = 剔除（调用方负责 warn 与计数）
     */
    public static boolean isAllowed(Metadata metadata, RetrieverContext ctx, boolean aclEnabled) {
        if (!aclEnabled) {
            return true;
        }
        if (null == ctx) {
            return true;
        }
        String userUuid = ctx.getUserUuid();
        if (StringUtils.isBlank(userUuid) || Boolean.TRUE.equals(ctx.getUserIsAdmin())) {
            return true;
        }
        if (null != metadata && metadata.containsKey(AdiConstant.MetadataKey.ACL_SENSITIVITY)) {
            if (AdiConstant.Sensitivity.PUBLIC.equals(metadata.getString(AdiConstant.MetadataKey.ACL_SENSITIVITY))) {
                return true;
            }
            String owner = metadata.containsKey(AdiConstant.MetadataKey.KB_OWNER_UUID)
                    ? metadata.getString(AdiConstant.MetadataKey.KB_OWNER_UUID) : null;
            if (StringUtils.isNotBlank(owner) && userUuid.equals(owner)) {
                return true;
            }
            List<String> userGroups = null == ctx.getUserGroups() ? List.of() : ctx.getUserGroups();
            List<String> chunkGroups = KbAclInfo.parseGroupsCsv(
                    metadata.containsKey(AdiConstant.MetadataKey.ACL_VISIBILITY_GROUPS)
                            ? metadata.getString(AdiConstant.MetadataKey.ACL_VISIBILITY_GROUPS) : null);
            return chunkGroups.stream().anyMatch(userGroups::contains);
        }
        // chunk metadata 缺失（存量未回填/未固化）→ 库级快照兜底；快照也没有则 fail-closed
        String kbUuid = null != metadata && metadata.containsKey(AdiConstant.MetadataKey.KB_UUID)
                ? metadata.getString(AdiConstant.MetadataKey.KB_UUID) : null;
        KbAclInfo info = MapUtils.isEmpty(ctx.getAclInfos()) || null == kbUuid
                ? null : ctx.getAclInfos().get(kbUuid);
        if (null == info) {
            log.warn("ACL double-check: chunk without ACL metadata and no KB snapshot, deny,kbUuid:{}", kbUuid);
            return false;
        }
        return info.visibleTo(userUuid, Boolean.TRUE.equals(ctx.getUserIsAdmin()), ctx.getUserGroups());
    }

    public static boolean isAllowed(Metadata metadata, RetrieverContext ctx) {
        return isAllowed(metadata, ctx, enabled());
    }

    /**
     * 为未显式携带 ACL 信息的 KB 检索入口（QA 问答 / 工作流节点等）补齐上下文：
     * 从 ThreadContext 取当前用户，按 kbUuid 反查库行组装快照。全程 try/catch，
     * 任何失败只 warn——ACL 上下文缺失时 {@link #wrapFilter} 会原样放行（fail-open），
     * 上线前必须确认 adi.kb.acl.enabled 打开时所有入口都能取到上下文。
     */
    public static void enrichAclContext(RetrieverContext ctx, String kbUuid) {
        if (!enabled() || null == ctx) {
            return;
        }
        try {
            if (StringUtils.isBlank(ctx.getUserUuid())) {
                User user = ThreadContext.getCurrentUser();
                if (null != user) {
                    ctx.setUserUuid(user.getUuid());
                    ctx.setUserIsAdmin(Boolean.TRUE.equals(user.getIsAdmin()));
                }
            }
            if (MapUtils.isEmpty(ctx.getAclInfos()) && StringUtils.isNotBlank(kbUuid)) {
                KnowledgeBase kb = SpringUtil.getBean(KnowledgeBaseService.class).getOrThrow(kbUuid);
                ctx.setAclInfos(Map.of(kb.getUuid(), toAclInfo(kb)));
                if (null == ctx.getKbUuids() || ctx.getKbUuids().isEmpty()) {
                    ctx.setKbUuids(List.of(kb.getUuid()));
                }
            }
        } catch (Exception e) {
            log.warn("enrichAclContext failed,kbUuid:{},error:{}", kbUuid, e.getMessage());
        }
    }

    /**
     * KnowledgeBase → KbAclInfo（含 jsonb 可见组解析，规则与 KbAclMetadata 一致）。
     */
    public static KbAclInfo toAclInfo(KnowledgeBase kb) {
        List<String> groups = new ArrayList<>();
        if (null != kb.getDefaultVisibilityGroups() && kb.getDefaultVisibilityGroups().isArray()) {
            kb.getDefaultVisibilityGroups().forEach(node -> {
                String g = node.asText("");
                if (StringUtils.isNotBlank(g)) {
                    groups.add(g);
                }
            });
        }
        return KbAclInfo.of(kb.getUuid(), kb.getOwnerUuid(), kb.getIsPublic(),
                kb.getDefaultSensitivity(), groups);
    }

    /**
     * 库级密级推导（写侧 KbAclMetadata.effectiveSensitivity 的同源副本，供调用方组装快照用）。
     */
    public static String effectiveSensitivity(KnowledgeBase kb) {
        return KbAclMetadata.effectiveSensitivity(kb);
    }
}
