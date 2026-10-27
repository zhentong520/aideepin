package com.moyz.adi.common.rag;

import com.moyz.adi.common.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Collections;
import java.util.List;

/**
 * 图库降级实现：未配置图数据库（adi.graph-database 未指向任何可用实现）时兜底。
 *
 * <p>设计意图（2026-10-16）：图检索是可选能力（仅勾选"图谱化"的知识库用到），
 * 不应因为环境里没有 Apache AGE 就让整个应用起不来。此实现下：
 * <ul>
 *   <li>{@link #isEmpty(Filter)} 恒返回 true——检索器据此直接跳过图谱路，
 *       不再花一次 LLM 调用从问题抽实体（这正是接口 default 注释设计的用途）；</li>
 *   <li>写操作静默跳过并 warn 一次（首次触发时提醒配置图库）；</li>
 *   <li>读操作返回空，调用方拿到"图谱为空"的正常降级语义。</li>
 * </ul>
 */
@Slf4j
public class NoopGraphStore implements GraphStore {

    private volatile boolean warned = false;

    private void warnOnce() {
        if (!warned) {
            warned = true;
            log.warn("Graph store is disabled (adi.graph-database not configured), all graph operations are no-op");
        }
    }

    @Override
    public boolean addVertexes(List<GraphVertex> vertexes) {
        warnOnce();
        return false;
    }

    @Override
    public boolean addVertex(GraphVertex vertex) {
        warnOnce();
        return false;
    }

    @Override
    public GraphVertex updateVertex(GraphVertexUpdateInfo updateInfo) {
        warnOnce();
        return null;
    }

    @Override
    public GraphVertex getVertex(GraphVertexSearch search) {
        return null;
    }

    @Override
    public List<GraphVertex> getVertices(List<String> ids) {
        return Collections.emptyList();
    }

    @Override
    public List<GraphVertex> searchVertices(GraphVertexSearch search) {
        return Collections.emptyList();
    }

    @Override
    public List<Triple<GraphVertex, GraphEdge, GraphVertex>> getEdges(List<String> ids) {
        return Collections.emptyList();
    }

    @Override
    public List<Triple<GraphVertex, GraphEdge, GraphVertex>> searchEdges(GraphEdgeSearch search) {
        return Collections.emptyList();
    }

    @Override
    public Triple<GraphVertex, GraphEdge, GraphVertex> getEdge(GraphEdgeSearch search) {
        return null;
    }

    @Override
    public Triple<GraphVertex, GraphEdge, GraphVertex> addEdge(GraphEdgeAddInfo addInfo) {
        warnOnce();
        return null;
    }

    @Override
    public Triple<GraphVertex, GraphEdge, GraphVertex> updateEdge(GraphEdgeEditInfo edgeEditInfo) {
        warnOnce();
        return null;
    }

    @Override
    public void deleteVertices(GraphSearchCondition filter, boolean includeEdges) {
        warnOnce();
    }

    @Override
    public void deleteEdges(GraphSearchCondition filter) {
        warnOnce();
    }

    @Override
    public void deleteEdge(String kbUuid, String sourceName, String targetName) {
        warnOnce();
    }

    @Override
    public boolean isEmpty(dev.langchain4j.store.embedding.filter.Filter metadataFilter) {
        // 恒 true：让图谱检索器直接跳过图谱路（省一次 LLM 抽实体调用）
        return true;
    }
}
