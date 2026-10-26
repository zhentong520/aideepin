package com.moyz.adi.common.interfaces;

import com.moyz.adi.common.multiagent.AgentCallChain;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * Delegate agent tool SPI (OPT-15 batch 3 dependency inversion).
 * <p>
 * Implemented by the business layer DelegateAgentTool in adi-common-biz,
 * which depends on CharacterService and UserService. The model integration
 * module (adi-model AbstractLLMService) only assembles tools against this
 * interface, avoiding a model-to-biz dependency. Resolved at runtime via
 * Spring by type (single implementation).
 */
public interface DelegateAgentSpi {

    /**
     * Feature switch. When off, the tool is not registered (invisible to the model).
     */
    boolean enabled();

    /**
     * Tool specification for the delegate_agent tool.
     */
    ToolSpecification buildSpecification();

    /**
     * Invoke delegation: hand the tool call to the target character running in
     * agent mode. Arguments are passed explicitly by the caller because
     * ThreadContext is not reachable on the tool callback thread.
     */
    String invoke(ToolExecutionRequest request, Long userId, String sourceCharacterUuid, AgentCallChain chain);
}
