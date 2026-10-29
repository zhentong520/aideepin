import { http } from '@/utils/http/axios'

function search(data: any, params: { current: number; size: number }) {
  return http.request({
    url: `/admin/token-monitor/search?currentPage=${params.current}&pageSize=${params.size}`,
    method: 'post',
    data,
  })
}

/**
 * T6-13 模型成本看板聚合。
 * 返回 status: OK / NO_DATA / UNAVAILABLE —— 前端据此区分「暂无数据」与「数据不可用」。
 * ⚠️ beginTime/endTime 必须拼进 URL query：axios 封装对无 body 的 POST 会把 params
 * 挪进请求体（config.data = params），后端 @RequestParam 就收不到了（真实页面踩坑）。
 */
function costDashboard(beginTime: string, endTime: string) {
  return http.request({
    url: `/admin/token-monitor/cost-dashboard?beginTime=${encodeURIComponent(
      beginTime
    )}&endTime=${encodeURIComponent(endTime)}`,
    method: 'post',
  })
}

export default {
  search,
  costDashboard,
}
