package com.moyz.adi.common.config.graphstore;

import com.moyz.adi.common.rag.GraphStore;
import com.moyz.adi.common.rag.NoopGraphStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 图库兜底配置（2026-10-16）。
 *
 * <p>当 {@code adi.graph-database} 未指向任何可用实现（未配置 / 值不是 apache-age）
 * 时，提供 {@link NoopGraphStore} 兜底，保证应用在无图数据库的环境（本地开发、精简部署）
 * 能正常启动，图检索自动降级（见 NoopGraphStore 的 isEmpty 语义）。
 *
 * <p>注意：此配置类的 bean 方法带 {@code name = "kbGraphStore"}，与 ApacheAge
 * 配置类互斥（各自 ConditionalOnProperty 匹配不同值）；匹配不到任何图库实现时由本类兜底。
 */
@Configuration
@ConditionalOnProperty(value = "adi.graph-database", havingValue = "disabled", matchIfMissing = true)
public class NoopGraphStoreConfig {

    @Bean(name = "kbGraphStore")
    public GraphStore initGraphStore() {
        return new NoopGraphStore();
    }
}
