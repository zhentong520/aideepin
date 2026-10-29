import { get } from '@/utils/request'

/**
 * 配置化聊天页面 API（用户端只读，二开 P1）。
 * 页面由 admin 后台配置；用户端按 uuid 拉取 schema 渲染外壳，会话仍走场景链路。
 */
/**
 * 区块画布布局（P2 拖拽设计器产物）。可选：全部区块带 layout 时走自由画布布局
 * （absolute 定位，x/y 画布坐标，w 宽度、高度自适应）；任一缺失则整页回退流式渲染。
 */
export interface ChatPageBlockLayout {
  x: number
  y: number
  w: number
}

export interface ChatPageBlock {
  type: 'welcome' | 'quickPrompts' | 'richText' | 'sceneInfo'
  title?: string
  subtitle?: string
  items?: string[]
  content?: string
  layout?: ChatPageBlockLayout
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
