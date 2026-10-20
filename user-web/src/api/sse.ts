import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source'
import { useAuthStore, useUserStore } from '@/store'
import { t } from '@/locales'

export class FatalError extends Error { }

export interface SseProcessParams {
  options: Record<string, unknown>
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  thinkingDataReceived: (chunk: string) => void
  messageReceived: (chunk: string, eventName: string) => void
  audioDataReceived?: (chunk: string) => void
  stateChanged?: (state: string) => void
  toolCallReceived?: (data: { toolName: string; durationMs: number; success: boolean }) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}

export function commonSseProcess(url: string, params: SseProcessParams) {
  fetchEventSource(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': useAuthStore().token,
    },
    signal: params.signal,
    body: JSON.stringify({
      ...params.options,
    }),
    async onopen(response) {
      if (response.ok && response.headers.get('content-type') === EventStreamContentType) {
        // everything's good
      } else if (response.status === 401) {
        const authStore = useAuthStore()
        authStore.removeToken()
        const userStore = useUserStore()
        userStore.resetUserInfo()
        throw new FatalError(t('api.noPermission'))
      } else {
        throw new FatalError()
      }
    },
    onmessage(eventMessage) {
      if (eventMessage.event === '[START]') {
        params.startCallback(eventMessage.data)
        return
      } else if (eventMessage.event === '[ERROR]') {
        params.errorCallback(eventMessage.data)
        return
      } else if (eventMessage.event === '[DONE]') {
        params.doneCallback(eventMessage.data)
        return
      } else if (eventMessage.event === '[AUDIO]') {
        params.audioDataReceived && params.audioDataReceived(eventMessage.data)
        return
      } else if (eventMessage.event === '[THINKING]') {
        params.thinkingDataReceived && params.thinkingDataReceived(eventMessage.data)
        return
      } else if (eventMessage.event === '[STATE_CHANGED]') {
        params.stateChanged && params.stateChanged(eventMessage.data)
        return
      } else if (eventMessage.event === '[TOOL_CALL]') {
        try {
          params.toolCallReceived && params.toolCallReceived(JSON.parse(eventMessage.data))
        } catch (e) {
          console.warn('[TOOL_CALL] parse error', e)
        }
        return
      }
      // 会自动处理后端返回内容的首个空格，需在后端的返回内容前多加个空格，相关源码：https://github.com/Azure/fetch-event-source/blob/45ac3cfffd30b05b79fbf95c21e67d4ef59aa56a/src/parse.ts#L129-L133
      params.messageReceived(eventMessage.data, eventMessage.event ? eventMessage.event : '')
    },
    onerror(error) {
      params.errorCallback(error)
      throw error
    },
    openWhenHidden: true,
  })
}
