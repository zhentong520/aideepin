package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * 附加数据源配置 | Extra datasource settings.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Datasource} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Datasource {
    private Neo4j neo4j;
}
