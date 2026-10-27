package com.moyz.adi.common.workflow;

import com.moyz.adi.common.config.AdiProperties;
import com.moyz.adi.common.helper.AdiMailSender;
import com.moyz.adi.common.service.AgentService;
import com.moyz.adi.common.service.CredentialVaultService;
import com.moyz.adi.common.service.DbDatasourceService;
import com.moyz.adi.common.service.FileService;
import com.moyz.adi.common.service.KbDocumentService;
import com.moyz.adi.common.service.LLMCallRecordService;
import com.moyz.adi.common.service.UserMcpService;
import com.moyz.adi.common.service.skill.SkillToolService;
import com.moyz.adi.common.service.SkillService;
import jakarta.validation.Validator;

/**
 * 工作流节点依赖集合（S1-02）。
 * <p>
 * 节点实例由 {@link WfNodeFactory} 手动 new、不是 Spring bean，无法享受容器注入；
 * 以前各节点用 SpringUtil.getBean 服务定位器按需取 bean，现在统一改为：
 * {@code WfNodeProvider}（Spring bean）在创建节点后注入本对象，节点通过字段 deps 取依赖。
 * <p>
 * 依赖项为只读快照，节点执行期间不允许替换。纯单测（不经 provider）可用 {@link #empty()}。
 */
public record WfNodeDeps(
        Validator validator,
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
        AdiProperties adiProperties
) {

    /**
     * 供纯单测/无容器场景使用：全部依赖为 null。
     * 注意：只有不在 process 路径上取依赖的节点（如 Template/Start/End）可以安全使用。
     */
    public static WfNodeDeps empty() {
        return new WfNodeDeps(null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
