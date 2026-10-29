import { http } from '@/utils/http/axios'

/**
 * T7-01 RAG 质量看板 + 失败闭环 API。
 * ⚠️ 后端 status 语义（与 token-monitor 成本看板一致）：
 *   OK = 正常（可能个别指标为 null，表示「没分数」而不是 0）
 *   NO_DATA = 查询正常但范围内无检索记录
 *   UNAVAILABLE = 数据源故障——前端必须显示「数据不可用」，绝不显示 0
 */

export interface RagQualityReq {
  beginTime: string
  endTime: string
}

function qualityDashboard(data: RagQualityReq) {
  // ⚠️ beginTime/endTime 必须拼进 URL query：axios 封装对无 body 的 POST 会把 params
  // 挪进请求体，后端 @RequestParam 收不到 → missingParameter（与成本看板同款坑）
  return http.request({
    url: `/admin/rag/quality-dashboard?beginTime=${encodeURIComponent(
      data.beginTime
    )}&endTime=${encodeURIComponent(data.endTime)}`,
    method: 'post',
  })
}

export interface RagQualityDetailReq {
  beginTime?: string
  endTime?: string
  kbUuid?: string
  userUuid?: string
  /** all / missed / low_score / failed */
  scope?: string
}

function qualityDetail(data: RagQualityDetailReq, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/rag/quality-detail?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

/**
 * 明细 CSV 导出：返回 blob（UTF-8 带 BOM），由调用方触发浏览器下载。
 */
function qualityDetailExport(data: RagQualityDetailReq) {
  return http.request({
    url: `/admin/rag/quality-detail/export`,
    method: 'post',
    data,
    responseType: 'blob',
  })
}

/**
 * 失败案例一键转评测题：写入 <dataset-dir>/qa-dataset-derived.json（候选池，金标待人工补）。
 */
function toEval(data: RagQualityDetailReq & { limit?: number }) {
  return http.request({
    url: `/admin/rag/to-eval`,
    method: 'post',
    data,
  })
}

export default {
  qualityDashboard,
  qualityDetail,
  qualityDetailExport,
  toEval,
}
