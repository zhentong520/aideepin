package com.moyz.adi.common.multiagent;

import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;

import java.util.List;

/**
 * T7-04：A2A 双向互调防环——调用链上出现重复 Agent（A→B→A 或自递归 A→A）即拒绝。
 * <p>
 * 错误信息带上重复的 Agent 与完整调用链，保证「为什么被拒」一目了然。
 */
public class AgentCallCycleException extends BaseException {

    public AgentCallCycleException(String repeatedAgentId, List<String> chain) {
        super(ErrorEnum.A_AGENT_DELEGATION_CYCLE, repeatedAgentId, String.join(" -> ", chain));
    }
}
