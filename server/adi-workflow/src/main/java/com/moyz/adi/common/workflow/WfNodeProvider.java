package com.moyz.adi.common.workflow;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.entity.WorkflowComponent;
import com.moyz.adi.common.entity.WorkflowNode;
import com.moyz.adi.common.helper.AdiMailSender;
import com.moyz.adi.common.service.AgentService;
import com.moyz.adi.common.service.CredentialVaultService;
import com.moyz.adi.common.service.DbDatasourceService;
import com.moyz.adi.common.service.FileService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.service.LLMCallRecordService;
import com.moyz.adi.common.service.UserMcpService;
import com.moyz.adi.common.service.SkillService;
import com.moyz.adi.common.service.skill.SkillToolService;
import com.moyz.adi.common.workflow.node.AbstractWfNode;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

/**
 * 节点创建入口（S1-02）。
 * <p>
 * 职责：先经 {@link WfNodeFactory} 纯实例化，再把容器依赖（{@link WfNodeDeps}）注入节点，
 * 使节点内部不再需要 SpringUtil.getBean 服务定位器。工作流引擎/触发器一律通过本类取节点，
 * 而非直接调用静态工厂（静态工厂保留给注册性单测）。
 */
@Component
public class WfNodeProvider {

    private final WfNodeDeps deps;

    /** 测试/手工组装用：直接给依赖集合 */
    public WfNodeProvider(WfNodeDeps deps) {
        this.deps = deps;
    }

    /** 生产用：容器逐个注入依赖，组装成只读快照 */
    public WfNodeProvider(Validator validator,
                          AgentService agentService,
                          DbDatasourceService dbDatasourceService,
                          FileService fileService,
                          KbDocumentService kbDocumentService,
                          CredentialVaultService credentialVaultService,
                          AdiMailSender adiMailSender,
                          UserMcpService userMcpService,
                          SkillToolService skillToolService,
                          SkillService skillService,
                          WorkflowStarter workflowStarter,
                          LLMCallRecordService llmCallRecordService,
                          AdiProperties adiProperties) {
        this.deps = new WfNodeDeps(validator, agentService, dbDatasourceService, fileService,
                kbDocumentService, credentialVaultService, adiMailSender, userMcpService,
                skillToolService, skillService, workflowStarter, llmCallRecordService, adiProperties);
    }

    public AbstractWfNode create(WorkflowComponent wfComponent, WorkflowNode nodeDefinition, WfState wfState, WfNodeState nodeState) {
        AbstractWfNode node = WfNodeFactory.create(wfComponent, nodeDefinition, wfState, nodeState);
        node.setDeps(deps);
        return node;
    }
}
