package com.moyz.adi.common.config.props;

import lombok.Data;

/**
 * 知识库检索侧 ACL 配置（adi.kb.acl.*）。
 * <p>
 * OPT-17 拆分：原 {@code AdiProperties.Acl} 嵌套类提为顶层类，Spring 绑定键不变。
 */
@Data
public class Acl {

    /**
     * 是否启用检索侧 ACL 过滤 + 应用层双保险 | Whether retrieval-side ACL filtering is enabled.
     */
    private boolean enabled = false;
}
