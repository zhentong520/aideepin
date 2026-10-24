package com.moyz.adi.common.service;

import com.moyz.adi.common.constant.AdiConstant;
import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.util.KbAclMetadata;
import dev.langchain4j.data.document.Metadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T1-04 验收：分块入库时把库级 ACL 固化进 chunk metadata。
 * 写入点（真实调用处，见 KbAclMetadata 类注释）：
 * - SegmentIndexService#baseMetadata（约 :505，普通/父子分块链路）
 * - DocumentQaModeService#generateQaAsync（约 :410，QA 问题向量化链路）
 */
class KbAclMetadataTest {

    private KnowledgeBase kb(String owner, boolean isPublic, String sensitivity, String groupsJson) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUuid("kb-1");
        kb.setOwnerUuid(owner);
        kb.setIsPublic(isPublic);
        kb.setDefaultSensitivity(sensitivity);
        kb.setDefaultVisibilityGroups(groupsJson == null ? null : com.moyz.adi.common.util.JsonUtil.toJsonNode(groupsJson));
        return kb;
    }

    @Test
    void injectWritesAllThreeAclKeys() {
        Metadata metadata = new Metadata();
        metadata.put(AdiConstant.MetadataKey.KB_UUID, "kb-1");

        KbAclMetadata.inject(metadata, kb("user-a", false, "PRIVATE", "[\"team-x\"]"));

        // 卡 3 验收：metadata 必须含 ACL 两个键（+ owner 键）
        assertEquals("PRIVATE", metadata.getString(AdiConstant.MetadataKey.ACL_SENSITIVITY));
        // 可见组 CSV 恒含合成 owner 组
        assertEquals("team-x,owner:user-a", metadata.getString(AdiConstant.MetadataKey.ACL_VISIBILITY_GROUPS));
        assertEquals("user-a", metadata.getString(AdiConstant.MetadataKey.KB_OWNER_UUID));
    }

    @Test
    void injectSkipsWhenKbOrMetadataMissing() {
        Metadata metadata = new Metadata();
        KbAclMetadata.inject(metadata, null);
        KbAclMetadata.inject(null, kb("user-a", false, "PRIVATE", null));
        assertTrue(metadata.toMap().isEmpty());
    }

    @Test
    void effectiveSensitivityKeepsPublicKbVisible() {
        // 公开库推导为 PUBLIC：保证回填前后存量公开库检索行为零变化
        assertEquals(AdiConstant.Sensitivity.PUBLIC,
                KbAclMetadata.effectiveSensitivity(kb("user-a", true, "PRIVATE", null)));
        // 显式 PUBLIC/ORG 优先
        assertEquals(AdiConstant.Sensitivity.ORG,
                KbAclMetadata.effectiveSensitivity(kb("user-a", true, "ORG", null)));
        // 非公开且未显式配置 → PRIVATE
        assertEquals(AdiConstant.Sensitivity.PRIVATE,
                KbAclMetadata.effectiveSensitivity(kb("user-a", false, null, null)));
    }

    @Test
    void ownerGroupSynthesized() {
        Metadata metadata = new Metadata();
        KbAclMetadata.inject(metadata, kb("user-b", false, "ORG", "[]"));
        assertEquals("owner:user-b", metadata.getString(AdiConstant.MetadataKey.ACL_VISIBILITY_GROUPS));
        // 空 owner：合成组留空但不 NPE
        Metadata empty = new Metadata();
        KbAclMetadata.inject(empty, kb("", false, "PRIVATE", null));
        assertEquals("PRIVATE", empty.getString(AdiConstant.MetadataKey.ACL_SENSITIVITY));
    }
}
