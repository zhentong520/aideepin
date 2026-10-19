package com.moyz.adi.common.config.embeddingstore;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.util.AdiPropertiesUtil;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * pgvector的相关配置
 */
@Slf4j
@Configuration
@ConditionalOnProperty(value = "adi.vector-database", havingValue = "pgvector")
public class PgVectorEmbeddingStoreConfig {

    @Value("${spring.datasource.url}")
    private String dataBaseUrl;

    @Value("${spring.datasource.username}")
    private String dataBaseUserName;

    @Value("${spring.datasource.password}")
    private String dataBasePassword;

    @Resource
    private AdiProperties adiProperties;

    @Resource
    private DataSource dataSource;

    /**
     * 根据选定的嵌入模型及其生成向量的维度不同来定义不同的表名，项目启动时只使用其中一种<br/>
     * 例如：<br/>
     * adi_knowledge_base_embedding_bge_384（默认）: 本地嵌入模型bge-small-zh-v1.5，维度384<br/>
     * adi_knowledge_base_embedding: 本地嵌入模型all-minilm-l6-v2，维度384<br/>
     * adi_knowledge_base_embedding_qwen_1024: 通义千问嵌入模型，维度1024<br/>
     * adi_knowledge_base_embedding_openai_1536: OpenAI嵌入模型，维度1536<br/>
     * <p>
     * Define different table names based on the selected embedding model and its vector dimension.
     * Only one is used at runtime.<br/>
     * Examples:<br/>
     * adi_knowledge_base_embedding_bge_384: bge-small-zh-v1.5, dimension 384 (default)<br/>
     * adi_knowledge_base_embedding: all-minilm-l6-v2, dimension 384<br/>
     * adi_knowledge_base_embedding_qwen_1024: Qwen embedding model, dimension 1024<br/>
     * adi_knowledge_base_embedding_openai_1536: OpenAI embedding model, dimension 1536<br/>
     *
     * @return embeddingStore
     */
    @Bean(name = "kbEmbeddingStore")
    @Primary
    public EmbeddingStore<TextSegment> initKbEmbeddingStore() {
        log.info("Initializing kbEmbeddingStore...");
        String tableName = "adi_knowledge_base_embedding";
        Pair<String, Integer> pair = AdiPropertiesUtil.getSuffixAndDimension(adiProperties);
        if (StringUtils.isNotBlank(pair.getLeft())) {
            tableName = tableName + "_" + pair.getLeft();
        }
        EmbeddingStore<TextSegment> store = createEmbeddingStore(tableName, pair.getRight());
        // 知识库向量表不再维护段级 hit_count/word_count（职责已迁到 adi_document_segment 关系表，
        // 且 text 列置空后 word_count 生成列失去意义），因此不再对 KB 表调用 ensureColumns。
        return store;
    }

    /**
     * 角色语义记忆使用的向量库
     *
     * @return EmbeddingStore实例
     */
    @Bean(name = "semanticEmbeddingStore")
    public EmbeddingStore<TextSegment> initSemanticEmbeddingStore() {
        log.info("Initializing semanticEmbeddingStore...");
        String tableName = "adi_character_memory_embedding";
        Pair<String, Integer> pair = AdiPropertiesUtil.getSuffixAndDimension(adiProperties);
        if (StringUtils.isNotBlank(pair.getLeft())) {
            tableName = tableName + "_" + pair.getLeft();
        }
        EmbeddingStore<TextSegment> store = createEmbeddingStore(tableName, pair.getRight());
        ensureColumns(tableName);
        return store;
    }

    /**
     * 角色情景记忆使用的独立向量库。
     * <p>
     * Dedicated vector store for episodic memory, physically isolated from semantic
     * memory so retrieval on either store never competes for top-K with the other.
     *
     * @return EmbeddingStore实例
     */
    @Bean(name = "episodicEmbeddingStore")
    public EmbeddingStore<TextSegment> initEpisodicEmbeddingStore() {
        log.info("Initializing episodicEmbeddingStore...");
        String tableName = "adi_character_episodic_memory_embedding";
        Pair<String, Integer> pair = AdiPropertiesUtil.getSuffixAndDimension(adiProperties);
        if (StringUtils.isNotBlank(pair.getLeft())) {
            tableName = tableName + "_" + pair.getLeft();
        }
        EmbeddingStore<TextSegment> store = createEmbeddingStore(tableName, pair.getRight());
        ensureColumns(tableName);
        return store;
    }

    private EmbeddingStore<TextSegment> createEmbeddingStore(String tableName, int dimension) {
        // 正则表达式匹配
        String regex = "jdbc:postgresql://([^:/]+):(\\d+)/(\\w+).*";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(dataBaseUrl);

        String host = "";
        String port = "";
        String databaseName = "";
        if (matcher.matches()) {
            host = matcher.group(1);
            port = matcher.group(2);
            databaseName = matcher.group(3);

            log.info("Host: " + host);
            log.info("Port: " + port);
            log.info("Database: " + databaseName);
        } else {
            throw new RuntimeException("parse url error");
        }
        log.info("Creating PgVectorEmbeddingStore with table name:{},dimension:{}", tableName, dimension);
        EmbeddingStore<TextSegment> store = PgVectorEmbeddingStore.builder()
                .host(host)
                .port(Integer.parseInt(port))
                .database(databaseName)
                .user(dataBaseUserName)
                .password(dataBasePassword)
                .dimension(dimension)
                .createTable(true)
                .dropTableFirst(false)
                .table(tableName)
                .build();
        ensureVectorIndex(tableName, dimension);
        return store;
    }

    /**
     * pgvector ANN 索引（HNSW / IVFFlat）的维度上限。超过该值 pgvector 无法建立任何近似索引，
     * 检索只能退化为全表精确扫描（行数上千后延迟线性上升）。
     */
    private static final int MAX_ANN_DIMENSION = 2000;

    /**
     * Ensure an approximate nearest neighbour index exists on the embedding column.
     * <p>
     * langchain4j's {@code useIndex} flag only builds an IVFFlat index, which needs a representative
     * data sample to cluster well (an empty or tiny table yields terrible recall) and is never rebuilt
     * afterwards. HNSW is the better fit for a read-mostly knowledge base: it can be created on an
     * empty table, degrades gracefully as rows are added, and does not need re-clustering.
     * <p>
     * Two guards matter in production:
     * <ul>
     *   <li><b>Dimension ceiling</b> — pgvector cannot index vectors above 2000 dimensions. When the
     *       configured embedding model exceeds it (e.g. a 2048-dim model) we log an actionable warning
     *       instead of silently paying for a sequential scan on every question.</li>
     *   <li><b>Metadata filtering</b> — knowledge-base queries always carry a {@code kb_uuid} filter.
     *       A plain HNSW scan returns the global top-K and filters afterwards, so a small KB can be
     *       crowded out by a bigger one and end up with zero hits. pgvector 0.8's iterative scan fixes
     *       this, so we raise {@code hnsw.ef_search} and enable {@code relaxed_order} at database level
     *       (the store keeps its own connection pool, so a per-session SET would never reach it).</li>
     * </ul>
     * Every step is best effort: a missing extension, an old pgvector or a non-superuser role only
     * downgrades to exact search, it never fails startup.
     */
    private void ensureVectorIndex(String tableName, int dimension) {
        if (dimension > MAX_ANN_DIMENSION) {
            log.warn("Vector table {} uses {}-dim embeddings, above the {} dimension ceiling of pgvector ANN "
                            + "indexes (HNSW/IVFFlat). Retrieval will fall back to an exact sequential scan. "
                            + "Switch to an embedding model with <= {} dimensions (or a Matryoshka-truncated one) "
                            + "if recall latency matters.",
                    tableName, dimension, MAX_ANN_DIMENSION, MAX_ANN_DIMENSION);
            return;
        }
        String indexName = tableName + "_hnsw_idx";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + tableName
                    + " USING hnsw (embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64)");
            log.info("Ensured HNSW index {} on table {}", indexName, tableName);
            tuneAnnSearchKnobs(stmt);
        } catch (Exception e) {
            // pgvector < 0.5 has no HNSW, and an index build can also hit the 63-char identifier limit.
            // Both are non fatal: the store still answers with an exact scan.
            log.warn("HNSW index not created on table {} (falling back to exact search): {}", tableName, e.getMessage());
        }
    }

    /**
     * Raise the HNSW search effort and enable iterative scanning so that queries carrying a metadata
     * filter (every knowledge-base query filters on kb_uuid) still come back with a full result set
     * instead of being starved by the post-filter. Database level, because the embedding store keeps
     * its own connection pool that we cannot reach with a per-session SET.
     * <p>
     * 每个参数**单独一条语句**：{@code ALTER DATABASE ... SET} 一次只接受一个赋值，
     * 写成 {@code SET a = 1, b = 2} 会直接语法错误（"syntax error at or near \".\""），
     * 而且整体被 catch 掉，结果是两个参数一个都没设上却只留一行 warn——调优静默失效。
     * 因此这里逐条执行、逐条容错：老版本 pgvector 缺 {@code iterative_scan} 也不影响 {@code ef_search}。
     */
    private void tuneAnnSearchKnobs(Statement stmt) {
        String databaseName;
        try {
            databaseName = currentDatabaseName(stmt);
        } catch (Exception e) {
            log.warn("Could not resolve current database name, skip pgvector knob tuning: {}", e.getMessage());
            return;
        }
        try {
            stmt.execute("ALTER DATABASE " + databaseName + " SET hnsw.ef_search = 100");
            log.info("Tuned pgvector hnsw.ef_search=100 on database {}", databaseName);
        } catch (Exception e) {
            // e.g. not the database owner. The index still works, just with the default ef_search.
            log.warn("Could not set hnsw.ef_search on {} (defaults kept): {}", databaseName, e.getMessage());
        }
        try {
            stmt.execute("ALTER DATABASE " + databaseName + " SET hnsw.iterative_scan = relaxed_order");
            log.info("Tuned pgvector hnsw.iterative_scan=relaxed_order on database {}", databaseName);
        } catch (Exception e) {
            // pgvector < 0.8 has no hnsw.iterative_scan; only the filtered-recall behaviour degrades.
            log.warn("Could not set hnsw.iterative_scan on {} (older pgvector? defaults kept): {}",
                    databaseName, e.getMessage());
        }
    }

    private String currentDatabaseName(Statement stmt) throws Exception {
        try (java.sql.ResultSet rs = stmt.executeQuery("select current_database()")) {
            return rs.next() ? rs.getString(1) : "aideepin";
        }
    }

    /**
     * Ensure custom columns exist on the embedding table. Only the character memory tables
     * (semantic / episodic) still need this: their {@code hit_count} is incremented on retrieval
     * and their {@code text} column remains the source of truth for memory content.
     * The KB embedding table no longer calls this - segment-level hit_count/word_count moved to
     * the relational {@code adi_document_segment*} tables and the vector-side {@code text} column
     * is kept empty (see migration 013).
     * <ul>
     *   <li>{@code hit_count} — plain int, DEFAULT 0, incremented on retrieval.</li>
     *   <li>{@code word_count} — STORED generated column, auto-computed from
     *       {@code char_length(text)} on every insert/update.</li>
     * </ul>
     * Covers both fresh installs and model switches that create a new suffixed table.
     */
    private void ensureColumns(String tableName) {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + tableName
                    + " ADD COLUMN IF NOT EXISTS hit_count int DEFAULT 0,"
                    + " ADD COLUMN IF NOT EXISTS word_count int GENERATED ALWAYS AS (char_length(\"text\")) STORED");
            log.info("Ensured custom columns (hit_count, word_count) on table {}", tableName);
        } catch (Exception e) {
            log.warn("Failed to add custom columns to table {}", tableName, e);
        }
    }

}
