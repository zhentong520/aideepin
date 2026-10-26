package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * 加密配置（adi.encrypt.*）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Encrypt} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Encrypt {
    private String aesKey;
}
