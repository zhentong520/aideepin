import { get } from '@/utils/request'

/**
 * 配置化聊天页面 API（用户端只读，二开 P1）。
 * 页面由 admin 后台配置；用户端按 uuid 拉取 schema 渲染外壳，会话仍走场景链路。
 */
export interface ChatPageBlock {
  type: 'welcome' | 'quickPrompts' | 'richText' | 'sceneInfo'
  title?: string
  subtitle?: string
  items?: string[]
  content?: string
}

export interface ChatPageInfo {
  uuid: string
  name: string
  icon?: string
  pageSchema?: { blocks: ChatPageBlock[] } | null
  sceneUuid?: string
  agentUuid?: string
  isEnable?: boolean
}

function fetchChatPage<T>(uuid: string) {
  return get<T>({
    url: `/chatpage/${uuid}`,
  })
}

function fetchEnabledChatPages<T>() {
  return get<T>({
    url: '/chatpage/list',
  })
}

export default {
  fetchChatPage,
  fetchEnabledChatPages,
}
