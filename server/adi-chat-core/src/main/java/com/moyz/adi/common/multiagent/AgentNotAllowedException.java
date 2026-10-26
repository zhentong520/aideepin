package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;

/**
 * T7-02：目标 Agent 无权访问（ACL 校验失败）或委派功能被开关关闭。
 * <p>
 * 错误信息只说明「无权访问/不可用」，不泄露目标 Agent 的归属等额外信息。
 */
public class AgentNotAllowedException extends BaseException {

    public AgentNotAllowedException(String targetAgent) {
        super(ErrorEnum.A_AGENT_DELEGATION_NOT_ALLOWED, targetAgent, "not accessible for current user");
    }

    public AgentNotAllowedException(String targetAgent, String reason) {
        super(ErrorEnum.A_AGENT_DELEGATION_NOT_ALLOWED, targetAgent, reason);
    }
}
