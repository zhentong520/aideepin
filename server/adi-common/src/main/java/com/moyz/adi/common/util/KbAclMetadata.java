package com.moyz.adi.common.util;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.KnowledgeBase;
import dev.langchain4j.data.document.Metadata;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 二开新增（T1-04，2026-10-16）：把库级 ACL 默认值固化进 chunk metadata。
 * <p>
 * 写入点（只写 metadata，不参与向量化、不重跑 embedding）：
 * <ul>
 *   <li>{@code SegmentIndexService#baseMetadata}（普通/父子分块入库链路）</li>
 *   <li>{@code DocumentQaModeService#generateQaAsync}（QA 模式问题向量化链路）</li>
 *   <li>存量数据由 T1-06（V6__acl_backfill.sql / 管理端一键回填）补齐</li>
 * </ul>
 * 密级推导规则（写入侧与回填侧必须完全一致，见 V6 的 CASE 表达式）：
 * 显式配置 PUBLIC/ORG 时采用；否则 is_public=true 视为 PUBLIC（保证回填前后
 * 公开库检索行为零变化），其余为 PRIVATE。
 */
public class KbAclMetadata {

    private KbAclMetadata() {
    }

    /**
     * 把 ACL 三个键写入 chunk metadata。kb 为 null 时静默跳过（兼容无库上下文的调用）。
     */
    public static void inject(Metadata metadata, KnowledgeBase kb) {
        if (null == metadata || null == kb) {
            return;
        }
        metadata.put(AdiConstant.MetadataKey.ACL_SENSITIVITY, effectiveSensitivity(kb));
        metadata.put(AdiConstant.MetadataKey.ACL_VISIBILITY_GROUPS, visibilityGroupsCsv(kb));
        metadata.put(AdiConstant.MetadataKey.KB_OWNER_UUID, StringUtils.defaultString(kb.getOwnerUuid()));
    }

    /**
     * 库的有效密级。规则与 V6__acl_backfill.sql 的 CASE 表达式一一对应。
     */
    public static String effectiveSensitivity(KnowledgeBase kb) {
        String configured = kb.getDefaultSensitivity();
        if (AdiConstant.Sensitivity.PUBLIC.equals(configured) || AdiConstant.Sensitivity.ORG.equals(configured)) {
            return configured;
        }
        return Boolean.TRUE.equals(kb.getIsPublic()) ? AdiConstant.Sensitivity.PUBLIC : AdiConstant.Sensitivity.PRIVATE;
    }

    /**
     * 可见组 CSV：库配置的默认可见组 + 合成 owner 组（{@code owner:<uuid>}）。
     * langchain4j Metadata 只支持标量值，数组折叠为 CSV 存储；
     * 组的包含判断在应用层双保险（KbAclFilterBuilder）做，向量过滤用独立的 kb_owner_uuid 键。
     */
    public static String visibilityGroupsCsv(KnowledgeBase kb) {
        List<String> groups = new ArrayList<>();
        if (null != kb.getDefaultVisibilityGroups() && kb.getDefaultVisibilityGroups().isArray()) {
            kb.getDefaultVisibilityGroups().forEach(node -> {
                String g = node.asText("");
                if (StringUtils.isNotBlank(g)) {
                    groups.add(g);
                }
            });
        }
        String ownerGroup = ownerGroupOf(kb.getOwnerUuid());
        if (StringUtils.isNotBlank(ownerGroup) && !groups.contains(ownerGroup)) {
            groups.add(ownerGroup);
        }
        return String.join(",", groups);
    }

    /**
     * 合成 owner 组 id。TODO 组管理：正式组体系落地后，把用户的真实组并入可见组集合，
     * 该合成组仅作为 owner 兜底保留。
     */
    public static String ownerGroupOf(String ownerUuid) {
        return StringUtils.isBlank(ownerUuid) ? "" : "owner:" + ownerUuid;
    }
}
