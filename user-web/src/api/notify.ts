import { fetchEventSource, EventStreamContentType } from '@microsoft/fetch-event-source'
import { useAuthStore } from '@/store'

/**
 * S2-06：用户级 SSE 通知通道（/api/notify/sse 长连）。
 * 接收跨实例推送的任务终态事件（worker 实例完成任务后经 Redis pub/sub 广播到
 * 持有本连接的 api 实例）。断线自动重连（指数退避封顶 30s），401 视为致命错误停止。
 */

export interface TaskStateChangedPayload {
  taskType: 'draw' | 'kb_index'
  /** draw 事件：绘图 uuid；kb_index 事件：文档 uuid（字段名 docUuid） */
  uuid?: string
  docUuid?: string
  status: string
}

export interface NotifySseHandlers {
  onTaskStateChanged?: (payload: TaskStateChangedPayload) => void
}

class NotifyFatalError extends Error { }

// 终态状态集合：只有这些才触发前端动作（避免 doing 等中间态打扰）
const TERMINAL_DRAW_STATUS = new Set(['success', 'fail'])
const TERMINAL_KB_STATUS = new Set(['done', 'failed'])

let retryCount = 0
let activeController: AbortController | null = null

function handleEvent(eventName: string, data: string, handlers: NotifySseHandlers) {
  if (eventName !== '[TASK_STATE_CHANGED]' || !data)
    return
  try {
    const payload = JSON.parse(data) as TaskStateChangedPayload
    if (payload.taskType === 'draw' && TERMINAL_DRAW_STATUS.has(payload.status)) {
      handlers.onTaskStateChanged?.(payload)
    } else if (payload.taskType === 'kb_index' && TERMINAL_KB_STATUS.has(payload.status)) {
      handlers.onTaskStateChanged?.(payload)
    }
  } catch (e) {
    console.warn('[notify-sse] payload parse error', e)
  }
}

/**
 * 打开通知 SSE 长连（每个页面生命周期调一次；重复调用会先关闭旧连接）。
 * 返回 close 函数；组件卸载时调用以释放服务端连接。
 */
export function openNotifySse(handlers: NotifySseHandlers): () => void {
  activeController?.abort()
  const controller = new AbortController()
  activeController = controller

  const connect = () => {
    if (controller.signal.aborted)
      return
    fetchEventSource('/api/notify/sse', {
      method: 'GET',
      headers: {
        'Authorization': useAuthStore().token,
      },
      signal: controller.signal,
      openWhenHidden: true,
      async onopen(response) {
        if (response.ok && response.headers.get('content-type') === EventStreamContentType) {
          retryCount = 0
          return
        }
        if (response.status === 401) {
          throw new NotifyFatalError('unauthorized')
        }
        throw new NotifyFatalError(`bad response: ${response.status}`)
      },
      onmessage(eventMessage) {
        handleEvent(eventMessage.event, eventMessage.data, handlers)
      },
      onerror(error) {
        if (error instanceof NotifyFatalError)
          throw error
        // 断线重连：指数退避 1s/2s/4s/... 封顶 30s（服务端 30min 超时也会走到这里）
        const delay = Math.min(1000 * 2 ** retryCount, 30_000)
        retryCount++
        console.warn(`[notify-sse] disconnected, retry in ${delay}ms`, error)
        return delay
      },
    }).catch((e) => {
      if (controller.signal.aborted || e instanceof NotifyFatalError)
        return
      console.warn('[notify-sse] stream ended unexpectedly', e)
    })
  }
  connect()

  return () => {
    controller.abort()
    if (activeController === controller)
      activeController = null
  }
}
