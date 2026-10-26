package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.dto.KbDocumentEmbeddingDto;
import com.moyz.adi.common.dto.RefEmbeddingDto;
import com.moyz.adi.common.entity.CharacterMessageRefEmbedding;
import com.moyz.adi.common.mapper.CharacterMessageRefEmbeddingMapper;
import com.moyz.adi.common.service.embedding.ICharacterMemoryEmbeddingService;
import com.moyz.adi.common.util.EmbeddingUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class CharacterMessageRefEmbeddingService extends ServiceImpl<CharacterMessageRefEmbeddingMapper, CharacterMessageRefEmbedding> {

    @Resource
    private ICharacterMemoryEmbeddingService characterMemoryEmbeddingService;

    public List<RefEmbeddingDto> listRefEmbeddings(String msgUuid) {
        List<CharacterMessageRefEmbedding> recordReferences = this.getBaseMapper().listByMsgUuid(msgUuid);
        if (CollectionUtils.isEmpty(recordReferences)) {
            return Collections.emptyList();
        }
        List<String> embeddingIds = recordReferences.stream().map(CharacterMessageRefEmbedding::getEmbeddingId).toList();
        if (CollectionUtils.isEmpty(embeddingIds)) {
            return Collections.emptyList();
        }
        List<KbDocumentEmbeddingDto> embeddings = characterMemoryEmbeddingService.listByEmbeddingIds(embeddingIds);
        List<RefEmbeddingDto> dtos = EmbeddingUtil.itemToRefEmbeddingDto(embeddings);
        // T7-01：回填本次检索的 query_id（embeddingId → query_id），页面刷新后仍可提交 👍👎 反馈
        Map<String, String> queryIdByEmbedding = recordReferences.stream()
                .filter(ref -> StringUtils.isNotBlank(ref.getRagQueryId()))
                .collect(Collectors.toMap(CharacterMessageRefEmbedding::getEmbeddingId,
                        CharacterMessageRefEmbedding::getRagQueryId, (a, b) -> a));
        if (!queryIdByEmbedding.isEmpty()) {
            for (RefEmbeddingDto dto : dtos) {
                dto.setRagQueryId(queryIdByEmbedding.get(dto.getEmbeddingId()));
            }
        }
        return dtos;
    }

}
