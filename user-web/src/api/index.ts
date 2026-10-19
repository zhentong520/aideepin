import { EventStreamContentType, fetchEventSource } from '@microsoft/fetch-event-source'
import { get, getRawAxios, post } from '@/utils/request'
import { useAuthStore, useUserStore } from '@/store'
import { t } from '@/locales'

class FatalError extends Error { }

function getSysConfig<T = any>() {
  return get<T>({
    url: '/sys/config/public/info',
  })
}

function fetchUserConfig<T = any>() {
  return get<T>({
    url: '/user/config',
  })
}

function fetchCharacters<T = any>() {
  return get<T>({
    url: '/character/list',
  })
}

function characterAdd<T = any>(params: { title: string; remark: string; aiSystemMessage: string }) {
  return post<T>({
    url: '/character/add',
    data: {
      ...params,
    },
  })
}

function characterEdit<T = any>(uuid: string, params: Partial<Chat.Character>) {
  return post<T>({
    url: `/character/edit/${uuid}`,
    data: {
      ...params,
    },
  })
}

function characterToggleUsingContext<T = any>(uuid: string, usingContext: boolean) {
  return post<T>({
    url: `/character/edit/${uuid}`,
    data: {
      understandContextEnable: usingContext,
    },
  })
}

function characterToggleThinking<T = any>(uuid: string, isEnableThinking: boolean) {
  return post<T>({
    url: `/character/edit/${uuid}`,
    data: {
      isEnableThinking,
    },
  })
}

function characterDel<T = any>(uuid: string) {
  return post<T>({
    url: `/character/del/${uuid}`,
  })
}

function searchPresetCharacters<T = any>(keyword = '') {
  return post<T>({
    url: '/character-preset/search?currentPage=1&pageSize=100',
    data: {
      keyword,
    },
  })
}

// 搜索会话与预设会话关联关系
function listCharacterPresetRels<T = any>() {
  return get<T>({
    url: '/character-preset-rel/mine?limit=100',
  })
}

function characterAddByPreset<T = any>(params: { presetCharacterUuid: string }) {
  return post<T>({
    url: `/character/addByPreset?presetUuid=${params.presetCharacterUuid}`,
  })
}

function fetchMessages<T = any>(characterUuid: string, maxMsgUuid: string, pageSize: number) {
  return get<T>({
    url: `/character/${characterUuid}?maxMsgUuid=${maxMsgUuid}&pageSize=${pageSize}`,
  })
}

// 场景：用户端对话的唯一入口，只读。
// 场景与智能体由管理员在后台配置并对所有用户共享；用户端不提供任何写接口。
// 取绑定顺序最靠前的 Agent 作为该场景的默认智能体，由它决定用哪个模型对话。
function fetchScenes<T = any>() {
  return get<T>({
    url: '/scene/list',
  })
}

function fetchSceneAgents<T = any>(sceneUuid: string) {
  return get<T>({
    url: `/scene/agents?sceneUuid=${sceneUuid}`,
  })
}

function fetchAgents<T = any>() {
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
function resolveAgentConv<T = any>(agentUuid: string) {
  return get<T>({
    url: `/agent/resolveConv?agentUuid=${agentUuid}`,
  })
}

function commonSseProcess(
  url: string,
  params: {
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
  },
) {
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
        console.log(t('api.noPermission'))
        const authStore = useAuthStore()
        authStore.removeToken()
        const userStore = useUserStore()
        userStore.resetUserInfo()
        throw new FatalError(t('api.noPermission'))
      } else {
        console.log('response', response)
        throw new FatalError()
      }
    },
    onmessage(eventMessage) {
      if (eventMessage.event === '[START]') {
        params.startCallback(eventMessage.data)
        return
      } else if (eventMessage.event === '[ERROR]') {
        console.log(`error:${eventMessage}`)
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
      console.log(`sse error:${error}`)
      params.errorCallback(error)
      throw error
    },
    openWhenHidden: true,
  })
}

function sseProcess(params: {
  options: { prompt?: string; characterUuid?: string; parentMessageId?: string; regenerateQuestionUuid?: string; modelName?: String; modelPlatform?: string; imageUrls?: string[]; audioUuid?: string; audioDuration?: number }
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  messageReceived: (chunk: string, eventName?: string) => void
  thinkingDataReceived: (chunk: string) => void
  audioDataReceived?: (pcmPart: any) => void
  stateChanged?: (state: string) => void
  toolCallReceived?: (data: { toolName: string; durationMs: number; success: boolean }) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}) {
  commonSseProcess('/api/chat/process', params)
}

function login<T>(email: string, password: string, captchaId: string, captchaCode: string) {
  return post<T>({
    url: '/auth/login',
    data: { email, password, captchaId, captchaCode },
  })
}

function register<T>(email: string, password: string, captchaId: string, captchaCode: string) {
  return post<T>({
    url: '/auth/register',
    data: { email, password, captchaId, captchaCode },
  })
}

// 注册邮箱验证码开关打开后的两个接口 | Used only when VITE_REGISTER_EMAIL_CODE=true
function sendRegisterEmailCode<T>(email: string) {
  return post<T>({
    url: '/auth/register/email-code',
    data: { email },
  })
}

function registerByEmailCode<T>(email: string, password: string, emailCode: string) {
  return post<T>({
    url: '/auth/register/by-email-code',
    data: { email, password, emailCode },
  })
}

function logout<T>() {
  return post<T>({
    url: '/user/logout',
  })
}

function userEdit<T>(config: User.Config) {
  return post<T>({
    url: '/user/edit',
    data: config,
  })
}

/**
 * S-07：通过找回密码 code 设置新密码（原实现为后端把口令重置为固定值，前端无此调用）
 */
function passwordReset<T>(code: string, newPassword: string) {
  return post<T>({
    url: '/auth/password/reset',
    data: { code, newPassword },
  })
}

function passwordFind<T>(email: string) {
  return post<T>({
    url: `/auth/password/forgot?email=${email}`,
  })
}

function loadSearchEngines<T>() {
  return get<T>({
    url: '/auth/search-engine/list',
  })
}

function modifyPassword<T>(oldPassword: string, newPassword: string) {
  return post<T>({
    url: '/user/password/modify',
    data: { oldPassword, newPassword },
  })
}

function searchPrompts<T>(currentPage: number, pageSize: number, keyword?: string) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({
    url: `/prompt/my/search?${search}currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function promptAutocomplete<T>(keyword: string) {
  const search = keyword === undefined ? '' : `keyword=${keyword}`
  return get<T>({
    url: `/prompt/my/autocomplete?${search}`,
  })
}

function promptsSave<T>(prompts: { act: string; prompt: string }[]) {
  return post<T>({
    url: '/prompt/save',
    data: { prompts },
  })
}

function promptDel<T>(id: number) {
  return post<T>({
    url: `/prompt/del/${id}`,
  })
}

function promptEdit<T>(id: number, title: string, remark: string) {
  return post<T>({
    url: `/prompt/edit/${id}`,
    data: { title, remark },
  })
}

function fetchDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/${uuid}`,
  })
}

function fetchNewerPublicDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/newer-public/${uuid}`,
  })
}

function fetchOlderPublicDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/older-public/${uuid}`,
  })
}

function fetchNewerStarredDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/newer-starred/${uuid}`,
  })
}

function fetchOlderStarredDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/older-starred/${uuid}`,
  })
}

function fetchNewerMineDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/newer-mine/${uuid}`,
  })
}

function fetchOlderMineDraw<T = any>(uuid: string) {
  return get<T>({
    url: `/draw/detail/older-mine/${uuid}`,
  })
}

function fetchDraws<T = any>(maxId: number, pageSize: number) {
  return get<T>({
    url: `/draw/list?maxId=${maxId}&pageSize=${pageSize}`,
  })
}

function fetchStarDraws<T = any>(maxId: number, pageSize: number) {
  return get<T>({
    url: `/draw/star/mine?maxId=${maxId}&pageSize=${pageSize}`,
  })
}

function fetchPublicDraws<T = any>(maxId: number, pageSize: number) {
  return get<T>({
    url: `/draw/public/list?maxId=${maxId}&pageSize=${pageSize}`,
  })
}

function fetchDrawComments<T = any>(uuid: string, curentPage: number, pageSize: number) {
  return get<T>({
    url: `/draw/comment/list/${uuid}?curentPage=${curentPage}&pageSize=${pageSize}`,
  })
}

function drawStarOrUnStar<T = any>(uuid: string) {
  return post<T>({
    url: `/draw/star/toggle/${uuid}`,
  })
}

function drawCommentAdd<T = any>(uuid: string, comment: string) {
  return post<T>({
    url: '/draw/comment/add',
    data: { drawUuid: uuid, comment },
  })
}

function fileUpload<T = any>(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return post<T>({
    url: '/file/upload',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

function fileDel<T = any>(uuid: string) {
  return post<T>({
    url: `/file/del/${uuid}`,
  })
}

function imageGenerate<T = any>(params: ImageGenerationParams) {
  return post<T>({
    url: '/draw/generation',
    data: params,
  })
}

/**
 * 删除作图任务
 * @param uuid 作图任务uuid
 * @returns
 */
function drawDel<T = any>(uuid: string) {
  return post<T>({
    url: `/draw/del/${uuid}`,
  })
}

function drawSetPublic<T = any>(uuid: string, isPublic: boolean) {
  return post<T>({
    url: `/draw/set-public/${uuid}?isPublic=${isPublic}`,
  })
}

/**
 * 删除作图任务中的一张图片
 * @param uuid 作图任务uuid
 * @param fileUuid 图片文件uuid
 * @returns
 */
function drawFileDel<T = any>(uuid: string, fileUuid: string) {
  return post<T>({
    url: `/draw/file/del/${fileUuid}?uuid=${uuid}`,
  })
}

function drawPublicImage<T = any>(uuid: string, imageUuid: string) {
  return post<T>({
    url: `/draw/public/image/${uuid}/${imageUuid}`,
  })
}

function drawThumbnailImage<T = any>(uuid: string, imageUuid: string) {
  return post<T>({
    url: `/draw/public/thumnbnail/${uuid}/${imageUuid}`,
  })
}

function messageDel<T = any>(uuid: string) {
  return post<T>({
    url: `/character/message/del/${uuid}`,
  })
}

/** 收集这条消息所属的一问一答为微调训练样本（进候选池待审核） */
function messageCollectSample<T = any>(uuid: string) {
  return post<T>({
    url: `/character/message/collect/${uuid}`,
  })
}

function messageTextByAudio<T = any>(audioUuid: string) {
  return get<T>({
    url: `/character/message/text/${audioUuid}`,
  })
}

function memoryEmbeddingRef<T = any>(msgUuid: string) {
  return get<T>({
    url: `/character/message/memory-embedding-ref/${msgUuid}`,
  })
}

function knowledgeEmbeddingRef<T = any>(msgUuid: string) {
  return get<T>({
    url: `/character/message/knowledge-embedding-ref/${msgUuid}`,
  })
}

function messageGraphRef<T = any>(msgUuid: string) {
  return get<T>({
    url: `/character/message/graph-ref/${msgUuid}`,
  })
}

function knowledgeBaseSearchMine<T>(keyword: string, currentPage: number, pageSize: number, includeOthersPublic?: boolean, categoryId?: number) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  const includePublic = includeOthersPublic ? '&includeOthersPublic=true' : ''
  const cat = categoryId === undefined ? '' : `&categoryId=${categoryId}`
  return get<T>({
    url: `/knowledge-base/mine/search?${search}currentPage=${currentPage}&pageSize=${pageSize}${includePublic}${cat}`,
  })
}

function knowledgeBaseSearchPublic<T>(keyword: string, currentPage: number, pageSize: number, categoryId?: number) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  const cat = categoryId === undefined ? '' : `&categoryId=${categoryId}`
  return get<T>({
    url: `/knowledge-base/public/search?${search}currentPage=${currentPage}&pageSize=${pageSize}${cat}`,
  })
}

function knowledgeBaseCategoryList<T = any>() {
  return get<T>({
    url: '/knowledge-base/category/list',
  })
}

function knowledgeBaseSaveOrUpdate<T = any>(obj: KnowledgeBase.Info) {
  return post<T>({
    url: '/knowledge-base/saveOrUpdate',
    data: obj,
  })
}

function knowledgeBaseInfo<T = any>(uuid: string) {
  return get<T>({
    url: `/knowledge-base/info/${uuid}`,
  })
}

function knowledgeBaseStar<T = any>(uuid: string) {
  return post<T>({
    url: `/knowledge-base/star/toggle?kbUuid=${uuid}`,
  })
}

function knowledgeBaseDelete<T = any>(uuid: string) {
  return post<T>({
    url: `/knowledge-base/del/${uuid}`,
  })
}

function knowledgeBaseItemsIndexing<T = any>(uuids: string[], indexTypes: string[]) {
  return post<T>({
    url: '/knowledge-base/item/indexing-list',
    data: {
      uuids,
      indexTypes,
    },
  })
}

function knowledgeBaseIndexingCheck<T = any>() {
  return get<T>({
    url: '/knowledge-base/indexing/check',
  })
}

function knowledgeBaseItemSearch<T>(currentPage: number, pageSize: number, kbUuid: string, keyword?: string) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({
    url: `/document/search?${search}kbUuid=${kbUuid}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function knowledgeBaseItemSaveOrUpdate<T = any>(obj: KnowledgeBase.Document) {
  return post<T>({
    url: '/document/saveOrUpdate',
    data: obj,
  })
}

/** 从网页 URL 建文档：服务端抓取正文入库并按需索引 */
function knowledgeBaseItemFromUrl<T = any>(req: KnowledgeBase.DocUrlReq) {
  return post<T>({
    url: '/document/fromUrl',
    data: req,
  })
}

/** 从 HTTP API 建文档：服务端按配置调用接口并抽取正文 */
function knowledgeBaseItemFromApi<T = any>(req: KnowledgeBase.DocApiReq) {
  return post<T>({
    url: '/document/fromApi',
    data: req,
  })
}

/** 手动重新抓取一个 url/api 文档（自动同步之外的即时入口） */
function knowledgeBaseItemRefetch<T = any>(uuid: string) {
  return post<T>({
    url: `/document/refetch/${uuid}`,
  })
}

/**
 * Save the form together with a Q&A pair file (multipart): when creating a qa-mode document an
 * xlsx/csv pair file can be attached; the server parses and validates it first, then creates
 * the document, imports the pairs and vectorizes synchronously. Empty fields are omitted
 */
function knowledgeBaseItemSaveOrUpdateWithFile<T = any>(obj: KnowledgeBase.Document, qaFile: File) {
  const formData = new FormData()
  if (obj.id)
    formData.append('id', obj.id)
  if (obj.kbId)
    formData.append('kbId', obj.kbId)
  if (obj.uuid)
    formData.append('uuid', obj.uuid)
  formData.append('kbUuid', obj.kbUuid)
  formData.append('title', obj.title)
  if (obj.brief)
    formData.append('brief', obj.brief)
  if (obj.remark)
    formData.append('remark', obj.remark)
  if (obj.segmentMode)
    formData.append('segmentMode', obj.segmentMode)
  if (obj.childMaxChunkSize)
    formData.append('childMaxChunkSize', `${obj.childMaxChunkSize}`)
  if (obj.autoGenerateQa !== undefined)
    formData.append('autoGenerateQa', `${obj.autoGenerateQa}`)
  formData.append('file', qaFile)
  return post<T>({
    url: '/document/saveOrUpdateWithFile',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

function knowledgeBaseItemDelete<T = any>(uuid: string) {
  return post<T>({
    url: `/document/del/${uuid}`,
  })
}

function knowledgeBaseItemToggleStatus<T = any>(uuid: string, isEnabled: boolean) {
  return post<T>({
    url: '/document/toggle-status',
    data: { uuid, isEnabled },
  })
}

function knowledgeBaseItemRetryIndex<T = any>(uuid: string) {
  return post<T>({
    url: `/document/retryIndex/${uuid}`,
  })
}

function documentIndexFailures<T = any>(uuid: string) {
  return get<T>({
    url: `/document/indexFailures/${uuid}`,
  })
}

// Whether the doc has a queued/running index task; tells queued/executing apart from a final
// failure while the doc status stays FAIL after retry enqueueing
function documentIndexProgress<T = any>(uuid: string) {
  return get<T>({
    url: `/document/indexProgress/${uuid}`,
  })
}

// QA import template download: authenticated blob (the endpoint is no longer in the public
// whitelist), the caller turns the response into an object URL and triggers the save
function downloadQaImportTemplate() {
  return getRawAxios().get<Blob>('/document/qaImportTemplate', { responseType: 'blob' })
}

function documentAttachment<T = any>(uuid: string) {
  return get<T>({
    url: `/document/attachment/${uuid}`,
  })
}

// Edit a QA pair: one element per question; the server normalizes newlines and diffs by content
function documentQaPairSaveOrUpdate<T = any>(obj: { docUuid: string; answerSegmentId?: string; answerContent?: string; questions: string[] }) {
  return post<T>({
    url: '/document-segment/qaPair/saveOrUpdate',
    data: obj,
  })
}

function knowledgeBaseItemAutoGenerateQa<T = any>(uuid: string) {
  return post<T>({
    url: `/document/autoGenerateQa/${uuid}`,
  })
}

function knowledgeBaseItemInfo<T = any>(uuid: string) {
  return get<T>({
    url: `/document/info/${uuid}`,
  })
}

function documentSegmentList<T = any>(docUuid: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/document-segment/list/${docUuid}?currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

/**
 * 分块预览（干跑）：按给定参数重新清洗并切分文档正文，只返回结果、不写库、不向量化。
 * Chunk preview (dry run): clean + split with the given params, read-only.
 */
function documentSegmentPreview<T = any>(docUuid: string, params: KnowledgeBase.ChunkPreviewReq = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '')
      query.append(key, String(value))
  })
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return get<T>({
    url: `/document-segment/preview/${docUuid}${suffix}`,
  })
}

function documentSegmentSaveOrUpdate<T = any>(obj: KnowledgeBase.Segment) {
  return post<T>({
    url: '/document-segment/saveOrUpdate',
    data: obj,
  })
}

function documentSegmentQuestionSaveOrUpdate<T = any>(obj: object) {
  return post<T>({
    url: '/document-segment/question/saveOrUpdate',
    data: obj,
  })
}

function documentSegmentChildSaveOrUpdate<T = any>(obj: object) {
  return post<T>({
    url: '/document-segment/child/saveOrUpdate',
    data: obj,
  })
}

function documentSegmentDel<T = any>(uuid: string) {
  return post<T>({
    url: `/document-segment/del/${uuid}`,
  })
}

function documentSegmentQuestionDel<T = any>(uuid: string) {
  return post<T>({
    url: `/document-segment/question/del/${uuid}`,
  })
}

function documentSegmentChildDel<T = any>(uuid: string) {
  return post<T>({
    url: `/document-segment/child/del/${uuid}`,
  })
}

function documentSegmentChildRegenerate<T = any>(obj: { id: number | string; docUuid: string }) {
  return post<T>({
    url: '/document-segment/child/regenerate',
    data: obj,
  })
}

function documentSegmentToggleStatus<T = any>(data: {
  uuid: string
  isEnabled: boolean
}) {
  return post<T>({
    url: '/document-segment/toggle-status',
    data,
  })
}

// Repair vector drift of one segment: missing embedding ids are cleared and re-embedded
function documentSegmentRepairVector<T = any>(uuid: string) {
  return post<T>({
    url: `/document-segment/repairVector/${uuid}`,
  })
}

// Document graph with name-ordered cursor pagination: the first page carries no cursor;
// follow-ups pass the last element of the previous batch (name / endpoint pair)
function knowledgeBaseGraph<T = any>(kbItemUuid: string, limit: number, afterVertex?: string, afterEdgeSource?: string, afterEdgeTarget?: string) {
  let query = `limit=${limit}`
  if (afterVertex)
    query += `&afterVertex=${encodeURIComponent(afterVertex)}`
  if (afterEdgeSource && afterEdgeTarget)
    query += `&afterEdgeSource=${encodeURIComponent(afterEdgeSource)}&afterEdgeTarget=${encodeURIComponent(afterEdgeTarget)}`
  return get<T>({
    url: `/knowledge-base-graph/list/${kbItemUuid}?${query}`,
  })
}

function knowledgeBaseEmbeddingRef<T = any>(recordUuid: string) {
  return get<T>({
    url: `/knowledge-base/qa/embedding-ref/${recordUuid}`,
  })
}

function knowledgeBaseGraphRef<T = any>(recordUuid: string) {
  return get<T>({
    url: `/knowledge-base/qa/graph-ref/${recordUuid}`,
  })
}

function knowledgeBaseQaSseAsk(params: {
  options: { qaRecordUuid: string }
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  messageReceived: (chunk: string, eventName?: string) => void
  thinkingDataReceived: (chunk: string) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}) {
  commonSseProcess(`/api/knowledge-base/qa/process/${params.options.qaRecordUuid}`, params)
}

function knowledgeBaseQaRecordSearch<T = any>(kbUuid: string, keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/knowledge-base/qa/search?kbUuid=${kbUuid}&keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function knowledgeBaseQaRecordAdd<T = any>(uuid: string, QaContent: { question: string; modelName: string }) {
  return post<T>({
    url: `/knowledge-base/qa/add/${uuid}`,
    data: QaContent,
  })
}

function knowledgeBaseQaRecordDel<T = any>(uuid: string) {
  return post<T>({
    url: `/knowledge-base/qa/del/${uuid}`,
  })
}

function knowledgeBaseQaRecordClear<T = any>() {
  return post<T>({
    url: '/knowledge-base/qa/clear',
  })
}

function knowledgeBaseStarListMine<T = any>(currentPage: number, pageSize: number) {
  return get<T>({
    url: `/knowledge-base/star/mine?currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function loadLLMs<T = any>() {
  return get<T>({
    url: '/model/llms',
  })
}

function loadImageModels<T = any>() {
  return get<T>({
    url: '/model/imageModels',
  })
}

function loadFileContent(fileUrl: string) {
  return getRawAxios().get(fileUrl, {
    responseType: 'text',
  })
}

function workflowAdd<T = any>(data: { title: string; remark: string; isPublic: boolean }) {
  return post<T>({
    url: '/workflow/add',
    data,
  })
}

function workflowCopy<T = any>(wfUuid: string) {
  return post<T>({
    url: `/workflow/copy/${wfUuid}`,
  })
}

function workflowUpdate<T = any>(data: Workflow.WorkflowUpdateReq) {
  return post<T>({
    url: '/workflow/update',
    data,
  })
}

function workflowDel<T = any>(uuid: string) {
  return post<T>({
    url: `/workflow/del/${uuid}`,
  })
}

function workflowSetPublic<T = any>(uuid: string, isPublic?: boolean) {
  return post<T>({
    url: `/workflow/set-public/${uuid}?isPublic=${isPublic}`,
  })
}

function workflowBaseInfoUpdate<T = any>(data: { uuid: string; title: string; remark: string }) {
  return post<T>({
    url: '/workflow/base-info/update',
    data,
  })
}

function workflowRun(params: {
  options: { uuid: string; inputs: Workflow.UserInput[] }
  signal?: AbortSignal
  startCallback: (chunk: string) => void
  messageReceived: (chunk: string, eventName: string) => void
  thinkingDataReceived: (chunk: string) => void
  doneCallback: (chunk: string) => void
  errorCallback: (error: string) => void
}) {
  commonSseProcess(`/api/workflow/run/${params.options.uuid}`, params)
}

function workflowRuntimeResume<T = any>(params: {
  runtimeUuid: string
  feedbackContent: string
}) {
  return post<T>({
    url: `/workflow/runtime/resume/${params.runtimeUuid}`,
    data: {
      ...params,
    },
  })
}

function workflowComponents<T = any>() {
  return get<T>({
    url: '/workflow/public/component/list',
  })
}

function workflowSearchMine<T = any>(keyword: string, currentPage: number, pageSize: number) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({
    url: `/workflow/mine/search?${search}currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function workflowSearchPublic<T = any>(keyword: string, currentPage: number, pageSize: number) {
  const search = keyword === undefined ? '' : `keyword=${keyword}&`
  return get<T>({
    url: `/workflow/public/search?${search}currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function workflowRuntimes<T = any>(wfUuid: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/workflow/runtime/page?wfUuid=${wfUuid}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function workflowRuntimeNodes<T = any>(wfRuntimeUuid: string) {
  return get<T>({
    url: `/workflow/runtime/nodes/${wfRuntimeUuid}`,
  })
}

function workflowRuntimesClear<T = any>() {
  return post<T>({
    url: '/workflow/runtime/clear',
  })
}

function workflowOperators<T = any>() {
  return get<T>({
    url: '/workflow/public/operators',
  })
}

function workflowRuntimeDelete<T = any>(wfRuntimeUuid: string) {
  return get<T>({
    url: `/workflow/runtime/del/${wfRuntimeUuid}`,
  })
}

function mcpSearch<T = any>(keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/mcp/public/search?keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function userMcpList<T = any>(currentPage: number, pageSize: number) {
  return get<T>({
    url: `/user/mcp/list?currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function userMcpSaveOrUpdate<T = any>(data: Mcp.UserMcpUpdateReq) {
  return post<T>({
    url: '/user/mcp/saveOrUpdate',
    data,
  })
}

// 当前用户已启用的 MCP 服务（含服务信息），供工作流 MCP 节点选择器使用
function userMcpEnabled<T = any>() {
  return get<T>({
    url: '/user/mcp/enabled',
  })
}

// 当前用户可用的 MCP 工具清单；mcpIds 为空表示查询全部已启用服务
function mcpTools<T = any>(mcpIds?: Array<string | number>) {
  const query = mcpIds && mcpIds.length > 0 ? `?mcpIds=${mcpIds.join(',')}` : ''
  return get<T>({
    url: `/user/mcp/tools${query}`,
  })
}

// 外部数据库数据源（Database MCP / Database 节点）
function datasourceList<T = any>() {
  return get<T>({
    url: '/db-datasource/list',
  })
}

function datasourceSaveOrUpdate<T = any>(data: any) {
  return post<T>({
    url: '/db-datasource/saveOrUpdate',
    data,
  })
}

function datasourceDelete<T = any>(uuid: string) {
  return post<T>({
    url: `/db-datasource/delete?uuid=${uuid}`,
  })
}

function datasourceTest<T = any>(data: any) {
  return post<T>({
    url: '/db-datasource/test',
    data,
  })
}

// 按已保存的 uuid 测试连接（用库里存的密码，前端拿不到明文密码）
function datasourceTestByUuid<T = any>(uuid: string) {
  return get<T>({
    url: `/db-datasource/test?uuid=${uuid}`,
  })
}

function datasourceTables<T = any>(uuid: string) {
  return get<T>({
    url: `/db-datasource/tables?uuid=${uuid}`,
  })
}

function datasourceColumns<T = any>(uuid: string, table: string) {
  return get<T>({
    url: `/db-datasource/columns?uuid=${uuid}&table=${table}`,
  })
}

// 只读查询：body = { uuid, sql, limit }
function datasourceQuery<T = any>(data: { uuid: string; sql: string; limit?: number }) {
  return post<T>({
    url: '/db-datasource/query',
    data,
  })
}

// Agent Skills
function skillPublicSearch<T = any>(keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/skill/public/search?keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function skillPublicDetail<T = any>(uuid: string) {
  return get<T>({
    url: `/skill/public/${uuid}`,
  })
}

function userSkillList<T = any>() {
  return get<T>({
    url: '/user/skill/list',
  })
}

function userSkillSaveOrUpdate<T = any>(skillId: number, isEnable: boolean) {
  return post<T>({
    url: `/user/skill/saveOrUpdate?skillId=${skillId}&isEnable=${isEnable}`,
  })
}

// 技能目录内的文件清单（供 Skill 节点选择要执行的脚本）
function skillFiles<T = any>(uuid: string) {
  return get<T>({
    url: `/skill/${uuid}/files`,
  })
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
  getSysConfig,
  login,
  register,
  sendRegisterEmailCode,
  registerByEmailCode,
  logout,
  passwordReset,
  passwordFind,
  modifyPassword,
  fetchUserConfig,
  userEdit,
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
  searchPrompts,
  promptsSave,
  promptDel,
  promptEdit,
  promptAutocomplete,
  fileUpload,
  fileDel,
  fetchDraw,
  fetchNewerPublicDraw,
  fetchOlderPublicDraw,
  fetchNewerStarredDraw,
  fetchOlderStarredDraw,
  fetchNewerMineDraw,
  fetchOlderMineDraw,
  fetchDraws,
  fetchStarDraws,
  fetchPublicDraws,
  fetchDrawComments,
  drawFileDel,
  drawStarOrUnStar,
  imageGenerate,
  drawDel,
  drawSetPublic,
  drawPublicImage,
  drawThumbnailImage,
  drawCommentAdd,
  messageDel,
  messageCollectSample,
  messageTextByAudio,
  memoryEmbeddingRef,
  knowledgeEmbeddingRef,
  messageGraphRef,
  knowledgeBaseInfo,
  knowledgeBaseStar,
  knowledgeBaseSearchMine,
  knowledgeBaseSearchPublic,
  knowledgeBaseCategoryList,
  knowledgeBaseSaveOrUpdate,
  knowledgeBaseDelete,
  knowledgeBaseItemSaveOrUpdate,
  knowledgeBaseItemFromUrl,
  knowledgeBaseItemFromApi,
  knowledgeBaseItemRefetch,
  knowledgeBaseItemSaveOrUpdateWithFile,
  knowledgeBaseItemSearch,
  knowledgeBaseItemDelete,
  knowledgeBaseItemToggleStatus,
  knowledgeBaseItemRetryIndex,
  documentIndexFailures,
  documentIndexProgress,
  downloadQaImportTemplate,
  documentAttachment,
  documentQaPairSaveOrUpdate,
  knowledgeBaseItemAutoGenerateQa,
  knowledgeBaseItemInfo,
  knowledgeBaseItemsIndexing,
  knowledgeBaseIndexingCheck,
  documentSegmentList,
  documentSegmentPreview,
  documentSegmentSaveOrUpdate,
  documentSegmentQuestionSaveOrUpdate,
  documentSegmentChildSaveOrUpdate,
  documentSegmentDel,
  documentSegmentQuestionDel,
  documentSegmentChildDel,
  documentSegmentChildRegenerate,
  documentSegmentToggleStatus,
  documentSegmentRepairVector,
  knowledgeBaseGraph,
  knowledgeBaseQaSseAsk,
  knowledgeBaseQaRecordSearch,
  knowledgeBaseQaRecordAdd,
  knowledgeBaseQaRecordDel,
  knowledgeBaseQaRecordClear,
  knowledgeBaseEmbeddingRef,
  knowledgeBaseGraphRef,
  knowledgeBaseStarListMine,
  loadSearchEngines,
  loadLLMs,
  loadImageModels,
  loadFileContent,
  workflowAdd,
  workflowCopy,
  workflowUpdate,
  workflowSetPublic,
  workflowDel,
  workflowBaseInfoUpdate,
  workflowRun,
  workflowComponents,
  workflowSearchPublic,
  workflowSearchMine,
  workflowRuntimes,
  workflowRuntimeNodes,
  workflowRuntimesClear,
  workflowOperators,
  workflowRuntimeDelete,
  workflowRuntimeResume,
  mcpSearch,
  userMcpList,
  userMcpSaveOrUpdate,
  userMcpEnabled,
  mcpTools,
  datasourceList,
  datasourceSaveOrUpdate,
  datasourceDelete,
  datasourceTest,
  datasourceTestByUuid,
  datasourceTables,
  datasourceColumns,
  datasourceQuery,
  skillPublicSearch,
  skillPublicDetail,
  userSkillList,
  userSkillSaveOrUpdate,
  skillFiles,
  extApiKeyGenerate,
  extApiKeyInfo,
  extApiKeyReveal,
  extApiKeyUserGenerate,
  extApiKeyUserInfo,
  extApiKeyUserReveal,
}
