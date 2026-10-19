package com.moyz.adi.common.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 二开新增（T1-05，2026-10-16）：单个知识库的 ACL 快照（库级，非 chunk 级）。
 * <p>
 * 由检索调用方从 KbInfoResp / KnowledgeBase 组装，经 {@link RetrieverContext} 传入
 * {@link KbAclFilterBuilder}，避免向量检索环节反查数据库。
 * <p>
 * ACL snapshot of one knowledge base at KB level, assembled by the caller and carried
 * through RetrieverContext so the retrieval path never needs a DB lookup.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KbAclInfo {

    private String kbUuid;

    private String ownerUuid;

    private Boolean isPublic;

    /** 库级默认密级：PUBLIC / ORG / PRIVATE */
    private String sensitivity;

    /** 库级默认可见组（原始组 id 列表，不含合成 owner 组） */
    private List<String> visibilityGroups;

    public static KbAclInfo of(String kbUuid, String ownerUuid, Boolean isPublic,
                               String sensitivity, List<String> visibilityGroups) {
        return KbAclInfo.builder()
                .kbUuid(kbUuid)
                .ownerUuid(ownerUuid)
                .isPublic(isPublic)
                .sensitivity(sensitivity)
                .visibilityGroups(visibilityGroups == null ? new ArrayList<>() : visibilityGroups)
                .build();
    }

    /**
     * 该库对当前用户是否可见（简化语义，与 chunk 级判定保持同源）：
     * owner 本人 / 显式 PUBLIC 或推导 PUBLIC（公开库）/ 可见组包含用户所属组。
     * TODO 组管理：正式组体系落地后把用户真实组并入 userGroups。
     */
    public boolean visibleTo(String userUuid, boolean isAdmin, List<String> userGroups) {
        if (isAdmin) {
            return true;
        }
        if (StringUtils.isNotBlank(userUuid) && userUuid.equals(ownerUuid)) {
            return true;
        }
        if (isPublicSensitivity(sensitivity) || Boolean.TRUE.equals(isPublic)) {
            return true;
        }
        return groupsOverlap(userGroups);
    }

    /** PUBLIC（或空配置推导为公开库）即对所有人可见 */
    private static boolean isPublicSensitivity(String sensitivity) {
        return com.moyz.adi.common.cosntant.AdiConstant.Sensitivity.PUBLIC.equals(sensitivity);
    }

    /** 用户所属组与库可见组是否有交集 */
    public boolean groupsOverlap(List<String> userGroups) {
        if (null == userGroups || userGroups.isEmpty() || null == visibilityGroups || visibilityGroups.isEmpty()) {
            return false;
        }
        return visibilityGroups.stream().anyMatch(userGroups::contains);
    }

    /** 解析 chunk metadata 里的可见组 CSV */
    public static List<String> parseGroupsCsv(String csv) {
        if (StringUtils.isBlank(csv)) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(StringUtils::isNotBlank).toList();
    }
}
