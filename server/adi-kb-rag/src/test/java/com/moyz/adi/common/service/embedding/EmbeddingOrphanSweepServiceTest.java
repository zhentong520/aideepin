package com.moyz.adi.common.service.embedding;

import com.moyz.adi.common.rag.QueryEmbeddingCache;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mockStatic;

/**
 * S1-15：孤儿向量对账清理的循环 / 中止 / 缓存失效行为。
 */
class EmbeddingOrphanSweepServiceTest {

    @Test
    void sweepLoopsBatchesUntilZeroAndInvalidatesCache() {
        IKnowledgeEmbeddingService embeddingService = mock(IKnowledgeEmbeddingService.class);
        when(embeddingService.deleteOrphanBatch(EmbeddingOrphanSweepService.BATCH_SIZE))
                .thenReturn(5000, 2000, 0);
        EmbeddingOrphanSweepService sweepService = new EmbeddingOrphanSweepService(embeddingService);

        try (MockedStatic<QueryEmbeddingCache> cacheMock = mockStatic(QueryEmbeddingCache.class)) {
            int total = sweepService.sweep();

            assertEquals(7000, total);
            // 三批（5000 + 2000 + 空批 0），每批都用同一 BATCH_SIZE
            verify(embeddingService, times(3)).deleteOrphanBatch(EmbeddingOrphanSweepService.BATCH_SIZE);
            // 有清理动作才失效查询向量缓存
            cacheMock.verify(QueryEmbeddingCache::invalidateAll);
        }
    }

    @Test
    void sweepWithNoOrphansReturnsZeroAndSkipsCacheInvalidation() {
        IKnowledgeEmbeddingService embeddingService = mock(IKnowledgeEmbeddingService.class);
        when(embeddingService.deleteOrphanBatch(anyInt())).thenReturn(0);
        EmbeddingOrphanSweepService sweepService = new EmbeddingOrphanSweepService(embeddingService);

        try (MockedStatic<QueryEmbeddingCache> cacheMock = mockStatic(QueryEmbeddingCache.class)) {
            assertEquals(0, sweepService.sweep());
            verify(embeddingService, times(1)).deleteOrphanBatch(anyInt());
            cacheMock.verify(() -> QueryEmbeddingCache.invalidateAll(), never());
        }
    }

    @Test
    void sweepAbortsAndRethrowsWhenBatchFails() {
        IKnowledgeEmbeddingService embeddingService = mock(IKnowledgeEmbeddingService.class);
        when(embeddingService.deleteOrphanBatch(anyInt()))
                .thenReturn(5000)
                .thenThrow(new IllegalStateException("vector store down"));
        EmbeddingOrphanSweepService sweepService = new EmbeddingOrphanSweepService(embeddingService);

        try (MockedStatic<QueryEmbeddingCache> cacheMock = mockStatic(QueryEmbeddingCache.class)) {
            assertThrows(IllegalStateException.class, sweepService::sweep);

            // 第一批成功、第二批失败即中止，不继续打第三次
            verify(embeddingService, times(2)).deleteOrphanBatch(anyInt());
            // 本轮异常中止，不触发缓存失效
            cacheMock.verify(() -> QueryEmbeddingCache.invalidateAll(), never());
        }
    }
}
