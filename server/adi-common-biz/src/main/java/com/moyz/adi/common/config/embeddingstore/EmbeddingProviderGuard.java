package com.moyz.adi.common.config.embeddingstore;

import com.moyz.adi.common.entity.KnowledgeBase;
import com.moyz.adi.common.service.KnowledgeBaseService;
import com.moyz.adi.common.service.embedding.EmbeddingProviderInfo;
import com.moyz.adi.common.service.embedding.EmbeddingProviderService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Embedding provider 一致性自检（T7-05，启动后跑一次，只读无副作用）。
 * <p>
 * 回答一个问题：<b>「有没有知识库的向量是旧 provider 产出的？」</b>
 * <p>
 * 换 embedding 模型后向量表按「模型 + 维度」分表，新表为空、旧表成孤儿，问答不报错
 * 只是永远零召回（严格模式一律拒答，普通模式纯幻觉）。{@link EmbeddingModelGuard}
 * 从<b>表结构</b>侧发现这个问题（维度不符 / 孤儿表），本组件从<b>库记录</b>侧发现：
 * 每个知识库记录着产出其向量的 provider 版本（重建时写入），与当前生效 provider
 * 不一致即「向量建于旧模型之下」，必须按当前 provider 全量重建。
 * <p>
 * 约定：
 * <ul>
 *   <li>版本为空串 = 未知（存量库 / T7-05 之前创建）：跳过比对并只记 INFO——
 *      绝不能判为不一致，否则升级后第一启动把全部存量库刷成告警；表结构侧的
 *      EmbeddingModelGuard 对这类库依然有效；</li>
 *   <li>配置本身解析失败（如远端模型缺维度）：只记 WARN 并退场——启动期还有
 *      BeanConfig/EmbeddingModelGuard 会给出更贴近现场的错误，这里不重复刷屏；</li>
 *   <li>只读、失败只记日志，绝不影响启动（与 EmbeddingModelGuard 同一纪律）。</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "adi.vector-database", havingValue = "pgvector")
public class EmbeddingProviderGuard {

    /** 告警里最多列多少个库，防止库多时刷爆日志 */
    private static final int MAX_LISTED = 20;

    @Resource
    private EmbeddingProviderService embeddingProviderService;

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 比对结果（纯函数产出，便于单测：不一致检出 / 空值跳过）。
     *
     * @param mismatched 版本与当前 provider 不一致的库（含定位信息）
     * @param unknown    未记录 provider 版本的存量库数量（跳过比对，只记 INFO）
     */
    record ProviderCheckResult(List<String> mismatched, int unknown) {
    }

    /**
     * 纯比对逻辑：版本为空串 = 未知（跳过，绝不判为不一致）；其余与当前版本串精确比对。
     */
    static ProviderCheckResult compareVersions(List<KnowledgeBase> kbs, EmbeddingProviderInfo current) {
        List<String> mismatched = new ArrayList<>();
        int unknown = 0;
        for (KnowledgeBase kb : kbs) {
            String version = StringUtils.defaultString(kb.getEmbeddingProviderVersion());
            if (version.isEmpty()) {
                unknown++;
            } else if (!version.equals(current.version())) {
                mismatched.add(kb.getTitle() + "(" + kb.getUuid() + ", built by " + version + ")");
            }
        }
        return new ProviderCheckResult(mismatched, unknown);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        EmbeddingProviderInfo current;
        try {
            current = embeddingProviderService.currentProvider();
        } catch (Exception e) {
            log.warn("Embedding provider check skipped: cannot resolve the active provider ({})", e.getMessage());
            return;
        }
        try {
            List<KnowledgeBase> kbs = knowledgeBaseService.lambdaQuery()
                    .select(KnowledgeBase::getUuid, KnowledgeBase::getTitle, KnowledgeBase::getEmbeddingProviderVersion)
                    .eq(KnowledgeBase::getIsDeleted, false)
                    .list();
            ProviderCheckResult result = compareVersions(kbs, current);
            List<String> mismatched = result.mismatched();
            if (!mismatched.isEmpty()) {
                log.warn("EMBEDDING PROVIDER MISMATCH: {} knowledge base(s) still hold vectors from an older provider "
                                + "while the active one is {}: {}. Retrieval on those KBs will silently recall nothing "
                                + "(the new dimension table is empty) until they are rebuilt. Action: rebuild each KB "
                                + "under the active provider (POST /admin/kb/rebuildEmbedding/{{uuid}}), or roll back by "
                                + "switching adi.embedding-model to the recorded provider and rebuilding again.",
                        mismatched.size(), current.version(),
                        mismatched.subList(0, Math.min(mismatched.size(), MAX_LISTED)));
            }
            if (result.unknown() > 0) {
                log.info("Embedding provider check: {} KB(s) have no recorded provider version (legacy, created before "
                        + "the version column existed); table-level checks in EmbeddingModelGuard still cover them",
                        result.unknown());
            }
        } catch (Exception e) {
            // 自检失败不拖住启动，也不掩盖真正的问题：只记一行
            log.warn("Embedding provider consistency check failed: {}", e.getMessage());
        }
    }
}
