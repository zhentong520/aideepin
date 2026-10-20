import { get, getRawAxios, post } from '@/utils/request'
import { commonSseProcess } from './sse'

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

function knowledgeBaseCategoryList<T>() {
  return get<T>({
    url: '/knowledge-base/category/list',
  })
}

function knowledgeBaseSaveOrUpdate<T>(obj: KnowledgeBase.Info) {
  return post<T>({
    url: '/knowledge-base/saveOrUpdate',
    data: obj,
  })
}

function knowledgeBaseInfo<T>(uuid: string) {
  return get<T>({
    url: `/knowledge-base/info/${uuid}`,
  })
}

function knowledgeBaseStar<T>(uuid: string) {
  return post<T>({
    url: `/knowledge-base/star/toggle?kbUuid=${uuid}`,
  })
}

function knowledgeBaseDelete<T>(uuid: string) {
  return post<T>({
    url: `/knowledge-base/del/${uuid}`,
  })
}

function knowledgeBaseItemsIndexing<T>(uuids: string[], indexTypes: string[]) {
  return post<T>({
    url: '/knowledge-base/item/indexing-list',
    data: {
      uuids,
      indexTypes,
    },
  })
}

function knowledgeBaseIndexingCheck<T>() {
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

function knowledgeBaseItemSaveOrUpdate<T>(obj: KnowledgeBase.Document) {
  return post<T>({
    url: '/document/saveOrUpdate',
    data: obj,
  })
}

/** 从网页 URL 建文档：服务端抓取正文入库并按需索引 */
function knowledgeBaseItemFromUrl<T>(req: KnowledgeBase.DocUrlReq) {
  return post<T>({
    url: '/document/fromUrl',
    data: req,
  })
}

/** 从 HTTP API 建文档：服务端按配置调用接口并抽取正文 */
function knowledgeBaseItemFromApi<T>(req: KnowledgeBase.DocApiReq) {
  return post<T>({
    url: '/document/fromApi',
    data: req,
  })
}

/** 手动重新抓取一个 url/api 文档（自动同步之外的即时入口） */
function knowledgeBaseItemRefetch<T>(uuid: string) {
  return post<T>({
    url: `/document/refetch/${uuid}`,
  })
}

/**
 * Save the form together with a Q&A pair file (multipart): when creating a qa-mode document an
 * xlsx/csv pair file can be attached; the server parses and validates it first, then creates
 * the document, imports the pairs and vectorizes synchronously. Empty fields are omitted
 */
function knowledgeBaseItemSaveOrUpdateWithFile<T>(obj: KnowledgeBase.Document, qaFile: File) {
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

function knowledgeBaseItemDelete<T>(uuid: string) {
  return post<T>({
    url: `/document/del/${uuid}`,
  })
}

function knowledgeBaseItemToggleStatus<T>(uuid: string, isEnabled: boolean) {
  return post<T>({
    url: '/document/toggle-status',
    data: { uuid, isEnabled },
  })
}

function knowledgeBaseItemRetryIndex<T>(uuid: string) {
  return post<T>({
    url: `/document/retryIndex/${uuid}`,
  })
}

function documentIndexFailures<T>(uuid: string) {
  return get<T>({
    url: `/document/indexFailures/${uuid}`,
  })
}

// Whether the doc has a queued/running index task; tells queued/executing apart from a final
// failure while the doc status stays FAIL after retry enqueueing
function documentIndexProgress<T>(uuid: string) {
  return get<T>({
    url: `/document/indexProgress/${uuid}`,
  })
}

// QA import template download: authenticated blob (the endpoint is no longer in the public
// whitelist), the caller turns the response into an object URL and triggers the save
function downloadQaImportTemplate() {
  return getRawAxios().get<Blob>('/document/qaImportTemplate', { responseType: 'blob' })
}

function documentAttachment<T>(uuid: string) {
  return get<T>({
    url: `/document/attachment/${uuid}`,
  })
}

// Edit a QA pair: one element per question; the server normalizes newlines and diffs by content
function documentQaPairSaveOrUpdate<T>(obj: { docUuid: string; answerSegmentId?: string; answerContent?: string; questions: string[] }) {
  return post<T>({
    url: '/document-segment/qaPair/saveOrUpdate',
    data: obj,
  })
}

function knowledgeBaseItemAutoGenerateQa<T>(uuid: string) {
  return post<T>({
    url: `/document/autoGenerateQa/${uuid}`,
  })
}

function knowledgeBaseItemInfo<T>(uuid: string) {
  return get<T>({
    url: `/document/info/${uuid}`,
  })
}

function documentSegmentList<T>(docUuid: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/document-segment/list/${docUuid}?currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

/**
 * 分块预览（干跑）：按给定参数重新清洗并切分文档正文，只返回结果、不写库、不向量化。
 * Chunk preview (dry run): clean + split with the given params, read-only.
 */
function documentSegmentPreview<T>(docUuid: string, params: KnowledgeBase.ChunkPreviewReq = {}) {
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

function documentSegmentSaveOrUpdate<T>(obj: KnowledgeBase.Segment) {
  return post<T>({
    url: '/document-segment/saveOrUpdate',
    data: obj,
  })
}

function documentSegmentQuestionSaveOrUpdate<T>(obj: object) {
  return post<T>({
    url: '/document-segment/question/saveOrUpdate',
    data: obj,
  })
}

function documentSegmentChildSaveOrUpdate<T>(obj: object) {
  return post<T>({
    url: '/document-segment/child/saveOrUpdate',
    data: obj,
  })
}

function documentSegmentDel<T>(uuid: string) {
  return post<T>({
    url: `/document-segment/del/${uuid}`,
  })
}

function documentSegmentQuestionDel<T>(uuid: string) {
  return post<T>({
    url: `/document-segment/question/del/${uuid}`,
  })
}

function documentSegmentChildDel<T>(uuid: string) {
  return post<T>({
    url: `/document-segment/child/del/${uuid}`,
  })
}

function documentSegmentChildRegenerate<T>(obj: { id: number | string; docUuid: string }) {
  return post<T>({
    url: '/document-segment/child/regenerate',
    data: obj,
  })
}

function documentSegmentToggleStatus<T>(data: {
  uuid: string
  isEnabled: boolean
}) {
  return post<T>({
    url: '/document-segment/toggle-status',
    data,
  })
}

// Repair vector drift of one segment: missing embedding ids are cleared and re-embedded
function documentSegmentRepairVector<T>(uuid: string) {
  return post<T>({
    url: `/document-segment/repairVector/${uuid}`,
  })
}

// Document graph with name-ordered cursor pagination: the first page carries no cursor;
// follow-ups pass the last element of the previous batch (name / endpoint pair)
function knowledgeBaseGraph<T>(kbItemUuid: string, limit: number, afterVertex?: string, afterEdgeSource?: string, afterEdgeTarget?: string) {
  let query = `limit=${limit}`
  if (afterVertex)
    query += `&afterVertex=${encodeURIComponent(afterVertex)}`
  if (afterEdgeSource && afterEdgeTarget)
    query += `&afterEdgeSource=${encodeURIComponent(afterEdgeSource)}&afterEdgeTarget=${encodeURIComponent(afterEdgeTarget)}`
  return get<T>({
    url: `/knowledge-base-graph/list/${kbItemUuid}?${query}`,
  })
}

function knowledgeBaseEmbeddingRef<T>(recordUuid: string) {
  return get<T>({
    url: `/knowledge-base/qa/embedding-ref/${recordUuid}`,
  })
}

function knowledgeBaseGraphRef<T>(recordUuid: string) {
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

function knowledgeBaseQaRecordSearch<T>(kbUuid: string, keyword: string, currentPage: number, pageSize: number) {
  return get<T>({
    url: `/knowledge-base/qa/search?kbUuid=${kbUuid}&keyword=${keyword}&currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

function knowledgeBaseQaRecordAdd<T>(uuid: string, QaContent: { question: string; modelName: string }) {
  return post<T>({
    url: `/knowledge-base/qa/add/${uuid}`,
    data: QaContent,
  })
}

function knowledgeBaseQaRecordDel<T>(uuid: string) {
  return post<T>({
    url: `/knowledge-base/qa/del/${uuid}`,
  })
}

function knowledgeBaseQaRecordClear<T>() {
  return post<T>({
    url: '/knowledge-base/qa/clear',
  })
}

function knowledgeBaseStarListMine<T>(currentPage: number, pageSize: number) {
  return get<T>({
    url: `/knowledge-base/star/mine?currentPage=${currentPage}&pageSize=${pageSize}`,
  })
}

export default {
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
}
