package com.moyz.adi.common.service;

import com.moyz.adi.common.entity.CharacterMessageRefEmbedding;
import com.moyz.adi.common.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T7-01：👍👎 反馈链路的 query_id 落库断言。
 *
 * <p>背景：feedback 接口以 query_id（adi_rag_query_log.query_id）为主键，
 * 前端需要从「回答 → 引用」拿到它。检索器在检索时把 query_id 绑到命中的 embeddingId，
 * 保存引用行时一并落库（页面刷新后 [META] 里的 refs 已丢失，靠引用行兜底）。
 */
@ExtendWith(MockitoExtension.class)
class CharacterMessageServiceEmbeddingRefQueryIdTest {

    @Mock
    private CharacterMessageRefEmbeddingService characterMessageRefEmbeddingService;

    @InjectMocks
    private CharacterMessageService characterMessageService;

    private static User user() {
        User user = new User();
        user.setId(7L);
        return user;
    }

    @Test
    @DisplayName("createEmbeddingRefs 落库时带上 rag_query_id")
    void createEmbeddingRefsCarriesQueryId() {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put("emb-1", 0.91);
        scoreMap.put("emb-2", 0.62);
        Map<String, String> queryIdMap = new LinkedHashMap<>();
        queryIdMap.put("emb-1", "q-1001");

        characterMessageService.createEmbeddingRefs(user(), 42L, scoreMap, queryIdMap);

        ArgumentCaptor<CharacterMessageRefEmbedding> captor = ArgumentCaptor.forClass(CharacterMessageRefEmbedding.class);
        verify(characterMessageRefEmbeddingService, times(2)).save(captor.capture());

        CharacterMessageRefEmbedding first = captor.getAllValues().get(0);
        assertEquals("emb-1", first.getEmbeddingId());
        assertEquals("q-1001", first.getRagQueryId());
        assertEquals(42L, first.getMessageId());

        // 没有 query_id 的命中（理论上不会发生，但字段必须为 null 而不是脏值）
        CharacterMessageRefEmbedding second = captor.getAllValues().get(1);
        assertEquals("emb-2", second.getEmbeddingId());
        assertNull(second.getRagQueryId());
    }

    @Test
    @DisplayName("旧链路（无 query_id 映射）不报错，rag_query_id 保持为空")
    void createEmbeddingRefsWithoutQueryIdMap() {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put("emb-9", 0.5);

        characterMessageService.createEmbeddingRefs(user(), 43L, scoreMap, null);

        ArgumentCaptor<CharacterMessageRefEmbedding> captor = ArgumentCaptor.forClass(CharacterMessageRefEmbedding.class);
        verify(characterMessageRefEmbeddingService, times(1)).save(captor.capture());
        CharacterMessageRefEmbedding ref = captor.getValue();
        assertNotNull(ref.getEmbeddingId());
        assertNull(ref.getRagQueryId());
    }

    @Test
    @DisplayName("两参重载向后兼容：仍只写 embeddingId/score/userId")
    void legacyOverloadStillWorks() {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put("emb-3", 0.33);

        characterMessageService.createEmbeddingRefs(user(), 44L, scoreMap);

        ArgumentCaptor<CharacterMessageRefEmbedding> captor = ArgumentCaptor.forClass(CharacterMessageRefEmbedding.class);
        verify(characterMessageRefEmbeddingService, times(1)).save(captor.capture());
        assertNull(captor.getValue().getRagQueryId());
    }

    @Test
    @DisplayName("保存动作对每条命中各触发一次（不吞不重）")
    void saveCalledPerEmbedding() {
        Map<String, Double> scoreMap = new LinkedHashMap<>();
        scoreMap.put("emb-a", 0.1);
        scoreMap.put("emb-b", 0.2);
        scoreMap.put("emb-c", 0.3);

        characterMessageService.createEmbeddingRefs(user(), 45L, scoreMap, Map.of());

        verify(characterMessageRefEmbeddingService, times(3)).save(any(CharacterMessageRefEmbedding.class));
    }
}
