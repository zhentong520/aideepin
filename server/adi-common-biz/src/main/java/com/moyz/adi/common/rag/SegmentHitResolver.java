package com.moyz.adi.common.rag;

import com.moyz.adi.common.entity.DocumentSegment;
import com.moyz.adi.common.entity.DocumentSegmentChildChunk;
import com.moyz.adi.common.entity.DocumentSegmentQuestion;
import com.moyz.adi.common.service.DocumentSegmentChildChunkService;
import com.moyz.adi.common.service.DocumentSegmentQuestionService;
import com.moyz.adi.common.service.DocumentSegmentService;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 向量命中 → 关系层片段 的映射（二开 2026-09-30 抽出）。
 * <p>
 * 向量库里一次 ANN 检索会混着三种形态的命中，它们要各自映射回关系表才是"真正的内容"：
 * <pre>
 *   text          → 直接用主表行（adi_document_segment）
 *   qa            → 命中问题，取它指向的答案行（answer_segment_id 二跳）
 *   parent_child  → 命中子块，上卷到父段（parent_segment_id 二跳）
 * </pre>
 * 没有关系行的命中一律丢弃——向量库里的文本只是占位，直接返回会给出与知识库不一致的内容。
 * <p>
 * <b>为什么抽成工具类而不是新建 Bean</b>：调用方（{@link SegmentExpandProcessor} 与召回测试服务）
 * 各自持有自己注入的那三个 Service，把 Service 当参数传进来即可——既不引入新的 Spring 依赖，
 * 又只有一份映射口径。三处服务调用（两次按 embeddingId 查、一次按段 id 批量回表）合起来是
 * 检索热路径上的固定开销，抽出来后调用次数与拆分前一致，没有额外查询。
 * <p>
 * Mapping of vector hits back to relational segments, shared by the retrieval pipeline and the
 * admin recall-test page so both resolve hits the exact same way.
 */
public final class SegmentHitResolver {

    private SegmentHitResolver() {
    }

    /**
     * 预取命中涉及的全部关系行（三次批量查询：主表 / 问题表 / 子块表 + 一次二跳回表）
     */
    public static Resolution resolve(DocumentSegmentService segmentService,
                                     DocumentSegmentQuestionService questionService,
                                     DocumentSegmentChildChunkService childChunkService,
                                     List<String> embeddingIds) {
        List<String> ids = null == embeddingIds ? List.of() : embeddingIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return new Resolution(Map.of(), Map.of(), Map.of(), Map.of());
        }

        Map<String, DocumentSegment> mainByEmbeddingId = segmentService.mapByEmbeddingIds(ids);

        List<DocumentSegmentQuestion> questionHits = questionService.listByEmbeddingIds(ids);
        Map<String, DocumentSegmentQuestion> questionByEmbeddingId = questionHits.stream()
                .filter(q -> null != q.getEmbeddingId())
                .collect(Collectors.toMap(DocumentSegmentQuestion::getEmbeddingId, Function.identity(), (a, b) -> a));
        List<DocumentSegmentChildChunk> childHits = childChunkService.listByEmbeddingIds(ids);
        Map<String, DocumentSegmentChildChunk> childByEmbeddingId = childHits.stream()
                .filter(c -> null != c.getEmbeddingId())
                .collect(Collectors.toMap(DocumentSegmentChildChunk::getEmbeddingId, Function.identity(), (a, b) -> a));

        // 二跳：命中问题 → 答案行；命中子块 → 父段行。两跳的 id 一起回表，省一轮查询
        Set<Long> secondHopIds = new HashSet<>();
        questionHits.forEach(q -> {
            if (null != q.getAnswerSegmentId()) {
                secondHopIds.add(q.getAnswerSegmentId());
            }
        });
        childHits.forEach(c -> {
            if (null != c.getParentSegmentId()) {
                secondHopIds.add(c.getParentSegmentId());
            }
        });
        Map<Long, DocumentSegment> segmentById = secondHopIds.isEmpty()
                ? Map.of() : segmentService.mapByIds(secondHopIds);

        return new Resolution(mainByEmbeddingId, questionByEmbeddingId, childByEmbeddingId, segmentById);
    }

    /**
     * 命中形态
     */
    public enum Kind {
        /** 命中正文分段 */
        TEXT,
        /** 命中问答的问题，内容取答案行 */
        QA,
        /** 命中父块下的子块，内容上卷为父段 */
        CHILD
    }

    /**
     * 一个命中解析出来的内容与归属
     */
    @Getter
    public static final class Hit {
        /**
         * 内容对应的关系行主键。问答命中且答案行缺失时，退化为问题行主键（与拆分前的行为一致）
         */
        private final Long segmentId;
        /**
         * 真正要用的文本（父段内容 / 答案内容 / 正文内容）
         */
        private final String text;
        /**
         * 关系行；问答命中且答案行缺失时为 null。用于取文档归属与段序
         */
        private final DocumentSegment segment;
        private final Kind kind;
        /**
         * 仅子块命中：父段主键，供调用方对"多个子块命中同一父段"去重
         */
        private final Long parentSegmentId;

        private Hit(Long segmentId, String text, DocumentSegment segment, Kind kind, Long parentSegmentId) {
            this.segmentId = segmentId;
            this.text = text;
            this.segment = segment;
            this.kind = kind;
            this.parentSegmentId = parentSegmentId;
        }
    }

    /**
     * 预取结果 + 单条解析
     */
    public static final class Resolution {

        private final Map<String, DocumentSegment> mainByEmbeddingId;
        private final Map<String, DocumentSegmentQuestion> questionByEmbeddingId;
        private final Map<String, DocumentSegmentChildChunk> childByEmbeddingId;
        private final Map<Long, DocumentSegment> segmentById;

        private Resolution(Map<String, DocumentSegment> mainByEmbeddingId,
                           Map<String, DocumentSegmentQuestion> questionByEmbeddingId,
                           Map<String, DocumentSegmentChildChunk> childByEmbeddingId,
                           Map<Long, DocumentSegment> segmentById) {
            this.mainByEmbeddingId = mainByEmbeddingId;
            this.questionByEmbeddingId = questionByEmbeddingId;
            this.childByEmbeddingId = childByEmbeddingId;
            this.segmentById = segmentById;
        }

        /**
         * 解析单个命中
         *
         * @return null 表示向量库里这条命中在关系层没有对应行（脏数据），调用方应丢弃并告警
         */
        public Hit resolveOne(String embeddingId) {
            if (null == embeddingId) {
                return null;
            }
            DocumentSegment main = mainByEmbeddingId.get(embeddingId);
            if (null != main) {
                return new Hit(main.getId(), main.getContent(), main, Kind.TEXT, null);
            }
            DocumentSegmentQuestion question = questionByEmbeddingId.get(embeddingId);
            if (null != question) {
                DocumentSegment answer = null == question.getAnswerSegmentId()
                        ? null : segmentById.get(question.getAnswerSegmentId());
                // 答案行缺失（脏数据）时退回问题文本，保证不返回空内容
                return answer != null
                        ? new Hit(answer.getId(), answer.getContent(), answer, Kind.QA, null)
                        : new Hit(question.getId(), question.getContent(), null, Kind.QA, null);
            }
            DocumentSegmentChildChunk child = childByEmbeddingId.get(embeddingId);
            if (null != child) {
                DocumentSegment parent = null == child.getParentSegmentId()
                        ? null : segmentById.get(child.getParentSegmentId());
                if (null == parent) {
                    return null;
                }
                return new Hit(parent.getId(), parent.getContent(), parent, Kind.CHILD, parent.getId());
            }
            return null;
        }
    }
}
