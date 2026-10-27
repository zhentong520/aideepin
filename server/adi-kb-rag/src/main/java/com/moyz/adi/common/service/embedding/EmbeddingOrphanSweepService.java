package com.moyz.adi.common.service.embedding;

import com.moyz.adi.common.rag.QueryEmbeddingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * S1-15：孤儿向量对账清理。
 * <p>
 * 孤儿向量 = 向量表里有行、但三张段表（主段 / 问题 / 子块）中没有任何<b>活跃</b>行
 * （未软删）引用其 embedding_id 的向量。产生途径：删除/重索引路径的向量清理是尽力而为
 * （事务外、失败不阻断主流程），历史版本还有从未实现删除的路径——残留向量本身不可达
 * （段行已删，检索展开会排除），但白白占用存储与召回扫描面。
 * <p>
 * 清理动作分批执行（每批 LIMIT，循环到 0 行），避免长事务锁住向量表；
 * 完成后失效查询向量缓存（内容无关缓存本不脏读，这里只是满足「清理后统计立即收敛」
 * 的保守预期，成本为零）。
 */
@Slf4j
@Service
public class EmbeddingOrphanSweepService {

    /** 单批删除上限：一条 DELETE 的锁面与 WAL 量受控，循环到清完为止 */
    static final int BATCH_SIZE = 5000;

    private final IKnowledgeEmbeddingService iKnowledgeEmbeddingService;

    public EmbeddingOrphanSweepService(IKnowledgeEmbeddingService iKnowledgeEmbeddingService) {
        this.iKnowledgeEmbeddingService = iKnowledgeEmbeddingService;
    }

    /**
     * 对账一轮，返回清理的孤儿向量总数。任何一批失败即中止并抛出，
     * 已删的批次不回滚（删除幂等，下一轮对账继续收敛）。
     */
    public int sweep() {
        int total = 0;
        while (true) {
            int deleted;
            try {
                deleted = iKnowledgeEmbeddingService.deleteOrphanBatch(BATCH_SIZE);
            } catch (Exception e) {
                log.error("Orphan embedding sweep failed after {} deletions, aborting this round", total, e);
                throw e;
            }
            if (deleted <= 0) {
                break;
            }
            total += deleted;
            log.info("Orphan embedding sweep batch done, deleted:{}, cumulative:{}", deleted, total);
        }
        if (total > 0) {
            QueryEmbeddingCache.invalidateAll();
            log.info("Orphan embedding sweep finished, total deleted:{}, query embedding cache invalidated", total);
        }
        return total;
    }
}
