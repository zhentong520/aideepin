package com.moyz.adi.common.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.moyz.adi.common.base.ThreadContext;
import com.moyz.adi.common.entity.Agent;
import com.moyz.adi.common.entity.Character;
import com.moyz.adi.common.entity.Scene;
import com.moyz.adi.common.entity.SceneAgent;
import com.moyz.adi.common.entity.UserAgentConv;
import com.moyz.adi.common.enums.ErrorEnum;
import com.moyz.adi.common.exception.BaseException;
import com.moyz.adi.common.mapper.AgentMapper;
import com.moyz.adi.common.mapper.CharacterMapper;
import com.moyz.adi.common.mapper.SceneAgentMapper;
import com.moyz.adi.common.mapper.SceneMapper;
import com.moyz.adi.common.mapper.UserAgentConvMapper;
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
 * 场景服务：场景与智能体由管理员在后台统一配置，对所有用户可见；用户端只读场景列表。
 * <p>
 * Scene service — scenes and agents are configured backstage and shared by every user;
 * the user side is read-only and only picks a scene.
 * </p>
 * <p>
 * <b>为什么每个用户要单独落一份会话</b>：智能体上的 {@code characterUuid} 只是模板。
 * 直接让所有用户共用它会同时踩两个坑——多处代码按 uuid 单条查询（{@code oneOpt()} 遇多行
 * 直接抛异常），以及短期记忆以 characterUuid 为键（会串到别人的对话内容）。
 * 因此这里为每个用户在每个智能体上落一份自己的会话实例（见 {@code adi_user_agent_conv}）。
 * </p>
 */
@Slf4j
@Service
public class SceneService extends ServiceImpl<SceneMapper, Scene> {

    @Resource
    private SceneAgentMapper sceneAgentMapper;

    @Resource
    private UserAgentConvMapper userAgentConvMapper;

    @Resource
    private CharacterMapper characterMapper;

    @Resource
    private AgentMapper agentMapper;

    /**
     * 用户端场景列表：全局共享，只下发启用的。
     * <p>
     * Global scene list for end users: shared by everyone, only enabled ones are served.
     * </p>
     */
    public List<Scene> listMine() {
        return list(new LambdaQueryWrapper<Scene>()
                .eq(Scene::getIsEnable, true)
                .eq(Scene::getIsDeleted, false)
                .orderByAsc(Scene::getSort)
                .orderByDesc(Scene::getId));
    }

    /**
     * 管理端场景列表：全部（含停用），不做 userId 过滤。
     * <p>
     * Admin scene list: everything including disabled, no owner filter.
     * </p>
     */
    public List<Scene> listAll() {
        return list(new LambdaQueryWrapper<Scene>()
                .eq(Scene::getIsDeleted, false)
                .orderByAsc(Scene::getSort)
                .orderByDesc(Scene::getId));
    }

    public Scene getByUuid(String uuid) {
        Scene scene = getOne(new LambdaQueryWrapper<Scene>()
                .eq(Scene::getUuid, uuid)
                .eq(Scene::getIsDeleted, false));
        if (null == scene) {
            throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
        }
        return scene;
    }

    public Scene saveOrUpdateScene(Scene scene) {
        if (StringUtils.isBlank(scene.getTitle())) {
            throw new BaseException(ErrorEnum.A_PARAMS_ERROR);
        }
        if (StringUtils.isBlank(scene.getUuid())) {
            scene.setUuid(UUID.randomUUID().toString().replace("-", ""));
            //userId now only records who created it. 0 means "system/backend owned",
            //matching the migration that normalised existing rows to 0.
            scene.setUserId(0L);
            //NOT NULL columns: never let a missing optional field break the insert
            scene.setRemark(StringUtils.defaultString(scene.getRemark()));
            scene.setIcon(StringUtils.defaultString(scene.getIcon()));
            if (null == scene.getSort()) {
                scene.setSort(0);
            }
            if (null == scene.getIsEnable()) {
                scene.setIsEnable(true);
            }
            save(scene);
        } else {
            Scene exist = getByUuid(scene.getUuid());
            exist.setTitle(scene.getTitle());
            //Same partial-update semantics as the agent: null keeps the stored value
            exist.setRemark(null == scene.getRemark() ? StringUtils.defaultString(exist.getRemark()) : scene.getRemark());
            exist.setIcon(null == scene.getIcon() ? StringUtils.defaultString(exist.getIcon()) : scene.getIcon());
            if (null != scene.getSort()) {
                exist.setSort(scene.getSort());
            }
            if (null != scene.getIsEnable()) {
                exist.setIsEnable(scene.getIsEnable());
            }
            updateById(exist);
            scene = exist;
        }
        return scene;
    }

    @Transactional(rollbackFor = Exception.class)
    public void softDelete(String uuid) {
        Scene exist = getByUuid(uuid);
        exist.setIsDeleted(true);
        updateById(exist);
        //Soft delete the bindings instead of a hard delete: matches the rest of the codebase
        //and keeps the audit trail of what this scene used to serve.
        for (SceneAgent bind : sceneAgentMapper.selectList(new LambdaQueryWrapper<SceneAgent>()
                .eq(SceneAgent::getSceneUuid, uuid)
                .eq(SceneAgent::getIsDeleted, false))) {
            bind.setIsDeleted(true);
            sceneAgentMapper.updateById(bind);
        }
    }

    /**
     * 场景绑定的 Agent 列表。
     * Agents bound to a scene.
     */
    public List<Agent> listAgents(String sceneUuid) {
        //Scenes are global now, so there is no per-user ownership check here: every logged-in
        //user is allowed to read the agents behind an enabled scene. Admin listing additionally
        //wants disabled agents, hence the flag.
        return listAgentsInternal(sceneUuid, false);
    }

    /**
     * 管理端：含停用智能体。
     * Admin listing: include disabled agents so the console can re-enable them.
     */
    public List<Agent> listAgentsForAdmin(String sceneUuid) {
        return listAgentsInternal(sceneUuid, true);
    }

    private List<Agent> listAgentsInternal(String sceneUuid, boolean includeDisabled) {
        List<SceneAgent> binds = sceneAgentMapper.selectList(new LambdaQueryWrapper<SceneAgent>()
                .eq(SceneAgent::getSceneUuid, sceneUuid)
                .eq(SceneAgent::getIsDeleted, false)
                .orderByAsc(SceneAgent::getSort));
        List<Agent> agents = new ArrayList<>();
        for (SceneAgent bind : binds) {
            Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                    .eq(Agent::getUuid, bind.getAgentUuid())
                    .eq(Agent::getIsDeleted, false));
            if (null == agent) {
                continue;
            }
            //Skip disabled agents for end users: a disabled agent must not be reachable at all,
            //otherwise "enabled" would only mean "hidden in some dropdown".
            if (!includeDisabled && !Boolean.TRUE.equals(agent.getIsEnable())) {
                continue;
            }
            agents.add(agent);
        }
        return agents;
    }

    /**
     * 绑定 Agent（全量覆盖）。
     * Bind agents to a scene (full replace).
     */
    @Transactional(rollbackFor = Exception.class)
    public void bindAgents(String sceneUuid, List<String> agentUuids) {
        //Existence check only: scenes and agents are globally shared, so the old
        //"agent must belong to the scene owner" rule no longer applies (and would make it
        //impossible to bind an agent created by a different admin account).
        getByUuid(sceneUuid);
        //Reuse existing rows instead of delete-all + insert-all: repeated calls with the same
        //payload are idempotent and concurrent calls cannot create duplicate bindings.
        Map<String, SceneAgent> existing = new HashMap<>();
        for (SceneAgent bind : sceneAgentMapper.selectList(new LambdaQueryWrapper<SceneAgent>()
                .eq(SceneAgent::getSceneUuid, sceneUuid))) {
            existing.put(bind.getAgentUuid(), bind);
        }
        if (null != agentUuids && !agentUuids.isEmpty()) {
            int sort = 0;
            for (String agentUuid : agentUuids) {
                if (StringUtils.isBlank(agentUuid)) {
                    continue;
                }
                Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                        .eq(Agent::getUuid, agentUuid)
                        .eq(Agent::getIsDeleted, false));
                if (null == agent) {
                    throw new BaseException(ErrorEnum.A_DATA_NOT_FOUND);
                }
                SceneAgent bind = existing.remove(agentUuid);
                if (null == bind) {
                    bind = new SceneAgent();
                    bind.setUuid(UUID.randomUUID().toString().replace("-", ""));
                    bind.setSceneUuid(sceneUuid);
                    bind.setAgentUuid(agentUuid);
                    bind.setSort(sort++);
                    bind.setIsDeleted(false);
                    sceneAgentMapper.insert(bind);
                } else if (Boolean.TRUE.equals(bind.getIsDeleted()) || !Objects.equals(bind.getSort(), sort)) {
                    bind.setIsDeleted(false);
                    bind.setSort(sort);
                    sort++;
                    sceneAgentMapper.updateById(bind);
                } else {
                    sort++;
                }
            }
        }
        //Whatever is left is no longer bound: soft delete instead of a blanket delete
        for (SceneAgent stale : existing.values()) {
            if (!Boolean.TRUE.equals(stale.getIsDeleted())) {
                stale.setIsDeleted(true);
                sceneAgentMapper.updateById(stale);
            }
        }
    }

    /**
     * 解析「当前用户 × 智能体」对应的会话 uuid，没有就建一个。
     * <p>
     * Resolve (and lazily create) the current user's own conversation for an agent.
     * </p>
     * <p>
     * 为什么不直接用 {@code agent.characterUuid}：那是「模板」会话，所有用户共用会同时踩
     * 两个坑——① 多处代码按 uuid 单条查询（{@code oneOpt()} 命中多行直接抛异常）；
     * ② 短期记忆 {@code MapDBChatMemoryStore} 以 characterUuid 为键，会串到别人的对话内容。
     * 所以这里给每个用户复制一份独立会话，系统提示词/知识库/模型等从智能体模板继承。
     * </p>
     *
     * @return 该用户自己的会话 uuid；智能体不存在时返回空串
     */
    @Transactional(rollbackFor = Exception.class)
    public String resolveUserCharacterUuid(String agentUuid) {
        if (StringUtils.isBlank(agentUuid)) {
            return StringUtils.EMPTY;
        }
        Long userId = ThreadContext.getExistCurrentUser().getId();
        //NOTE: `last("limit 1")` is load-bearing, not cosmetic. MyBatis-Plus' selectOne throws
        //TooManyResultsException on >1 row, and the partial unique index on
        //(user_id, agent_uuid) only guards *active* rows — a soft-deleted duplicate can still
        //exist. Taking the newest row keeps this read total instead of exploding.
        UserAgentConv conv = userAgentConvMapper.selectOne(new LambdaQueryWrapper<UserAgentConv>()
                .eq(UserAgentConv::getUserId, userId)
                .eq(UserAgentConv::getAgentUuid, agentUuid)
                .eq(UserAgentConv::getIsDeleted, false)
                .orderByDesc(UserAgentConv::getId)
                .last("limit 1"));
        if (null != conv) {
            //Trust but verify: the binding row can outlive a character that was deleted
            //elsewhere (user deleted the conversation). Recreate instead of sending the
            //frontend a uuid that would 400 on the first message.
            Character exist = characterMapper.selectOne(new LambdaQueryWrapper<Character>()
                    .eq(Character::getUuid, conv.getCharacterUuid())
                    .eq(Character::getUserId, userId)
                    .eq(Character::getIsDeleted, false)
                    .last("limit 1"));
            if (null != exist) {
                //2026-10-05: 会话是「创建时快照」，但产品语义要求「后台改配置、用户端立刻生效」。
                //管理员改了智能体的系统提示词/温度/知识库/MCP/联网开关后，老会话不会自动跟上，
                //实测后台把 systemMessage 改成含暗号的话，用户端老会话回答里完全不出现。
                //所以这里把后台配置同步进已有会话：只同步「配置类」字段，
                //不碰 uuid / title / 历史消息，保证对话连续性不受影响。
                syncFromAgent(exist, agentUuid);
                return conv.getCharacterUuid();
            }
            conv.setIsDeleted(true);
            userAgentConvMapper.updateById(conv);
        }
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUuid, agentUuid)
                .eq(Agent::getIsDeleted, false)
                .last("limit 1"));
        if (null == agent) {
            return StringUtils.EMPTY;
        }
        Character character = copyTemplateCharacter(agent, userId);
        characterMapper.insert(character);
        UserAgentConv created = new UserAgentConv();
        created.setUuid(UUID.randomUUID().toString().replace("-", ""));
        created.setUserId(userId);
        created.setAgentUuid(agentUuid);
        created.setCharacterUuid(character.getUuid());
        created.setIsDeleted(false);
        userAgentConvMapper.insert(created);
        return character.getUuid();
    }

    /**
     * 把智能体（后台配置）的最新配置同步到已存在的用户会话。
     * <p>
     * 只同步「管理员在后台能改的配置项」：系统提示词、温度、知识库、MCP、联网开关。
     * 刻意不碰 uuid / title / remark / 历史消息——会话是用户自己的，对话连续性优先。
     * </p>
     * <p>
     * 无变化时不写库：resolveConv 每次进入聊天页都会调，无脑 update 会把
     * {@code update_time} 刷成噪声，还可能在高并发下撞同一行的锁。
     * </p>
     */
    private void syncFromAgent(Character character, String agentUuid) {
        Agent agent = agentMapper.selectOne(new LambdaQueryWrapper<Agent>()
                .eq(Agent::getUuid, agentUuid)
                .eq(Agent::getIsDeleted, false)
                .last("limit 1"));
        if (null == agent) {
            return;
        }
        String systemMessage = StringUtils.isNotBlank(agent.getSystemMessage())
                ? agent.getSystemMessage()
                : character.getAiSystemMessage();
        Double temperature = null == agent.getLlmTemperature() ? 0.7D : agent.getLlmTemperature();
        String kbIds = StringUtils.defaultString(agent.getKbIds());
        String mcpIds = StringUtils.defaultString(agent.getMcpIds());
        //2026-10-28: 业务插件码也属于后台可改的配置项，必须同步（T9-M2）
        String pluginIds = StringUtils.defaultString(agent.getPluginIds());
        boolean webSearch = Boolean.TRUE.equals(agent.getEnableWebSearch());

        boolean unchanged = Objects.equals(systemMessage, character.getAiSystemMessage())
                && Objects.equals(temperature, character.getLlmTemperature())
                && Objects.equals(kbIds, StringUtils.defaultString(character.getKbIds()))
                && Objects.equals(mcpIds, StringUtils.defaultString(character.getMcpIds()))
                && Objects.equals(pluginIds, StringUtils.defaultString(character.getPluginIds()))
                && webSearch == Boolean.TRUE.equals(character.getIsEnableWebSearch());
        if (unchanged) {
            return;
        }
        character.setAiSystemMessage(systemMessage);
        character.setLlmTemperature(temperature);
        character.setKbIds(kbIds);
        character.setMcpIds(mcpIds);
        character.setPluginIds(pluginIds);
        character.setIsEnableWebSearch(webSearch);
        characterMapper.updateById(character);
    }

    /**
     * 从智能体模板复制一份属于该用户的会话。
     * Copy the agent's template character into a conversation owned by this user.
     */
    private Character copyTemplateCharacter(Agent agent, Long userId) {
        Character template = null;
        if (StringUtils.isNotBlank(agent.getCharacterUuid())) {
            template = characterMapper.selectOne(new LambdaQueryWrapper<Character>()
                    .eq(Character::getUuid, agent.getCharacterUuid())
                    .eq(Character::getIsDeleted, false)
                    .last("limit 1"));
        }
        Character character = new Character();
        //Fresh uuid per user: this is what keeps conversations (and short-term memory) isolated.
        character.setUuid(UUID.randomUUID().toString().replace("-", ""));
        character.setUserId(userId);
        character.setTitle(StringUtils.isNotBlank(agent.getTitle()) ? agent.getTitle() : "agent");
        character.setRemark(StringUtils.defaultString(agent.getRemark()));
        character.setAiSystemMessage(StringUtils.isNotBlank(agent.getSystemMessage())
                ? agent.getSystemMessage()
                : (null == template ? StringUtils.EMPTY : template.getAiSystemMessage()));
        character.setLlmTemperature(null == agent.getLlmTemperature() ? 0.7D : agent.getLlmTemperature());
        character.setUnderstandContextEnable(true);
        //Always text: the chat page renders text answers, and audio would need a TTS voice
        //configured on a per-user conversation we do not create here.
        character.setAnswerContentType(2);
        character.setIsEnableThinking(false);
        character.setIsEnableWebSearch(Boolean.TRUE.equals(agent.getEnableWebSearch()));
        character.setIsAutoplayAnswer(false);
        character.setKbIds(StringUtils.defaultString(agent.getKbIds()));
        character.setMcpIds(StringUtils.defaultString(agent.getMcpIds()));
        character.setPluginIds(StringUtils.defaultString(agent.getPluginIds()));
        character.setSkillIds(null == template ? StringUtils.EMPTY : StringUtils.defaultString(template.getSkillIds()));
        character.setIsDeleted(false);
        return character;
    }
}
