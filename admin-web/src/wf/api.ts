import { fetchEventSource, EventStreamContentType } from '@microsoft/fetch-event-source'
import { http } from '@/utils/http/axios'
import { useAuthStore } from '@/wf/stores'

/**
 * wf-designer 专用 API 层：
 * 复用用户端同款后端接口（/workflow、/character、/skill、/user/mcp、/knowledge-base、/db-datasource），
 * 后端权限层对管理员全程旁路（PrivilegeUtil isAdmin / mine-search 全量），无需新增后端接口。
 * 响应形状与 admin http 客户端一致：await api.xxx() → { data: payload }。
 */

function get<T = any>(config: { url: string }): Promise<{ data: T }> {
  return http.request<{ data: T }>({ url: config.url, method: 'get' })
}

function post<T = any>(config: { url: string; data?: any }): Promise<{ data: T }> {
  return http.request<{ data: T }>({ url: config.url, method: 'post', data: config.data })
}

function workflowAdd<T = any>(data: { title: string; remark: string; isPublic: boolean }) {
  return post<T>({ url: '/workflow/add', data })
}

function workflowCopy<T = any>(wfUuid: string) {
  return post<T>({ url: `/workflow/copy/${wfUuid}` })
}

function workflowUpdate<T = any>(data: Workflow.WorkflowUpdateReq) {
  return post<T>({ url: '/workflow/update', data })
}

function workflowDel<T = any>(uuid: string) {
  return post<T>({ url: `/workflow/del/${uuid}` })
}

function workflowSetPublic<T = any>(uuid: string, isPublic?: boolean) {
  return post<T>({ url: `/workflow/set-public/${uuid}?isPublic=${isPublic}` })
}

function workflowBaseInfoUpdate<T = any>(data: { uuid: string; title: string; remark: string }) {
  return post<T>({ url: '/workflow/base-info/update', data })
}

function workflowRun(params: {
  options: any
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  thinkingDataReceived: (chunk: string) => void
  messageReceived: (chunk: string, eventName: string) => void
  audioDataReceived?: (chunk: string) => void
  stateChanged?: (state: string) => void
  toolCallReceived?: (data: { toolName: string; durationMs: number; success: boolean }) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}) {
  fetchEventSource(`/api/workflow/run/${params.options.uuid}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': useAuthStore().token,
    },
    signal: params.signal,
    body: JSON.stringify({ ...params.options }),
    async onopen(response) {
      if (!(response.ok && response.headers.get('content-type') === EventStreamContentType)) {
        throw new Error(`HTTP ${response.status}`)
      }
    },
    onmessage(eventMessage) {
      switch (eventMessage.event) {
        case '[START]':
          params.startCallback(eventMessage.data)
          return
        case '[ERROR]':
          params.errorCallback(eventMessage.data)
          return
        case '[DONE]':
          params.doneCallback(eventMessage.data)
          return
        case '[AUDIO]':
          params.audioDataReceived?.(eventMessage.data)
          return
        case '[THINKING]':
          params.thinkingDataReceived(eventMessage.data)
          return
        case '[STATE_CHANGED]':
          params.stateChanged?.(eventMessage.data)
          return
        case '[TOOL_CALL]':
          try {
            params.toolCallReceived?.(JSON.parse(eventMessage.data))
          } catch (e) {
            console.warn('[TOOL_CALL] parse error', e)
          }
          return
        default:
          params.messageReceived(eventMessage.data, eventMessage.event)
      }
    },
    onerror(err) {
      params.errorCallback(String(err))
      throw err
    },
  })
}

function workflowRuntimeResume<T = any>(params: { runtimeUuid: string; feedbackContent: string }) {
  return http.request<T>({
    url: `/workflow/runtime/resume/${params.runtimeUuid}`,
    method: 'post',
    data: { feedbackContent: params.feedbackContent },
  })
}

function workflowComponents<T = any>() {
  return get<T>({ url: '/workflow/public/component/list' })
}

function workflowSearchMine<T = any>(keyword: string, currentPage: number, pageSize: number) {
  const search = keyword ? `keyword=${keyword}&` : ''
  return get<T>({ url: `/workflow/mine/search?${search}currentPage=${currentPage}&pageSize=${pageSize}` })
}

function workflowSearchPublic<T = any>(keyword: string, currentPage: number, pageSize: number) {
  const search = keyword ? `keyword=${keyword}&` : ''
  return get<T>({ url: `/workflow/public/search?${search}currentPage=${currentPage}&pageSize=${pageSize}` })
}

function workflowRuntimes<T = any>(wfUuid: string, currentPage: number, pageSize: number) {
  return get<T>({ url: `/workflow/runtime/page?wfUuid=${wfUuid}&currentPage=${currentPage}&pageSize=${pageSize}` })
}

function workflowRuntimeNodes<T = any>(wfRuntimeUuid: string) {
  return get<T>({ url: `/workflow/runtime/nodes/${wfRuntimeUuid}` })
}

function workflowRuntimesClear<T = any>() {
  return post<T>({ url: '/workflow/runtime/clear' })
}

function workflowRuntimeDelete<T = any>(wfRuntimeUuid: string) {
  return get<T>({ url: `/workflow/runtime/del/${wfRuntimeUuid}` })
}

function workflowOperators<T = any>() {
  return get<T>({ url: '/workflow/public/operators' })
}

function fetchCharacters<T = any>() {
  return get<T>({ url: '/character/list' })
}

function userSkillList<T = any>() {
  return get<T>({ url: '/user/skill/list' })
}

function skillPublicSearch<T = any>(keyword: string, currentPage: number, pageSize: number) {
  return get<T>({ url: `/skill/public/search?keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}` })
}

function skillFiles<T = any>(uuid: string) {
  return get<T>({ url: `/skill/${uuid}/files` })
}

function userMcpEnabled<T = any>() {
  return get<T>({ url: '/user/mcp/enabled' })
}

function mcpTools<T = any>(mcpIds?: Array<string | number>) {
  const query = mcpIds && mcpIds.length > 0 ? `?mcpIds=${mcpIds.join(',')}` : ''
  return get<T>({ url: `/user/mcp/tools${query}` })
}

function knowledgeBaseSearchMine<T = any>(keyword: string, currentPage: number, pageSize: number, includeOthersPublic?: boolean) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  const includePublic = includeOthersPublic ? '&includeOthersPublic=true' : ''
  return get<T>({ url: `/knowledge-base/mine/search?${search}currentPage=${currentPage}&pageSize=${pageSize}${includePublic}` })
}

function knowledgeBaseSearchPublic<T = any>(keyword: string, currentPage: number, pageSize: number) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({ url: `/knowledge-base/public/search?${search}currentPage=${currentPage}&pageSize=${pageSize}` })
}

function knowledgeBaseInfo<T = any>(uuid: string) {
  return get<T>({ url: `/knowledge-base/info/${uuid}` })
}

function knowledgeBaseCategoryList<T = any>() {
  return get<T>({ url: '/knowledge-base/category/list' })
}

function datasourceList<T = any>() {
  return get<T>({ url: '/db-datasource/list' })
}

function datasourceTables<T = any>(uuid: string) {
  return get<T>({ url: `/db-datasource/tables?uuid=${uuid}` })
}

function datasourceColumns<T = any>(uuid: string, table: string) {
  return get<T>({ url: `/db-datasource/columns?uuid=${uuid}&table=${table}` })
}

// External API Key management (resource-level)
function extApiKeyGenerate<T = any>(type: string, uuid: string) {
  return post<T>({ url: `/external-api-key/${type}/${uuid}` })
}
function extApiKeyInfo<T = any>(type: string, uuid: string) {
  return get<T>({ url: `/external-api-key/${type}/${uuid}` })
}
function extApiKeyReveal<T = any>(type: string, uuid: string) {
  return post<T>({ url: `/external-api-key/${type}/${uuid}/reveal` })
}

// External API Key management (user-level: draw, mcp)
function extApiKeyUserGenerate<T = any>(type: string) {
  return post<T>({ url: `/external-api-key/user/${type}` })
}
function extApiKeyUserInfo<T = any>(type: string) {
  return get<T>({ url: `/external-api-key/user/${type}` })
}
function extApiKeyUserReveal<T = any>(type: string) {
  return post<T>({ url: `/external-api-key/user/${type}/reveal` })
}

export default {
  workflowAdd,
  workflowCopy,
  workflowUpdate,
  workflowSetPublic,
  workflowDel,
  workflowBaseInfoUpdate,
  workflowRun,
  workflowRuntimeResume,
  workflowComponents,
  workflowSearchMine,
  workflowSearchPublic,
  workflowRuntimes,
  workflowRuntimeNodes,
  workflowRuntimesClear,
  workflowRuntimeDelete,
  workflowOperators,
  fetchCharacters,
  userSkillList,
  skillPublicSearch,
  skillFiles,
  userMcpEnabled,
  mcpTools,
  knowledgeBaseSearchMine,
  knowledgeBaseSearchPublic,
  knowledgeBaseInfo,
  knowledgeBaseCategoryList,
  datasourceList,
  datasourceTables,
  datasourceColumns,
  extApiKeyGenerate,
  extApiKeyInfo,
  extApiKeyReveal,
  extApiKeyUserGenerate,
  extApiKeyUserInfo,
  extApiKeyUserReveal,
}
