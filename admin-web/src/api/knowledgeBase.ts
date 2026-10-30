import { http } from '@/utils/http/axios'

function search(data: any, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/kb/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

function deleteOne(uuid: string) {
  return http.request({
    url: `/admin/kb/del/${uuid}`,
    method: 'POST',
  })
}

function edit(data: any) {
  return http.request({
    url: '/admin/kb/edit',
    method: 'post',
    data,
  })
}

/**
 * @description: 获取用户信息
 */
function getInfo(uuid: string) {
  return http.request({
    url: `/admin/kb/info/${uuid}`,
    method: 'get',
  })
}

/**
 * @description: 重建关键词索引（混合检索用）
 */
function rebuildKeywordIndex(uuid: string) {
  return http.request({
    url: `/admin/kb/rebuildKeywordIndex/${uuid}`,
    method: 'post',
  })
}

/**
 * @description: 重建向量索引（ embedding ）
 */
function rebuildEmbedding(uuid: string) {
  return http.request({
    url: `/admin/kb/rebuildEmbedding/${uuid}`,
    method: 'post',
  })
}

/**
 * @description: 召回测试。只传本次要临时覆盖的参数，null/缺省表示沿用知识库配置，不落库。
 */
function recallTest(data: any) {
  return http.request({
    url: '/admin/kb/recallTest',
    method: 'post',
    data,
    // 召回 + 重排是重活，给足超时，避免前端先超时而后端仍在跑
    timeout: 60000,
  })
}

/**
 * @description: 存量 chunk ACL 一键回填（幂等，返回影响行数）
 */
function aclBackfill() {
  return http.request({
    url: '/admin/kb/acl/backfill',
    method: 'post',
  })
}

// ===== 二开新增（T7-06）：知识库技能配置包导出/导入 =====

/**
 * 导出配置包 zip。
 * 走原生响应（isReturnNativeResponse）以拿到 Blob；默认的 transformRequestData
 * 会按 {code,success,data} 解析，对二进制响应不适用。
 */
function exportPack(uuid: string) {
  return http.request(
    {
      url: `/admin/kb/pack/export/${uuid}`,
      method: 'get',
      responseType: 'blob',
    },
    { isReturnNativeResponse: true, isShowMessage: false }
  )
}

/** 导入配置包：解析 zip 后以当前用户为 owner 新建知识库 */
function importPack(formData: FormData) {
  return http.request({
    url: '/admin/kb/pack/import',
    method: 'post',
    data: formData,
  })
}

// ===== 二开新增（2026-10-09）：知识库分类字典 =====

function categoryList() {
  return http.request({
    url: '/admin/kb/category/list',
    method: 'get',
  })
}

function categorySave(data: { id?: number; name: string; sort?: number; remark?: string }) {
  return http.request({
    url: '/admin/kb/category/save',
    method: 'post',
    data,
  })
}

function categoryDelete(id: number) {
  return http.request({
    url: `/admin/kb/category/del/${id}`,
    method: 'POST',
  })
}

export default {
  search,
  getInfo,
  edit,
  deleteOne,
  rebuildKeywordIndex,
  rebuildEmbedding,
  recallTest,
  aclBackfill,
  exportPack,
  importPack,
  categoryList,
  categorySave,
  categoryDelete,
}
