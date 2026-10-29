import { http } from '@/utils/http/axios'

/**
 * 配置化聊天页面 API（管理端，二开 P1）。
 *
 * 页面 = 名称 + page_schema（区块数组：welcome/quickPrompts/richText/sceneInfo，
 * 数组顺序即展示顺序）+ 场景绑定（可选智能体覆盖）。
 * 用户端 /chat/page/:uuid 按 schema 渲染外壳并走现有场景会话链路。
 */
/**
 * 区块画布布局（P2 拖拽设计器）。可选：全部区块带 layout 时用户端走自由画布布局，
 * 任一缺失则整页回退流式渲染（数组顺序）。x/y 为画布坐标，w 为区块宽度（h 自适应）。
 */
export interface BlockLayout {
  x: number
  y: number
  w: number
}

export interface PageBlock {
  type: 'welcome' | 'quickPrompts' | 'richText' | 'sceneInfo'
  title?: string
  subtitle?: string
  items?: string[]
  content?: string
  layout?: BlockLayout
}

export interface ChatPageItem {
  id?: number
  uuid?: string
  name: string
  icon?: string
  pageSchema?: { blocks: PageBlock[] } | null
  sceneUuid?: string
  agentUuid?: string
  sort?: number
  isEnable?: boolean
}

function fetchChatPages() {
  return http.request({ url: '/admin/chatpage/list', method: 'get' })
}

function saveChatPage(data: ChatPageItem) {
  return http.request({ url: '/admin/chatpage/saveOrUpdate', method: 'post', data })
}

function toggleChatPage(uuid: string, isEnable: boolean) {
  return http.request({ url: `/admin/chatpage/toggle/${uuid}?isEnable=${isEnable}`, method: 'post' })
}

function deleteChatPage(uuid: string) {
  return http.request({ url: `/admin/chatpage/delete?uuid=${uuid}`, method: 'post' })
}

export default {
  fetchChatPages,
  saveChatPage,
  toggleChatPage,
  deleteChatPage,
}
