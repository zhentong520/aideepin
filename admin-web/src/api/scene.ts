import { http } from '@/utils/http/axios'

/**
 * 场景管理 API（管理端）。
 *
 * 场景是用户端聊天页面唯一可见的东西：用户在侧边栏选一个场景，
 * 该场景绑定顺序最靠前的智能体负责对话，模型由那个智能体在后台决定。
 */
function fetchScenes() {
  return http.request({ url: '/admin/scene/list', method: 'get' })
}

function saveScene(data: any) {
  return http.request({ url: '/admin/scene/saveOrUpdate', method: 'post', data })
}

function toggleScene(uuid: string, isEnable: boolean) {
  return http.request({ url: `/admin/scene/toggle/${uuid}?isEnable=${isEnable}`, method: 'post' })
}

function deleteScene(uuid: string) {
  return http.request({ url: `/admin/scene/delete?uuid=${uuid}`, method: 'post' })
}

function fetchSceneAgents(sceneUuid: string) {
  return http.request({ url: `/admin/scene/agents?sceneUuid=${sceneUuid}`, method: 'get' })
}

/** 全量覆盖绑定。数组顺序即优先级：第一个是默认智能体，用户端实际用它对话。 */
function bindSceneAgents(sceneUuid: string, agentUuids: string[]) {
  return http.request({ url: `/admin/scene/bindAgents?sceneUuid=${sceneUuid}`, method: 'post', data: agentUuids })
}

/** 可用模型列表（给智能体页面选模型用）。 */
function fetchLlmList() {
  return http.request({ url: '/model/llms', method: 'get' })
}

export default {
  fetchScenes,
  saveScene,
  toggleScene,
  deleteScene,
  fetchSceneAgents,
  bindSceneAgents,
  fetchLlmList,
}
