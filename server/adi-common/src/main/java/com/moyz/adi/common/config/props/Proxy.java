package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * 代理配置 | Proxy settings.
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Proxy} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Proxy {
    private boolean enable;
    private String host;
    private int httpPort;
}
