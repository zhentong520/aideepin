import { defineStore } from 'pinia'
import { ref } from 'vue'
// 直接引用 app 模块而不是 '@/store' 聚合出口：后者会级联导出所有模块，
// scene -> store/index -> modules -> scene 形成循环依赖。
import { useAppStore } from '@/store/modules/app'
import { emptyAiModel } from '@/utils/functions'
import api from '@/api'

/**
 * 场景 store —— 用户端对话的唯一入口。
 *
 * 设计要点（2026-10-05）：
 * 场景与智能体都由管理员在管理后台配置，**对所有用户共享**；用户端只读场景列表，
 * 不提供任何创建入口，界面也不出现模型/厂商。因此这里做三件事：
 *   1. 拉取场景列表，并为每个场景解析出「默认智能体」（绑定顺序最靠前的 Agent）；
 *   2. 用 `/agent/resolveConv` 换取**当前用户自己**的会话 uuid
 *      （不是智能体上那个模板 characterUuid —— 那是全局共享的，共用会串对话记录）；
 *   3. 发送消息所需的 modelPlatform / modelName 一律从默认智能体取，
 *      绝不回退到用户在界面上选过的模型（界面上已经没有模型选择器了）。
 *
 * Scene store — the only entry point for user chat.
 */
export const useSceneStore = defineStore('scene-store', () => {
  const scenes = ref<Chat.Scene[]>([])
  // sceneUuid -> 该场景的默认智能体
  const defaultAgents = ref<Record<string, Chat.Agent | null>>({})
  // sceneUuid -> 当前用户在该场景下的会话 uuid
  const characterUuids = ref<Record<string, string>>({})
  const loading = ref<boolean>(false)
  const loaded = ref<boolean>(false)

  /** 取场景的默认智能体：SceneService.listAgents 已按 sort 升序返回，首个即默认。 */
  function defaultAgentOf(sceneUuid: string): Chat.Agent | null {
    return defaultAgents.value[sceneUuid] ?? null
  }

  /** 侧边栏渲染项：场景 + 默认智能体 + 该用户在此场景下的会话 uuid。 */
  const entries = ref<Chat.SceneEntry[]>([])

  function buildEntry(scene: Chat.Scene): Chat.SceneEntry {
    const agent = defaultAgentOf(scene.uuid)
    // 场景没绑智能体（或智能体没配模型）时 characterUuid 为空串，
    // 侧边栏据此禁用该项——宁可明确不可用，也不要静默拿一个模型裸聊。
    return {
      scene,
      agent,
      characterUuid: characterUuids.value[scene.uuid] ?? '',
    }
  }

  /**
   * 解析当前对话应该使用的模型。
   * 这是页面唯一获取模型的地方——UI 不暴露模型，所以这里必须可靠。
   */
  function resolveModel(sceneUuid: string): { modelPlatform: string, modelName: string } | null {
    const agent = defaultAgentOf(sceneUuid)
    if (!agent || !agent.modelName)
      return null
    return { modelPlatform: agent.modelPlatform, modelName: agent.modelName }
  }

  /** 当前激活会话（characterUuid）所属场景的默认智能体所绑定的模型。 */
  function resolveModelByCharacter(characterUuid: string): { modelPlatform: string, modelName: string } | null {
    const hit = entries.value.find(item => item.characterUuid === characterUuid)
    if (!hit)
      return null
    return resolveModel(hit.scene.uuid)
  }

  /**
   * 当前会话（characterUuid）所属场景的标题。
   *
   * 聊天页头部优先显示这个而不是 character.title：character 是从后台智能体模板
   * 复制出来的会话，title 就是智能体名（「通用智能体」之类）。用户端的产品语义里
   * 只有「场景」，显示智能体名等于把后台概念漏给用户。
   */
  function resolveSceneTitle(characterUuid: string): string {
    if (!characterUuid)
      return ''
    return entries.value.find(item => item.characterUuid === characterUuid)?.scene.title ?? ''
  }

  /**
   * 当前会话（characterUuid）背后那套模型的「能力信息」。
   *
   * 注意与 resolveModelByCharacter 的区别：这里返回的是能力元数据
   * （是否推理模型、支不支持图片输入、支不支持联网），供工具栏决定按钮是否可用；
   * 模型名本身不返回到界面，避免又把它显示出来。
   */
  function resolveModelCapability(characterUuid: string): AiModelInfo {
    const model = resolveModelByCharacter(characterUuid)
    if (!model)
      return emptyAiModel()
    const appStore = useAppStore()
    // appStore.llms 来自 /model/list，已在登录时加载；这里只取能力字段，不改 selectedLLM，
    // 因为 selectedLLM 是「用户选过哪个模型」的状态，场景模式下已无意义。
    const hit = appStore.llms.find(item => item.modelName === model.modelName)
    if (hit)
      return hit
    // 模型没在可用列表里（例如后台刚改配置未同步）：给一个安全的空能力集，
    // 工具栏会据此关掉图片/联网等入口，而不是让用户点了才报错。
    const fallback = emptyAiModel()
    fallback.modelName = model.modelName
    fallback.modelPlatform = model.modelPlatform
    return fallback
  }

  async function loadScenes(force = false) {
    if (loading.value)
      return
    if (loaded.value && !force) {
      entries.value = scenes.value.map(buildEntry)
      return
    }
    loading.value = true
    try {
      const { data: sceneList } = await api.fetchScenes<Chat.Scene[]>()
      // 后端已按 is_enable 过滤，这里再兜一层：停用的场景不该出现在用户端。
      const list = (sceneList ?? []).filter(item => item.isEnable !== false)
      scenes.value = list

      const agentMap: Record<string, Chat.Agent | null> = {}
      const convMap: Record<string, string> = {}
      await Promise.all(
        list.map(async (scene) => {
          try {
            const { data: agents } = await api.fetchSceneAgents<Chat.Agent[]>(scene.uuid)
            const enabled = (agents ?? []).filter(item => item.isEnable !== false)
            // 后端 listAgents 已按 SceneAgent.sort 升序返回，首个即默认智能体。
            // 这里不再自行排序：Agent 实体没有 sort 字段，按 id 排会与绑定顺序冲突，反而让默认智能体漂移。
            const agent = enabled[0] ?? null
            agentMap[scene.uuid] = agent
            // 只有绑定了智能体才需要换取会话；没绑定的场景保持空串，
            // 侧边栏会把它显示成不可点击，而不是给一个必然失败的 uuid。
            if (!agent)
              return
            const { data: characterUuid } = await api.resolveAgentConv(agent.uuid)
            convMap[scene.uuid] = characterUuid || ''
          }
          catch (error) {
            // 单个场景取智能体失败不应让整个侧边栏空白
            console.error(`load scene agents failed: ${scene.uuid}`, error)
            agentMap[scene.uuid] = null
          }
        }),
      )
      defaultAgents.value = agentMap
      characterUuids.value = convMap
      entries.value = list.map(buildEntry)
      loaded.value = true
    }
    finally {
      loading.value = false
    }
  }

  function reset() {
    scenes.value = []
    defaultAgents.value = {}
    characterUuids.value = {}
    entries.value = []
    loaded.value = false
    loading.value = false
  }

  /**
   * 重新解析某场景下当前用户的会话 uuid（2026-10-08 修复）。
   *
   * 场景：用户在 EditConvDetail 删除了自己的会话后，本 store 缓存的
   * characterUuid 已失效，但没有人刷新它——侧边栏会把用户路由到一个
   * 已删除的 uuid，表现为「暂无对话记录」，直到整页刷新才自愈。
   * 后端 resolveConv 对已删角色会自动重建（旧绑定行标删 + 新建会话），
   * 所以这里只需重调一次 resolveConv 让前端缓存跟上后端。
   */
  async function revalidateConv(sceneUuid: string): Promise<string> {
    const agent = defaultAgentOf(sceneUuid)
    if (!agent)
      return ''
    const { data: characterUuid } = await api.resolveAgentConv(agent.uuid)
    characterUuids.value = { ...characterUuids.value, [sceneUuid]: characterUuid || '' }
    entries.value = scenes.value.map(buildEntry)
    return characterUuid || ''
  }

  return {
    scenes,
    entries,
    defaultAgents,
    characterUuids,
    loading,
    loaded,
    defaultAgentOf,
    resolveModel,
    resolveModelByCharacter,
    resolveModelCapability,
    resolveSceneTitle,
    loadScenes,
    revalidateConv,
    reset,
  }
})
