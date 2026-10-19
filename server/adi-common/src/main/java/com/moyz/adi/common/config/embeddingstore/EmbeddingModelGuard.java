package com.moyz.adi.common.config.embeddingstore;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.util.AdiPropertiesUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 向量模型一致性自检（启动后跑一次）。
 * <p>
 * 商业背景：换 embedding 模型是知识库最典型的"静默事故"。运维在后台把
 * {@code adi.embedding-model} 从 bge 改成 volcengine，重启之后——
 * <ul>
 *   <li>向量表名是按「模型 + 维度」拼的，新建的表是空的，历史向量全留在旧表里成了孤儿；</li>
 *   <li>知识库问答不会报错，只是永远召不回任何内容（严格模式下变成一律拒答，普通模式下变成纯幻觉）；</li>
 *   <li>更隐蔽的一种：表名没变但维度变了（比如同名表被旧版本建过），
 *       {@code CREATE TABLE IF NOT EXISTS} 不会改列类型，于是所有写入都会因维度不符而失败。</li>
 * </ul>
 * 这类问题靠看接口返回是看不出来的，只能翻日志和数表。这里在启动后直接把它打出来：
 * 维度不符按 ERROR 报（写入必然失败），孤儿向量按 WARN 报（附处理指引）。
 * <p>
 * 只读、无副作用、失败只记日志，绝不影响启动。
 */
@Slf4j
@Component
@ConditionalOnProperty(value = "adi.vector-database", havingValue = "pgvector")
public class EmbeddingModelGuard {

    /** 需要体检的向量表前缀（与 PgVectorEmbeddingStoreConfig 里创建的三个 store 一一对应） */
    private static final String[] TABLE_PREFIXES = {
            "adi_knowledge_base_embedding",
            "adi_character_memory_embedding",
            "adi_character_episodic_memory_embedding"
    };

    @Resource
    private DataSource dataSource;

    @Resource
    private AdiProperties adiProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        Pair<String, Integer> pair = AdiPropertiesUtil.getSuffixAndDimension(adiProperties);
        String suffix = pair.getLeft();
        int dimension = pair.getRight();
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String prefix : TABLE_PREFIXES) {
                String currentTable = StringUtils.isBlank(suffix) ? prefix : prefix + "_" + suffix;
                List<VectorTableInfo> siblings = listVectorTables(stmt, prefix);
                checkDimensionMismatch(currentTable, dimension, siblings);
                checkOrphanedVectors(stmt, currentTable, siblings);
            }
        } catch (Exception e) {
            // 自检失败不能拖住启动，也不能掩盖真正的问题：只记一行
            log.warn("Embedding model consistency check failed: {}", e.getMessage());
        }
    }

    /**
     * 当前表已存在但向量维度与模型不符：{@code CREATE TABLE IF NOT EXISTS} 不会修正列类型，
     * 之后每一次写入都会因维度不匹配而报错，属于必须马上处理的问题。
     */
    private void checkDimensionMismatch(String currentTable, int dimension, List<VectorTableInfo> siblings) {
        for (VectorTableInfo info : siblings) {
            if (!currentTable.equals(info.tableName)) {
                continue;
            }
            String expected = "vector(" + dimension + ")";
            if (!expected.equals(info.columnType)) {
                log.error("EMBEDDING MODEL MISMATCH: table {} holds {} but the configured model produces {}. "
                                + "Every vector write will fail until the column is rebuilt "
                                + "(drop the table, or migrate it, then re-index the knowledge base).",
                        info.tableName, info.columnType, expected);
            }
        }
    }

    /**
     * 旧模型的向量表还有数据、当前模型的表却是空的：历史向量成了孤儿，检索必然零命中。
     * <p>
     * 行数判断不用 {@code reltuples}（它是统计估算值，刚建表/没跑 ANALYZE 时是 -1/0，
     * 会产生"当前表明明有数据却报孤儿"的误报），改用 {@code select 1 ... limit 1} 精确判存。
     */
    private void checkOrphanedVectors(Statement stmt, String currentTable, List<VectorTableInfo> siblings) {
        boolean currentExists = siblings.stream().anyMatch(t -> currentTable.equals(t.tableName));
        if (!currentExists) {
            return;
        }
        Boolean currentHasRows = probeHasRows(stmt, currentTable);
        if (null == currentHasRows || currentHasRows) {
            // 探不明或确实有数据：保持沉默，不打扰
            return;
        }
        List<String> orphanTables = new ArrayList<>();
        for (VectorTableInfo info : siblings) {
            if (currentTable.equals(info.tableName)) {
                continue;
            }
            if (Boolean.TRUE.equals(probeHasRows(stmt, info.tableName))) {
                orphanTables.add(info.tableName);
            }
        }
        if (!orphanTables.isEmpty()) {
            log.warn("ORPHANED VECTORS: the configured embedding model now uses empty table {}, while rows "
                            + "still sit in tables of previous models {}. Those vectors are not searchable any more, "
                            + "so questions will silently recall nothing. Re-index the affected documents "
                            + "(Knowledge base -> rebuild index) to bring them back.",
                    currentTable, orphanTables);
        }
    }

    /**
     * @return true/false 表里是否有行；查询失败返回 null（无法判断，调用方应放弃检查）
     */
    private Boolean probeHasRows(Statement stmt, String table) {
        try (ResultSet rs = stmt.executeQuery("select 1 from " + table + " limit 1")) {
            return rs.next();
        } catch (Exception e) {
            log.warn("Could not probe rows of vector table {}: {}", table, e.getMessage());
            return null;
        }
    }

    private List<VectorTableInfo> listVectorTables(Statement stmt, String prefix) throws Exception {
        String sql = """
                select c.relname,
                       format_type(a.atttypid, a.atttypmod) as col_type,
                       greatest(c.reltuples, 0)::bigint as approx_rows
                from pg_class c
                join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
                join pg_attribute a on a.attrelid = c.oid and a.attname = 'embedding' and a.attnum > 0
                where c.relkind = 'r' and c.relname like '%s%%'
                order by c.relname
                """.formatted(prefix);
        List<VectorTableInfo> result = new ArrayList<>();
        try (ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new VectorTableInfo(rs.getString(1), rs.getString(2), rs.getLong(3)));
            }
        }
        return result;
    }

    private record VectorTableInfo(String tableName, String columnType, long approxRows) {
    }
}
