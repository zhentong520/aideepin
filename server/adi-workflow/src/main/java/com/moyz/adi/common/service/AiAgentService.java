package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.AgentWorkflow;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.Workflow;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.AgentMapper;
import com.moyz.adi.common.mapper.AgentWorkflowMapper;
import com.moyz.adi.common.mapper.CharacterMapper;
import com.moyz.adi.common.mapper.WorkflowMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Agent 服务：智能体定义、工作设计、工作流绑定。
 * <p>
 * Agent service — agent definition, work design rendering and workflow binding.
 * Creating an agent automatically creates its runtime Character so that the
 * standard chat pipeline ({@code /chat/process}) can talk to it directly.
 * </p>
 */
@Slf4j
@Service
public class AiAgentService extends ServiceImpl<AgentMapper, Agent> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Resource
    private AgentWorkflowMapper agentWorkflowMapper;

    @Resource
    private CharacterMapper characterMapper;

    @Resource
    private WorkflowMapper workflowMapper;

    /**
     * 全局智能体列表：所有用户共用同一批智能体，仅下发启用的。
     * <p>
     * Global agent list: agents are configured backstage and shared by every user.
     * </p>
     */
    public List<Agent> listMine() {
        return list(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getIsEnable, true)
                .eq(Agent::getIsDeleted, false)
                .orderByAsc(Agent::getSort)
                .orderByDesc(Agent::getId));
    }

    /**
     * 管理端列表：含停用，不做 userId 过滤。
     * <p>
     * Admin listing: includes disabled agents, no owner filter.
     * </p>
     */
    public List<Agent> listAll() {
        return list(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getIsDeleted, false)
                .orderByAsc(Agent::getSort)
                .orderByDesc(Agent::getId));
    }

    public Agent getByUuid(String uuid) {
        Agent agent = getOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUuid, uuid)
                .eq(Agent::getIsDeleted, false));
        if (null == agent) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return agent;
    }

    private Long currentUserId() {
        return ThreadContext.getExistCurrentUser().getId();
    }

    /**
     * 新增或更新 Agent，创建时自动生成对话角色。
     * Create or update an agent; a runtime Character is created on first save.
     */
    @Transactional(rollbackFor = Exception.class)
    public Agent saveOrUpdateAgent(Agent agent) {
        if (StringUtils.isBlank(agent.getTitle())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        if (StringUtils.isBlank(agent.getUuid())) {
            agent.setUuid(UUID.randomUUID().toString().replace("-", ""));
            //userId only records who created it. 0 = backend owned, matching migration 033.
            agent.setUserId(0L);
            //NOT NULL columns: never let a missing optional field break the insert
            agent.setRemark(StringUtils.defaultString(agent.getRemark()));
            agent.setAvatarUrl(StringUtils.defaultString(agent.getAvatarUrl()));
            agent.setSystemMessage(StringUtils.defaultString(agent.getSystemMessage()));
            agent.setWorkDesign(StringUtils.defaultString(agent.getWorkDesign()));
            agent.setModelPlatform(StringUtils.defaultString(agent.getModelPlatform()));
            agent.setModelName(StringUtils.defaultString(agent.getModelName()));
            agent.setKbIds(StringUtils.defaultString(agent.getKbIds()));
            agent.setMcpIds(StringUtils.defaultString(agent.getMcpIds()));
            agent.setPluginIds(StringUtils.defaultString(agent.getPluginIds()));
            if (null == agent.getLlmTemperature()) {
                agent.setLlmTemperature(0.7D);
            }
            if (null == agent.getSort()) {
                agent.setSort(0);
            }
            if (null == agent.getEnableRag()) {
                agent.setEnableRag(true);
            }
            if (null == agent.getEnableMcp()) {
                agent.setEnableMcp(false);
            }
            if (null == agent.getEnableWebSearch()) {
                agent.setEnableWebSearch(false);
            }
            if (null == agent.getIsEnable()) {
                agent.setIsEnable(true);
            }
            //Always create a brand new template character: never adopt a characterUuid from the
            //request body, otherwise an agent could hijack another conversation. The template is
            //only a blueprint —每用户实际会话由 SceneService.resolveUserCharacterUuid 复制生成。
            Character character = buildCharacter(agent, 0L);
            characterMapper.insert(character);
            agent.setCharacterUuid(character.getUuid());
            save(agent);
        } else {
            Agent exist = getByUuid(agent.getUuid());
            exist.setTitle(agent.getTitle());
            //Partial update semantics: null means "not sent, keep the stored value"; an explicit
            //empty string still clears the field. Without this, a form that only posts a few
            //fields would wipe systemMessage / workDesign / model settings.
            exist.setRemark(patch(exist.getRemark(), agent.getRemark()));
            exist.setAvatarUrl(patch(exist.getAvatarUrl(), agent.getAvatarUrl()));
            exist.setSystemMessage(patch(exist.getSystemMessage(), agent.getSystemMessage()));
            exist.setWorkDesign(patch(exist.getWorkDesign(), agent.getWorkDesign()));
            exist.setModelPlatform(patch(exist.getModelPlatform(), agent.getModelPlatform()));
            exist.setModelName(patch(exist.getModelName(), agent.getModelName()));
            exist.setKbIds(patch(exist.getKbIds(), agent.getKbIds()));
            exist.setMcpIds(patch(exist.getMcpIds(), agent.getMcpIds()));
            exist.setPluginIds(patch(exist.getPluginIds(), agent.getPluginIds()));
            if (null != agent.getLlmTemperature()) {
                exist.setLlmTemperature(agent.getLlmTemperature());
            }
            if (null != agent.getEnableRag()) {
                exist.setEnableRag(agent.getEnableRag());
            }
            if (null != agent.getEnableMcp()) {
                exist.setEnableMcp(agent.getEnableMcp());
            }
            if (null != agent.getEnableWebSearch()) {
                exist.setEnableWebSearch(agent.getEnableWebSearch());
            }
            if (null != agent.getSort()) {
                exist.setSort(agent.getSort());
            }
            if (null != agent.getIsEnable()) {
                exist.setIsEnable(agent.getIsEnable());
            }
            updateById(exist);
            syncCharacter(exist);
            agent = exist;
        }
        return agent;
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDelete(String uuid) {
        Agent exist = getByUuid(uuid);
        exist.setIsDeleted(true);
        updateById(exist);
        //Soft delete the workflow bindings instead of a hard delete: keeps the audit trail and
        //avoids destroying rows another record may still point at.
        for (AgentWorkflow bind : agentWorkflowMapper.selectList(new LambdaQueryWrapper<AgentWorkflow>()
                .eq(AgentWorkflow::getAgentUuid, uuid)
                .eq(AgentWorkflow::getIsDeleted, false))) {
            bind.setIsDeleted(true);
            agentWorkflowMapper.updateById(bind);
        }
        //Drop the template character as well, otherwise it stays as an orphan role. Per-user
        //conversations copied from it are intentionally left alone: they hold the user's own
        //message history, and resolveUserCharacterUuid() stops handing this agent out once the
        //agent row is soft deleted.
        if (StringUtils.isNotBlank(exist.getCharacterUuid())) {
            Character character = characterMapper.selectOne(new LambdaQueryWrapper<Character>()
                    .eq(Character::getUuid, exist.getCharacterUuid())
                    .eq(Character::getIsDeleted, false)
                    .last("limit 1"));
            if (null != character) {
                character.setIsDeleted(true);
                characterMapper.updateById(character);
            }
        }
    }

    public List<Workflow> listWorkflows(String agentUuid) {
        getByUuid(agentUuid);
        List<AgentWorkflow> binds = agentWorkflowMapper.selectList(new LambdaQueryWrapper<AgentWorkflow>()
                .eq(AgentWorkflow::getAgentUuid, agentUuid)
                .eq(AgentWorkflow::getIsDeleted, false)
                .orderByAsc(AgentWorkflow::getSort));
        List<Workflow> result = new ArrayList<>();
        for (AgentWorkflow bind : binds) {
            Workflow wf = workflowMapper.selectOne(new LambdaQueryWrapper<Workflow>()
                    .eq(Workflow::getUuid, bind.getWorkflowUuid())
                    .eq(Workflow::getIsDeleted, false));
            if (null != wf) {
                result.add(wf);
            }
        }
        return result;
    }

    /**
     * 绑定工作流（全量覆盖）。
     * Bind workflows to an agent (full replace).
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindWorkflows(String agentUuid, List<String> workflowUuids, String defaultWorkflowUuid) {
        getByUuid(agentUuid);
        //Reuse existing rows instead of delete-all + insert-all: repeated calls with the same
        //payload are idempotent and concurrent calls cannot create duplicate bindings.
        Map<String, AgentWorkflow> existing = new HashMap<>();
        for (AgentWorkflow bind : agentWorkflowMapper.selectList(new LambdaQueryWrapper<AgentWorkflow>()
                .eq(AgentWorkflow::getAgentUuid, agentUuid))) {
            existing.put(bind.getWorkflowUuid(), bind);
        }
        if (null != workflowUuids && !workflowUuids.isEmpty()) {
            int sort = 0;
            for (String wfUuid : workflowUuids) {
                if (StringUtils.isBlank(wfUuid)) {
                    continue;
                }
                Workflow wf = workflowMapper.selectOne(new LambdaQueryWrapper<Workflow>()
                        .eq(Workflow::getUuid, wfUuid)
                        .eq(Workflow::getIsDeleted, false));
                if (null == wf) {
                    throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
                }
                // Workflows are user-scoped: without this check an agent could bind (and later
                // execute) another user's private workflow — a cross-user privilege hole.
                // Same visibility rule as WorkflowService listing: admins see all, users see their own.
                Long operatorId = currentUserId();
                boolean isAdmin = Boolean.TRUE.equals(ThreadContext.getCurrentUser().getIsAdmin());
                if (!isAdmin && !operatorId.equals(wf.getUserId())) {
                    throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
                }
                AgentWorkflow bind = existing.remove(wfUuid);
                boolean isDefault = wfUuid.equals(defaultWorkflowUuid);
                if (null == bind) {
                    bind = new AgentWorkflow();
                    bind.setUuid(UUID.randomUUID().toString().replace("-", ""));
                    bind.setAgentUuid(agentUuid);
                    bind.setWorkflowUuid(wfUuid);
                    bind.setIsDefault(isDefault);
                    bind.setSort(sort++);
                    bind.setIsDeleted(false);
                    agentWorkflowMapper.insert(bind);
                } else if (Boolean.TRUE.equals(bind.getIsDeleted())
                        || !Objects.equals(bind.getSort(), sort)
                        || !Objects.equals(bind.getIsDefault(), isDefault)) {
                    bind.setIsDeleted(false);
                    bind.setIsDefault(isDefault);
                    bind.setSort(sort++);
                    agentWorkflowMapper.updateById(bind);
                } else {
                    sort++;
                }
            }
        }
        for (AgentWorkflow stale : existing.values()) {
            if (!Boolean.TRUE.equals(stale.getIsDeleted())) {
                stale.setIsDeleted(true);
                agentWorkflowMapper.updateById(stale);
            }
        }
    }

    /**
     * 局部更新取值：入参为 null 表示“未提交”，保留原值；显式空串表示“清空”。
     * Patch semantics for text columns: null = not sent (keep stored value), "" = clear.
     */
    private String patch(String stored, String incoming) {
        if (null == incoming) {
            return StringUtils.defaultString(stored);
        }
        return incoming;
    }

    private Character buildCharacter(Agent agent, Long userId) {
        Character character = new Character();
        character.setUuid(UUID.randomUUID().toString().replace("-", ""));
        character.setUserId(userId);
        character.setTitle(StringUtils.isBlank(agent.getTitle()) ? "agent" : agent.getTitle());
        character.setRemark(StringUtils.isBlank(agent.getRemark()) ? "" : agent.getRemark());
        character.setAiSystemMessage(buildSystemMessage(agent));
        character.setLlmTemperature(null == agent.getLlmTemperature() ? 0.7D : agent.getLlmTemperature());
        character.setUnderstandContextEnable(true);
        character.setAnswerContentType(2);
        character.setIsEnableThinking(false);
        character.setIsEnableWebSearch(Boolean.TRUE.equals(agent.getEnableWebSearch()));
        character.setIsAutoplayAnswer(false);
        character.setKbIds(StringUtils.isBlank(agent.getKbIds()) ? "" : agent.getKbIds());
        character.setMcpIds(StringUtils.isBlank(agent.getMcpIds()) ? "" : agent.getMcpIds());
        character.setPluginIds(StringUtils.isBlank(agent.getPluginIds()) ? "" : agent.getPluginIds());
        return character;
    }

    private void syncCharacter(Agent agent) {
        if (StringUtils.isBlank(agent.getCharacterUuid())) {
            return;
        }
        Character character = characterMapper.selectOne(new LambdaQueryWrapper<Character>()
                .eq(Character::getUuid, agent.getCharacterUuid())
                .eq(Character::getIsDeleted, false));
        if (null == character) {
            return;
        }
        character.setTitle(agent.getTitle());
        character.setAiSystemMessage(buildSystemMessage(agent));
        character.setKbIds(StringUtils.isBlank(agent.getKbIds()) ? "" : agent.getKbIds());
        character.setMcpIds(StringUtils.isBlank(agent.getMcpIds()) ? "" : agent.getMcpIds());
        character.setPluginIds(StringUtils.isBlank(agent.getPluginIds()) ? "" : agent.getPluginIds());
        character.setIsEnableWebSearch(Boolean.TRUE.equals(agent.getEnableWebSearch()));
        if (null != agent.getLlmTemperature()) {
            character.setLlmTemperature(agent.getLlmTemperature());
        }
        characterMapper.updateById(character);
    }

    /**
     * 将系统提示词与工作设计合并成最终的角色设定。
     * Merge system message and work design into the final character system prompt.
     */
    public String buildSystemMessage(Agent agent) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(agent.getSystemMessage())) {
            sb.append(agent.getSystemMessage()).append("\n");
        }
        if (StringUtils.isBlank(agent.getWorkDesign())) {
            return sb.toString();
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(agent.getWorkDesign());
            String goal = text(node, "goal");
            String constraints = text(node, "constraints");
            String outputFormat = text(node, "outputFormat");
            if (StringUtils.isNotBlank(goal)) {
                sb.append("\n## 工作目标 / Goal\n").append(goal).append("\n");
            }
            JsonNode steps = node.get("steps");
            if (null != steps && steps.isArray() && steps.size() > 0) {
                sb.append("\n## 执行步骤 / Steps\n");
                int idx = 1;
                for (JsonNode step : steps) {
                    String stepText = step.isTextual() ? step.asText() : step.toString();
                    if (StringUtils.isBlank(stepText)) {
                        continue;
                    }
                    sb.append(idx++).append(". ").append(stepText).append("\n");
                }
            }
            if (StringUtils.isNotBlank(constraints)) {
                sb.append("\n## 约束 / Constraints\n").append(constraints).append("\n");
            }
            if (StringUtils.isNotBlank(outputFormat)) {
                sb.append("\n## 输出格式 / Output Format\n").append(outputFormat).append("\n");
            }
        } catch (Exception e) {
            log.warn("work design is not valid json, append as plain text, agent:{}", agent.getUuid());
            sb.append("\n").append(agent.getWorkDesign()).append("\n");
        }
        return sb.toString();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return null == value || value.isNull() ? "" : value.asText();
    }
}
