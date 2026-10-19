import { http } from '@/utils/http/axios'

/**
 * 智能体管理 API（管理端）。
 *
 * 智能体是「后台配置」的核心：场景绑定智能体，智能体绑定模型。
 * 用户端聊天页面只显示场景列表，永远看不到这里的任何内容——
 * 用户选场景，场景的默认智能体决定用哪个模型对话。
 */
function fetchAgents() {
  return http.request({ url: '/admin/agent/list', method: 'get' })
}

function saveAgent(data: any) {
  return http.request({ url: '/admin/agent/saveOrUpdate', method: 'post', data })
}

function toggleAgent(uuid: string, isEnable: boolean) {
  return http.request({ url: `/admin/agent/toggle/${uuid}?isEnable=${isEnable}`, method: 'post' })
}

function deleteAgent(uuid: string) {
  return http.request({ url: `/admin/agent/delete?uuid=${uuid}`, method: 'post' })
}

export default {
  fetchAgents,
  saveAgent,
  toggleAgent,
  deleteAgent,
}
