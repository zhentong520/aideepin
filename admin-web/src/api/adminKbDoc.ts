import { http } from '@/utils/http/axios'

// 管理端知识库文档管理：复用用户端已有的后端接口（/knowledge-base、/document）。
// 后端权限层对管理员有旁路（isAdmin 直接放行），所以管理员可以操作任意知识库的文档，
// 不需要新增后端接口。http 客户端会自动拼接 /api 前缀并携带鉴权头。

function kbInfo(uuid: string) {
  return http.request({
    url: `/knowledge-base/info/${uuid}`,
    method: 'get',
  })
}

function docSearch(params: { kbUuid: string; keyword?: string; currentPage: number; pageSize: number }) {
  const { kbUuid, keyword, currentPage, pageSize } = params
  const search = keyword ? `keyword=${encodeURIComponent(keyword)}&` : ''
  return http.request({
    url: `/document/search?${search}kbUuid=${kbUuid}&currentPage=${currentPage}&pageSize=${pageSize}`,
    method: 'get',
  })
}

function deleteDoc(uuid: string) {
  return http.request({
    url: `/document/del/${uuid}`,
    method: 'post',
  })
}

function retryIndex(uuid: string) {
  return http.request({
    url: `/document/retryIndex/${uuid}`,
    method: 'post',
  })
}

function retryOcr(uuid: string) {
  return http.request({
    url: `/document/retryOcr/${uuid}`,
    method: 'post',
  })
}

function refetch(uuid: string) {
  return http.request({
    url: `/document/refetch/${uuid}`,
    method: 'post',
  })
}

function toggleStatus(uuid: string, isEnabled: boolean) {
  return http.request({
    url: '/document/toggle-status',
    method: 'post',
    data: { uuid, isEnabled },
  })
}

function fromUrl(req: {
  kbUuid: string
  url: string
  title?: string
  fetchIntervalMinutes?: number | null
}) {
  return http.request({
    url: '/document/fromUrl',
    method: 'post',
    data: req,
  })
}

function fromApi(req: Record<string, unknown>) {
  return http.request({
    url: '/document/fromApi',
    method: 'post',
    data: req,
  })
}

// 文档最终分块列表（模式感知：text→content；qa→questions；parent_child→children）。
// 后端 checkReadPrivilege 对管理员有旁路，管理员可查看任意知识库文档的分块。
function segmentList(docUuid: string, currentPage: number, pageSize: number) {
  return http.request({
    url: `/document-segment/list/${docUuid}?currentPage=${currentPage}&pageSize=${pageSize}`,
    method: 'get',
  })
}

// 文档详情。含解析原文 rawText（迁移 032 之前入库的老文档没有该列，页面回退 remark）。
function docInfo(uuid: string) {
  return http.request({
    url: `/document/info/${uuid}`,
    method: 'get',
  })
}

// 分块预览（干跑）：把「解析 → 清洗 → 切分」三步的中间产物一次摊开。
// 只读——不写分段行、不写向量、不触发索引任务，参数可随意改并立刻重跑。
// 未传的切分参数由后端回退到知识库当前配置；非法值会被就地修正并回传生效值。
function segmentPreview(docUuid: string, params: Record<string, unknown> = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, String(value))
    }
  })
  const suffix = query.toString() ? `?${query.toString()}` : ''
  return http.request({
    url: `/document-segment/preview/${docUuid}${suffix}`,
    method: 'get',
  })
}

export default {
  kbInfo,
  docSearch,
  deleteDoc,
  retryIndex,
  retryOcr,
  refetch,
  toggleStatus,
  fromUrl,
  fromApi,
  segmentList,
  docInfo,
  segmentPreview,
}
