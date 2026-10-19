package com.moyz.adi.common.rag;

import com.moyz.adi.common.vo.*;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;

public interface GraphStore {
    boolean addVertexes(List<GraphVertex> vertexes);

    boolean addVertex(GraphVertex vertex);

    GraphVertex updateVertex(GraphVertexUpdateInfo updateInfo);

    GraphVertex getVertex(GraphVertexSearch search);

    List<GraphVertex> getVertices(List<String> ids);

    List<GraphVertex> searchVertices(GraphVertexSearch search);

    List<Triple<GraphVertex, GraphEdge, GraphVertex>> getEdges(List<String> ids);

    List<Triple<GraphVertex, GraphEdge, GraphVertex>> searchEdges(GraphEdgeSearch search);

    Triple<GraphVertex, GraphEdge, GraphVertex> getEdge(GraphEdgeSearch search);

    Triple<GraphVertex, GraphEdge, GraphVertex> addEdge(GraphEdgeAddInfo addInfo);

    Triple<GraphVertex, GraphEdge, GraphVertex> updateEdge(GraphEdgeEditInfo edgeEditInfo);

    void deleteVertices(GraphSearchCondition filter, boolean includeEdges);

    void deleteEdges(GraphSearchCondition filter);

    /**
     * 定点删除源/目标实体名与 kb 匹配的边（段停用时按账本清理独占边）。
     * <p>
     * 无向匹配：与 searchEdges/getEdge 的查找语义保持一致——方向不参与边的身份，
     * 账本中按字典序规范化记账的边与其在图库中的建边方向可能相反。
     */
    void deleteEdge(String kbUuid, String sourceName, String targetName);

    /**
     * 该图谱在当前过滤条件下是否没有任何顶点。
     * <p>
     * 用途：绝大多数知识库从不建图谱索引，但图谱检索器每次提问都会先调一次 LLM 从问题里
     * 抽实体（约 6 秒 + 一次计费），抽完再去空图里查，必然零命中。有了这个探针，
     * 建检索器时就能直接跳过图谱路。
     * <p>
     * 默认返回 false（= 不跳过），保证任何未实现该方法的 GraphStore 行为与改造前一致。
     *
     * @param metadataFilter 元数据过滤条件（知识库场景为 kb_uuid 等值过滤）
     * @return true 表示该条件下图谱为空
     */
    default boolean isEmpty(dev.langchain4j.store.embedding.filter.Filter metadataFilter) {
        return false;
    }
}
