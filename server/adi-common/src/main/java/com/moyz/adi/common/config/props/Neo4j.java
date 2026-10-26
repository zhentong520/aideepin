package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * Neo4j 图数据库连接配置（adi.datasource.neo4j.*）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Neo4j} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Neo4j {
    private String host;
    private int port;
    private String username;
    private String password;
    private String database;
}
