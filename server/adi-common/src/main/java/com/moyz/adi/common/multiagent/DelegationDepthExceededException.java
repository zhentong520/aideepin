package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;

import java.util.List;

/**
 * T7-02：委派嵌套深度超限（深度 ≥2 拒绝，硬限制不可配置放软）。
 * <p>
 * 错误信息必须可读：带上目标 Agent、当前调用链与最大深度，
 * 使调用方（与最终用户）能看懂「谁在什么链上被以什么理由拒绝」。
 */
public class DelegationDepthExceededException extends BaseException {

    public DelegationDepthExceededException(String targetAgentId, List<String> chain, int maxDepth) {
        super(ErrorEnum.A_AGENT_DELEGATION_DEPTH_EXCEEDED,
                targetAgentId, String.join(" -> ", chain), String.valueOf(maxDepth));
    }
}
