import { get, post } from '@/utils/request'
import { commonSseProcess } from './sse'

function fetchCharacters<T>() {
  return get<T>({
    url: '/character/list',
  })
}

function characterAdd<T>(params: { title: string; remark: string; aiSystemMessage: string }) {
  return post<T>({
    url: '/character/add',
    data: {
      ...params,
    },
  })
}

function characterEdit<T>(uuid: string, params: Partial<Chat.Character>) {
  return post<T>({
    url: `/character/edit/${uuid}`,
    data: {
      ...params,
    },
  })
}

function characterToggleUsingContext<T>(uuid: string, usingContext: boolean) {
  return post<T>({
    url: `/character/edit/${uuid}`,
    data: {
      understandContextEnable: usingContext,
    },
  })
}

function characterToggleThinking<T>(uuid: string, isEnableThinking: boolean) {
  return post<T>({
    url: `/character/edit/${uuid}`,
    data: {
      isEnableThinking,
    },
  })
}

function characterDel<T>(uuid: string) {
  return post<T>({
    url: `/character/del/${uuid}`,
  })
}

function searchPresetCharacters<T>(keyword = '') {
  return post<T>({
    url: '/character-preset/search?currentPage=1&pageSize=100',
    data: {
      keyword,
    },
  })
}

// 搜索会话与预设会话关联关系
function listCharacterPresetRels<T>() {
  return get<T>({
    url: '/character-preset-rel/mine?limit=100',
  })
}

function characterAddByPreset<T>(params: { presetCharacterUuid: string }) {
  return post<T>({
    url: `/character/addByPreset?presetUuid=${params.presetCharacterUuid}`,
  })
}

function fetchMessages<T>(characterUuid: string, maxMsgUuid: string, pageSize: number) {
  return get<T>({
    url: `/character/${characterUuid}?maxMsgUuid=${maxMsgUuid}&pageSize=${pageSize}`,
  })
}

// 场景：用户端对话的唯一入口，只读。
// 场景与智能体由管理员在后台配置并对所有用户共享；用户端不提供任何写接口。
// 取绑定顺序最靠前的 Agent 作为该场景的默认智能体，由它决定用哪个模型对话。
function fetchScenes<T>() {
  return get<T>({
    url: '/scene/list',
  })
}

function fetchSceneAgents<T>(sceneUuid: string) {
  return get<T>({
    url: `/scene/agents?sceneUuid=${sceneUuid}`,
  })
}

function fetchAgents<T>() {
  return get<T>({
    url: '/agent/list',
  })
}

/**
 * 取「当前用户 × 智能体」自己的会话 uuid（没有则后端即时创建）。
 *
 * 必须走这个接口而不是直接用 `agent.characterUuid`：智能体上的会话是全局模板，
 * 所有用户共用会同时踩两个坑——多处代码按 uuid 单条查询命中多行抛异常，
 * 以及短期记忆按 characterUuid 存储导致跨用户串对话内容。
 */
function resolveAgentConv<T>(agentUuid: string) {
  return get<T>({
    url: `/agent/resolveConv?agentUuid=${agentUuid}`,
  })
}

function sseProcess(params: {
  options: { prompt?: string; characterUuid?: string; parentMessageId?: string; regenerateQuestionUuid?: string; modelName?: String; modelPlatform?: string; imageUrls?: string[]; audioUuid?: string; audioDuration?: number }
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  messageReceived: (chunk: string, eventName?: string) => void
  thinkingDataReceived: (chunk: string) => void
  audioDataReceived?: (pcmPart: string) => void
  stateChanged?: (state: string) => void
  toolCallReceived?: (data: { toolName: string; durationMs: number; success: boolean }) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}) {
  commonSseProcess('/api/chat/process', params)
}

function messageDel<T>(uuid: string) {
  return post<T>({
    url: `/character/message/del/${uuid}`,
  })
}

/** 收集这条消息所属的一问一答为微调训练样本（进候选池待审核） */
function messageCollectSample<T>(uuid: string) {
  return post<T>({
    url: `/character/message/collect/${uuid}`,
  })
}

function messageTextByAudio<T>(audioUuid: string) {
  return get<T>({
    url: `/character/message/text/${audioUuid}`,
  })
}

function memoryEmbeddingRef<T>(msgUuid: string) {
  return get<T>({
    url: `/character/message/memory-embedding-ref/${msgUuid}`,
  })
}

function knowledgeEmbeddingRef<T>(msgUuid: string) {
  return get<T>({
    url: `/character/message/knowledge-embedding-ref/${msgUuid}`,
  })
}

function messageGraphRef<T>(msgUuid: string) {
  return get<T>({
    url: `/character/message/graph-ref/${msgUuid}`,
  })
}

/**
 * T7-01：提交知识库回答的 👍/👎 反馈。
 * @param queryId 本次检索的 query_id（必填，反馈定位到那次检索）
 * @param messageUuid 回答消息 uuid（可选）
 * @param rating PASS（👍）/ FAIL（👎），可改票
 */
function ragFeedback<T>(params: { queryId: string; messageUuid?: string; rating: 'PASS' | 'FAIL'; comment?: string }) {
  return post<T>({
    url: '/chat/rag-feedback',
    data: params,
  })
}

export default {
  fetchCharacters,
  characterAdd,
  characterAddByPreset,
  characterEdit,
  characterToggleUsingContext,
  characterToggleThinking,
  characterDel,
  searchPresetCharacters,
  listCharacterPresetRels,
  fetchMessages,
  fetchScenes,
  fetchSceneAgents,
  fetchAgents,
  resolveAgentConv,
  sseProcess,
  messageDel,
  messageCollectSample,
  messageTextByAudio,
  memoryEmbeddingRef,
  knowledgeEmbeddingRef,
  messageGraphRef,
  ragFeedback,
}
